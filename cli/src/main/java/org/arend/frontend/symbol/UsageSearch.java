package org.arend.frontend.symbol;

import org.arend.error.SourcePosition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.BaseConcreteExpressionVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements `-fu` find-usages.
 *
 * Pipeline:
 *   1. Locate the target referable from {@code MODULE_PATH:GROUP_PATH}.
 *   2. Refresh per-library symbol indexes (cheap; reuses the -ss machinery).
 *   3. Collect locally-aliased names: walk every in-scope module's
 *      {@link ConcreteNamespaceCommand}s, match their {@code NameRenaming}s
 *      against the target's standard/alias name, and record the local alias.
 *      Iterate to a fixed point so multi-hop renames are caught.
 *   4. Per-module text scan with a word-boundary regex over the union of
 *      {standardName, aliasName} ∪ that module's local aliases.
 *   5. Resolve all candidate modules through {@link ArendServer} (this also
 *      pulls in their transitive dependencies).
 *   6. Walk the resolved {@link Concrete.ResolvableDefinition}s of every
 *      candidate module with {@link UsageVisitor}, keep
 *      {@code expr.getReferent() == target} hits, drop the target's own
 *      declaration site, dedupe by (file, line, col).
 *   7. Print a header, one line per usage, and a footer.
 *
 * The alias-handling mirrors how {@code ArendCustomSearcher} solves the same
 * problem in the IntelliJ plugin: name match plus identity check after
 * resolution. We pre-compute renames upfront because we have direct access
 * to the parsed {@code ConcreteNamespaceCommand} records, whereas the plugin
 * has to dispatch a recursive PSI search from inside its result processor.
 */
public final class UsageSearch {

  // ---- options ------------------------------------------------------------

  public static final class Options {
    public boolean withTests = false;
    public boolean printLine = true;
    public boolean useAliases = true;
    public int limit = 500;
    public @Nullable Set<String> onlyLibraries = null;
  }

  public record Parsed(String spec, Options options) {}

  /**
   * Parses sub-tokens passed alongside {@code -fu}. The first token that
   * doesn't look like an option is the target spec.
   */
  public static @Nullable Parsed parseArgs(String[] args) {
    Options opts = new Options();
    String spec = null;
    for (String arg : args) {
      switch (arg) {
        case "with-tests" -> opts.withTests = true;
        case "no-line" -> opts.printLine = false;
        case "aliases=false" -> opts.useAliases = false;
        case "aliases=true" -> opts.useAliases = true;
        default -> {
          if (arg.startsWith("limit=")) {
            try { opts.limit = Integer.parseInt(arg.substring("limit=".length())); }
            catch (NumberFormatException e) {
              System.err.println("[ERROR] Bad -fu limit: " + arg);
              return null;
            }
          } else if (arg.startsWith("only=")) {
            if (opts.onlyLibraries == null) opts.onlyLibraries = new HashSet<>();
            for (String s : arg.substring("only=".length()).split(",")) {
              if (!s.isEmpty()) opts.onlyLibraries.add(s.trim());
            }
          } else if (spec == null) {
            spec = arg;
          } else {
            System.err.println("[ERROR] Multiple -fu specs: '" + spec + "' and '" + arg + "'.");
            return null;
          }
        }
      }
    }
    if (spec == null) {
      System.err.println("[ERROR] -fu requires a <MODULE_PATH>:<GROUP_PATH> spec");
      return null;
    }
    return new Parsed(spec, opts);
  }

  // ---- top-level driver --------------------------------------------------

  public static int run(@NotNull String spec,
                        @NotNull Options options,
                        @NotNull List<SourceLibrary> requestedLibraries,
                        @NotNull LibraryManager libraryManager,
                        @NotNull ArendServer server,
                        @NotNull ErrorReporter errorReporter) {
    // 1) Parse spec
    Target target = parseTarget(spec);
    if (target == null) return 0;

    // Choose libraries in scope (default: every loaded library)
    List<SourceLibrary> libsInScope = librariesInScope(requestedLibraries, libraryManager, options);
    if (libsInScope.isEmpty()) {
      System.err.println("[ERROR] No libraries in scope.");
      return 0;
    }

    // 2) Refresh symbol indexes for in-scope libraries. This also gets the
    //    server to register every source module so getRawGroup works below.
    Map<SourceLibrary, SymbolIndex> indexes = new LinkedHashMap<>();
    for (SourceLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      for (ModulePath mp : lib.findModules(false)) {
        if (idx.isStale(lib, mp)) {
          server.findModule(mp, lib.getLibraryName(), false, false);
        }
      }
      if (options.withTests) {
        for (ModulePath mp : lib.findModules(true)) {
          server.findModule(mp, lib.getLibraryName(), true, false);
        }
      }
      idx.refresh(lib, server, false);
      idx.save();
      indexes.put(lib, idx);
    }

    // 2b) If the user passed a bare long-name (no module prefix), resolve it via
    //     the symbol index: match by trailing short name, then keep only candidates
    //     whose full long-name ends with the user's segments.
    if (target.module.getModulePath().size() == 0) {
      target = resolveByShortName(target.longName, indexes, libsInScope, server);
      if (target == null) return 0;
    }

    // 3) Resolve target referable. We need a registered module + its raw group.
    ResolvedTarget resolved = resolveTarget(server, target);
    if (resolved == null) {
      System.err.println("[ERROR] Definition not found: " + spec);
      return 0;
    }
    LocatedReferable targetReferable = resolved.referable;
    target = new Target(resolved.module, target.longName);
    String standardName = targetReferable.textRepresentation();
    String aliasName = targetReferable.getAliasName();

    Set<String> primaryNames = new LinkedHashSet<>();
    primaryNames.add(standardName);
    if (options.useAliases && aliasName != null) primaryNames.add(aliasName);

    // 4) Collect per-module local aliases by inspecting \import / \open renamings.
    Map<ModuleLocation, Set<String>> perModuleNames = collectLocalAliases(server, libsInScope, options.withTests, primaryNames);

    // 5) Text-scan to identify candidate modules.
    Map<ModuleLocation, List<TextHit>> textHits = new LinkedHashMap<>();
    for (SourceLibrary lib : libsInScope) {
      List<ModulePath> modules = new ArrayList<>(lib.findModules(false));
      if (options.withTests) modules.addAll(lib.findModules(true));
      for (ModulePath mp : modules) {
        ModuleLocation source = new ModuleLocation(lib.getLibraryName(),
            ModuleLocation.LocationKind.SOURCE, mp);
        ModuleLocation tests = new ModuleLocation(lib.getLibraryName(),
            ModuleLocation.LocationKind.TEST, mp);
        for (ModuleLocation moduleLoc : new ModuleLocation[] { source, tests }) {
          if (moduleLoc.getLocationKind() == ModuleLocation.LocationKind.TEST && !options.withTests) continue;
          Set<String> names = unionNames(primaryNames, perModuleNames.get(moduleLoc));
          if (names.isEmpty()) continue;
          Path file = sourcePathFor(lib, moduleLoc);
          if (file == null || !Files.isRegularFile(file)) continue;
          List<TextHit> hits = scanFile(file, names);
          if (!hits.isEmpty()) textHits.put(moduleLoc, hits);
        }
      }
    }

    // 6) Resolve candidates (their transitive deps come along).
    if (!textHits.isEmpty()) {
      server.getCheckerFor(new ArrayList<>(textHits.keySet()))
          .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }

    // 7) Walk candidates' resolved definitions, keep refs whose getReferent() == target.
    UsageVisitor visitor = new UsageVisitor(targetReferable);
    Set<UsageHit> hits = new LinkedHashSet<>();
    for (ModuleLocation moduleLoc : textHits.keySet()) {
      Path absFile = sourcePathFor(libraryManager.getLibrary(moduleLoc.getLibraryName()), moduleLoc);
      String absStr = absFile == null ? "" : absFile.toString();
      for (DefinitionData data : server.getResolvedDefinitions(moduleLoc)) {
        Concrete.ResolvableDefinition def = data.definition();
        if (def == null) continue;
        visitor.context = new VisitorContext(moduleLoc, absStr);
        try {
          def.accept(visitor, null);
        } catch (RuntimeException ignored) {
          // Skip malformed definitions; resolution errors are reported elsewhere.
        }
        hits.addAll(visitor.collected);
        visitor.collected.clear();
      }
    }

    // 8) Drop the target's own declaration site (same line+col as targetReferable.getData()).
    int[] declPos = positionOf(targetReferable);
    Path targetPath = sourcePathFor(libraryManager.getLibrary(target.module.getLibraryName()), target.module);
    if (declPos[0] != 0 && targetPath != null) {
      hits.removeIf(h -> h.line == declPos[0] && h.column == declPos[1]
          && pathEquals(h.absoluteFile, targetPath));
    }

    // 9) Print
    String kind = kindLabel(targetReferable);
    System.out.println("Usages of " + target.module.getLibraryName() + "::" + targetReferable.getRefLongName()
        + "  [" + kind + "]");
    if (hits.isEmpty()) {
      System.out.println();
      System.out.println("No usages.");
      return 0;
    }
    int printed = 0;
    boolean truncated = false;
    List<UsageHit> sorted = new ArrayList<>(hits);
    sorted.sort(Comparator.comparing((UsageHit h) -> h.absoluteFile)
        .thenComparingInt(h -> h.line)
        .thenComparingInt(h -> h.column));
    for (UsageHit h : sorted) {
      if (options.limit > 0 && printed >= options.limit) { truncated = true; continue; }
      System.out.println();
      String headLine = (h.absoluteFile == null || h.absoluteFile.isEmpty()
          ? "<" + h.module.getLibraryName() + ":" + h.module.getModulePath() + ">"
          : PathDisplay.shorten(h.absoluteFile, libraryManager)) + ":" + h.line + ":" + h.column;
      if (options.printLine) {
        String content = readLine(h.absoluteFile, h.line);
        System.out.println(headLine + (content == null ? "" : ": " + content.strip()));
      } else {
        System.out.println(headLine);
      }
      printed++;
    }
    System.out.println();
    System.out.println("Found " + hits.size() + " usage" + (hits.size() == 1 ? "" : "s")
        + (truncated ? " (showing " + printed + "; pass `limit=0` for all)" : ""));
    return hits.size();
  }

  // ---- target parsing & resolution ---------------------------------------

  private record Target(ModuleLocation module, LongName longName) {}

  private static @Nullable Target parseTarget(String spec) {
    int idx = spec.indexOf(':');
    if (idx < 0) {
      // Bare-name form. Parse as LongName; later resolved against the symbol index
      // by trailing short name, then suffix-matched against the user's segments.
      LongName ln = LongName.fromString(spec);
      if (!FileUtils.isCorrectDefinitionName(ln)) {
        System.err.println("[ERROR] invalid definition name '" + spec + "'");
        return null;
      }
      return new Target(
          new ModuleLocation("", ModuleLocation.LocationKind.SOURCE, new ModulePath()), ln);
    }
    String modStr = spec.substring(0, idx);
    String defStr = spec.substring(idx + 1);
    if (modStr.isEmpty() || defStr.isEmpty()) {
      System.err.println("[ERROR] empty module or definition path in -fu spec '" + spec + "'");
      return null;
    }
    ModulePath mp = ModulePath.fromString(modStr);
    if (!FileUtils.isCorrectModulePath(mp)) {
      System.err.println("[ERROR] invalid module path '" + modStr + "'");
      return null;
    }
    LongName ln = LongName.fromString(defStr);
    if (!FileUtils.isCorrectDefinitionName(ln)) {
      System.err.println("[ERROR] invalid definition name '" + defStr + "'");
      return null;
    }
    // ModuleLocation populated below in resolveTarget once we know the library.
    return new Target(new ModuleLocation("", ModuleLocation.LocationKind.SOURCE, mp), ln);
  }

  /**
   * Resolve a bare-long-name spec against the symbol index. Looks up entries whose
   * short name matches the spec's last segment, then keeps only those whose full
   * long-name ends with the user's segments (suffix match). Returns the unique
   * candidate as a Target, or null after printing a diagnostic if zero or more
   * than one survive.
   */
  private static @Nullable Target resolveByShortName(@NotNull LongName userLongName,
                                                     @NotNull Map<SourceLibrary, SymbolIndex> indexes,
                                                     @NotNull List<SourceLibrary> libsInScope,
                                                     @NotNull ArendServer server) {
    List<String> userSegs = userLongName.toList();
    String shortName = userSegs.get(userSegs.size() - 1);

    record Candidate(SourceLibrary lib, SymbolIndex.Entry entry) {}
    List<Candidate> matches = new ArrayList<>();
    for (Map.Entry<SourceLibrary, SymbolIndex> e : indexes.entrySet()) {
      for (SymbolIndex.Entry entry : e.getValue().allEntries()) {
        if (!entry.shortName().equals(shortName)) continue;
        List<String> entrySegs = LongName.fromString(entry.longName()).toList();
        if (!endsWith(entrySegs, userSegs)) continue;
        matches.add(new Candidate(e.getKey(), entry));
      }
    }
    if (matches.isEmpty()) {
      System.err.println("[ERROR] No definition with short name '" + shortName + "' in scope"
          + (userSegs.size() > 1 ? " whose long-name ends with '" + userLongName + "'" : "")
          + ".");
      return null;
    }
    if (matches.size() > 1) {
      System.err.println("[ERROR] '" + userLongName + "' is ambiguous. Use one of:");
      List<String> labels = new ArrayList<>();
      for (Candidate c : matches) {
        labels.add("  " + c.lib.getLibraryName() + "::" + c.entry.modulePath() + ":" + c.entry.longName());
      }
      Collections.sort(labels);
      for (String l : labels) System.err.println(l);
      return null;
    }
    Candidate only = matches.get(0);
    ModulePath mp = only.entry.modulePath();
    LongName fullLongName = LongName.fromString(only.entry.longName());
    System.out.println("[INFO] Resolved '" + userLongName + "' -> "
        + only.lib.getLibraryName() + "::" + mp + ":" + fullLongName);
    // Pre-register the module on the server so resolveTarget's getRawGroup works.
    server.findModule(mp, only.lib.getLibraryName(), true, false);
    return new Target(
        new ModuleLocation(only.lib.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp),
        fullLongName);
  }

  /** True iff {@code tail} is a (non-empty) suffix of {@code full} (segment-wise). */
  private static boolean endsWith(List<String> full, List<String> tail) {
    if (tail.size() > full.size()) return false;
    int offset = full.size() - tail.size();
    for (int i = 0; i < tail.size(); i++) {
      if (!full.get(offset + i).equals(tail.get(i))) return false;
    }
    return true;
  }

  private record ResolvedTarget(ModuleLocation module, LocatedReferable referable) {}

  private static @Nullable ResolvedTarget resolveTarget(ArendServer server, Target target) {
    ModuleLocation found = server.findModule(target.module.getModulePath(), null, true, true);
    if (found == null) return null;
    ConcreteGroup group = server.getRawGroup(found);
    if (group == null) return null;

    LocatedReferable result = group.referable();
    List<String> names = target.longName.toList();
    for (int i = 0; i < names.size(); i++) {
      String segment = names.get(i);
      ConcreteGroup next = findChildGroup(group, segment);
      if (next != null) {
        group = next;
        result = next.referable();
        continue;
      }
      LocatedReferable internal = findInternalReferable(group, segment);
      if (internal != null) {
        // Constructor / class field — must be the last segment.
        return i == names.size() - 1 ? new ResolvedTarget(found, internal) : null;
      }
      return null;
    }
    return new ResolvedTarget(found, result);
  }

  private static @Nullable ConcreteGroup findChildGroup(ConcreteGroup group, String name) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null && sub.referable() != null && name.equals(sub.referable().textRepresentation())) {
        return sub;
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      if (dyn.referable() != null && name.equals(dyn.referable().textRepresentation())) {
        return dyn;
      }
    }
    return null;
  }

  private static @Nullable LocatedReferable findInternalReferable(ConcreteGroup group, String name) {
    for (LocatedReferable r : group.getInternalReferables()) {
      if (name.equals(r.textRepresentation())) return r;
    }
    return null;
  }

  // ---- alias collection (Phase A) ----------------------------------------

  private static Map<ModuleLocation, Set<String>> collectLocalAliases(
      ArendServer server, List<SourceLibrary> libs, boolean withTests, Set<String> primary) {
    Map<ModuleLocation, Set<String>> perModule = new HashMap<>();
    boolean changed = true;
    int iterations = 0;
    Set<String> currentSeeds = new LinkedHashSet<>(primary);

    while (changed && iterations < 8) {
      changed = false;
      iterations++;
      for (SourceLibrary lib : libs) {
        List<ModulePath> mps = new ArrayList<>(lib.findModules(false));
        if (withTests) mps.addAll(lib.findModules(true));
        for (ModulePath mp : mps) {
          for (ModuleLocation.LocationKind kind : new ModuleLocation.LocationKind[] {
              ModuleLocation.LocationKind.SOURCE, ModuleLocation.LocationKind.TEST }) {
            if (kind == ModuleLocation.LocationKind.TEST && !withTests) continue;
            ModuleLocation moduleLoc = new ModuleLocation(lib.getLibraryName(), kind, mp);
            ConcreteGroup group = server.getRawGroup(moduleLoc);
            if (group == null) continue;
            Set<String> bucket = perModule.computeIfAbsent(moduleLoc, k -> new LinkedHashSet<>());
            int before = bucket.size();
            collectFromGroup(group, currentSeeds, bucket);
            if (bucket.size() != before) changed = true;
          }
        }
      }
      // Seed next iteration with everything observed so far (handles multi-hop).
      Set<String> nextSeeds = new LinkedHashSet<>(primary);
      for (Set<String> set : perModule.values()) nextSeeds.addAll(set);
      if (nextSeeds.equals(currentSeeds)) break;
      currentSeeds = nextSeeds;
    }
    return perModule;
  }

  private static void collectFromGroup(ConcreteGroup group, Set<String> seeds, Set<String> out) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteNamespaceCommand cmd = stmt.command();
      if (cmd != null) {
        for (ConcreteNamespaceCommand.NameRenaming r : cmd.renamings()) {
          String src = r.reference().getRefName();
          String renamed = r.newName();
          if (renamed != null && seeds.contains(src)) out.add(renamed);
        }
      }
      ConcreteGroup sub = stmt.group();
      if (sub != null) collectFromGroup(sub, seeds, out);
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      collectFromGroup(dyn, seeds, out);
    }
  }

  // ---- text scan ---------------------------------------------------------

  /** Word-boundary scan against {@code names} over a UTF-8 file. */
  private record TextHit(int line, int column) {}

  private static List<TextHit> scanFile(Path file, Set<String> names) {
    List<TextHit> hits = new ArrayList<>();
    String text;
    try { text = Files.readString(file); }
    catch (IOException e) { return hits; }

    Pattern pattern = buildPattern(names);
    if (pattern == null) return hits;

    int[] lineStarts = computeLineStarts(text);
    Matcher m = pattern.matcher(text);
    while (m.find()) {
      int start = m.start();
      int line = lineFor(lineStarts, start);
      int col = start - lineStarts[line - 1] + 1;
      hits.add(new TextHit(line, col));
    }
    return hits;
  }

  private static @Nullable Pattern buildPattern(Set<String> names) {
    if (names.isEmpty()) return null;
    StringBuilder alt = new StringBuilder();
    for (String n : names) {
      if (alt.length() > 0) alt.append('|');
      alt.append(Pattern.quote(n));
    }
    // Identifier characters in Arend names. Includes the symbol class from
    // FileUtils.DEFINITION_NAME_START_SYMBOL_REGEX plus digits and apostrophe.
    String idChar = "[A-Za-z0-9_'~!@#$%\\^&*\\-+=<>?/|\\[\\]:]";
    return Pattern.compile("(?<!" + idChar + ")(?:" + alt + ")(?!" + idChar + ")");
  }

  private static int[] computeLineStarts(String text) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') starts.add(i + 1);
    }
    int[] out = new int[starts.size()];
    for (int i = 0; i < out.length; i++) out[i] = starts.get(i);
    return out;
  }

  private static int lineFor(int[] lineStarts, int offset) {
    int lo = 0, hi = lineStarts.length - 1;
    while (lo < hi) {
      int mid = (lo + hi + 1) / 2;
      if (lineStarts[mid] <= offset) lo = mid; else hi = mid - 1;
    }
    return lo + 1;
  }

  // ---- usage visitor (Phase D) -------------------------------------------

  private record UsageHit(ModuleLocation module, String absoluteFile, int line, int column) {}

  private record VisitorContext(ModuleLocation module, String absoluteFile) {}

  private static final class UsageVisitor extends BaseConcreteExpressionVisitor<Void> {
    private final LocatedReferable target;
    final Set<UsageHit> collected = new LinkedHashSet<>();
    VisitorContext context;

    UsageVisitor(LocatedReferable target) { this.target = target; }

    private void recordRef(@Nullable Referable ref, @Nullable Object data) {
      if (ref == null || ref != target) return;
      if (!(data instanceof SourcePosition pos)) return;
      collected.add(new UsageHit(context.module, context.absoluteFile, pos.line, pos.column));
    }

    @Override
    public Concrete.Expression visitReference(Concrete.ReferenceExpression expr, Void params) {
      recordRef(expr.getReferent(), expr.getData());
      if (expr instanceof Concrete.LongReferenceExpression lr && lr.getQualifier() != null) {
        lr.getQualifier().accept(this, params);
      }
      return expr;
    }

    @Override
    public Concrete.Expression visitFieldCall(Concrete.FieldCallExpression expr, Void params) {
      recordRef(expr.getField(), expr.getData());
      return super.visitFieldCall(expr, params);
    }

    @Override
    protected void visitClassFieldImpl(Concrete.ClassFieldImpl impl, Void params) {
      recordRef(impl.getImplementedField(), impl.getData());
      super.visitClassFieldImpl(impl, params);
    }

    @Override
    protected void visitClassElement(Concrete.ClassElement element, Void params) {
      if (element instanceof Concrete.OverriddenField field) {
        recordRef(field.getOverriddenField(), field.getData());
      }
      super.visitClassElement(element, params);
    }

    @Override
    protected void visitPattern(Concrete.Pattern pattern, Void params) {
      if (pattern instanceof Concrete.ConstructorPattern cp) {
        recordRef(cp.getConstructor(), cp.getData());
      }
      super.visitPattern(pattern, params);
    }

    @Override
    public Void visitClass(Concrete.ClassDefinition def, Void params) {
      // Visit super-class references (BaseConcreteExpressionVisitor doesn't).
      for (Concrete.ReferenceExpression sup : def.getSuperClasses()) {
        sup.accept(this, params);
      }
      return super.visitClass(def, params);
    }

    @Override
    public Void visitData(Concrete.DataDefinition def, Void params) {
      // Eliminated references are not Concrete.Expressions visited otherwise.
      if (def.getEliminatedReferences() != null) {
        for (Concrete.ReferenceExpression ref : def.getEliminatedReferences()) {
          ref.accept(this, params);
        }
      }
      return super.visitData(def, params);
    }
  }

  // ---- helpers -----------------------------------------------------------

  private static List<SourceLibrary> librariesInScope(
      List<SourceLibrary> requested, LibraryManager manager, Options opts) {
    List<SourceLibrary> all = new ArrayList<>();
    for (String name : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(name);
      if (lib != null) all.add(lib);
    }
    if (opts.onlyLibraries == null) return all;

    Set<String> allow = new HashSet<>();
    for (String s : opts.onlyLibraries) {
      if ("self".equalsIgnoreCase(s)) {
        for (SourceLibrary l : requested) allow.add(l.getLibraryName());
      } else {
        allow.add(s);
      }
    }
    List<SourceLibrary> filtered = new ArrayList<>();
    for (SourceLibrary lib : all) if (allow.contains(lib.getLibraryName())) filtered.add(lib);
    return filtered;
  }

  private static Set<String> unionNames(Set<String> a, @Nullable Set<String> b) {
    if (b == null || b.isEmpty()) return a;
    Set<String> out = new LinkedHashSet<>(a);
    out.addAll(b);
    return out;
  }

  private static @Nullable Path sourcePathFor(@Nullable SourceLibrary lib, ModuleLocation moduleLoc) {
    if (!(lib instanceof FileSourceLibrary fl)) return null;
    Path src = fl.getSourceBasePath();
    if (src == null) return null;
    try {
      return FileUtils.sourceFile(src, moduleLoc.getModulePath()).toAbsolutePath().normalize();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static int[] positionOf(LocatedReferable ref) {
    Object data = ref instanceof org.arend.ext.reference.DataContainer dc ? dc.getData() : null;
    if (data instanceof SourcePosition sp) return new int[] { sp.line, sp.column };
    return new int[] { 0, 0 };
  }

  private static String kindLabel(LocatedReferable ref) {
    if (!(ref instanceof org.arend.naming.reference.GlobalReferable g)) return "OTHER";
    return g.getKind().name();
  }

  private static boolean pathEquals(@Nullable String absStr, @Nullable Path path) {
    if (path == null || absStr == null) return false;
    return absStr.equals(path.toString());
  }

  private static @Nullable String readLine(String absFile, int line) {
    if (absFile == null || absFile.isEmpty() || line <= 0) return null;
    try {
      List<String> lines = Files.readAllLines(Path.of(absFile));
      return line <= lines.size() ? lines.get(line - 1) : null;
    } catch (IOException e) {
      return null;
    }
  }
}
