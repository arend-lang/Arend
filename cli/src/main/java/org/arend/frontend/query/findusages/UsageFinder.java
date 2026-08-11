package org.arend.frontend.query.findusages;

import org.arend.error.SourcePosition;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.ArendNameCharSet;
import org.arend.frontend.query.SourcePositionUtils;
import org.arend.frontend.query.SymbolIndex;
import org.arend.frontend.source.StreamRawSource;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.source.Source;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.BaseConcreteExpressionVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The usage-search engine behind {@code -fu}: given a target definition, name-scans
 * candidate modules, resolves them, keeps the references whose referent is the target,
 * and returns the hits (declaration site excluded) sorted by source position. Presentation
 * lives in {@link FindUsagesTool}; this class only produces the {@link UsageHit} list.
 *
 * <p>Mirrors the IntelliJ plugin's {@code ArendCustomSearcher}: a hit is a name match
 * confirmed by an identity check after name-resolution. Local {@code \open}/{@code \import}
 * renamings are pre-computed to a fixed point so aliased usages are scanned for too.
 */
public final class UsageFinder {
  private UsageFinder() {}

  /**
   * A single usage. {@code ambientName}/{@code ambientKind} describe the enclosing definition
   * the usage sits in; {@code sourceLine} is the full text of the source row it is on (for the
   * highlighted-line render), or {@code null} when the source text was unavailable.
   */
  public record UsageHit(ModuleLocation module, int line, int column,
                  String ambientName, String ambientKind, @Nullable String sourceLine) {}

  /**
   * Every usage of {@code target} in {@code searchScope}, declaration site excluded,
   * sorted by (module, line, column). The libraries must already be registered on
   * {@code server} (so raw groups and resolved definitions are available).
   */
  public static List<UsageHit> find(LocatedReferable target, ModuleLocation targetModule,
      List<SourceLibrary> searchScope, ArendServer server,
      boolean withTests, boolean useAliases) {
    String aliasName = target.getAliasName();
    Set<String> primaryNames = new LinkedHashSet<>();
    primaryNames.add(target.textRepresentation());
    if (useAliases && aliasName != null) primaryNames.add(aliasName);

    // Collect per-module local aliases by inspecting \import / \open renamings.
    Map<ModuleLocation, Set<String>> perModuleNames = collectLocalAliases(server, searchScope, withTests, primaryNames);

    // Text-scan to identify candidate modules. Read each module's source through the
    // library's Source abstraction (StreamRawSource.getInputStream), NOT the filesystem,
    // so this works for zip-backed libraries too; cache the text for the line render below.
    Map<ModuleLocation, List<TextHit>> textHits = new LinkedHashMap<>();
    Map<ModuleLocation, String> moduleText = new LinkedHashMap<>();
    for (SourceLibrary lib : searchScope) {
      List<ModulePath> modules = new ArrayList<>(lib.findModules(false));
      if (withTests) modules.addAll(lib.findModules(true));
      for (ModulePath mp : modules) {
        ModuleLocation source = new ModuleLocation(lib.getLibraryName(),
            ModuleLocation.LocationKind.SOURCE, mp);
        ModuleLocation tests = new ModuleLocation(lib.getLibraryName(),
            ModuleLocation.LocationKind.TEST, mp);
        for (ModuleLocation moduleLoc : new ModuleLocation[] { source, tests }) {
          boolean inTests = moduleLoc.getLocationKind() == ModuleLocation.LocationKind.TEST;
          if (inTests && !withTests) continue;
          Set<String> names = unionNames(primaryNames, perModuleNames.get(moduleLoc));
          if (names.isEmpty()) continue;
          String text = readModuleText(lib, moduleLoc.getModulePath(), inTests);
          if (text == null) continue;
          List<TextHit> hits = scanText(text, names);
          if (!hits.isEmpty()) {
            textHits.put(moduleLoc, hits);
            moduleText.put(moduleLoc, text);
          }
        }
      }
    }

    // Resolve candidates (their transitive deps come along).
    if (!textHits.isEmpty()) {
      server.getCheckerFor(new ArrayList<>(textHits.keySet()))
          .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }

    // Walk candidates' resolved definitions, keep refs whose getReferent() == target.
    UsageVisitor visitor = new UsageVisitor(target);
    Set<UsageHit> hits = new LinkedHashSet<>();
    for (ModuleLocation moduleLoc : textHits.keySet()) {
      List<String> lines = splitLines(moduleText.get(moduleLoc));
      for (DefinitionData data : server.getResolvedDefinitions(moduleLoc)) {
        Concrete.ResolvableDefinition def = data.definition();
        if (def == null) continue;
        LocatedReferable defRef = def.getData();
        String ambientName = defRef.getRefLongName().toString();
        String ambientKind = SymbolIndex.kindOf(defRef, def).name();
        visitor.context = new VisitorContext(moduleLoc, ambientName, ambientKind, lines);
        try {
          def.accept(visitor, null);
        } catch (RuntimeException ignored) {
          // Skip malformed definitions; resolution errors are reported elsewhere.
        }
        hits.addAll(visitor.collected);
        visitor.collected.clear();
      }
    }

    // Drop the target's own declaration site, matched by module identity + position
    // (works for zip, where there is no filesystem path to compare against).
    int[] declPos = SourcePositionUtils.lineColumn(target);
    if (declPos[0] != 0) {
      hits.removeIf(h -> h.module.equals(targetModule)
          && h.line == declPos[0] && h.column == declPos[1]);
    }

    // Sort by module identity (a stable library / path / kind key) then position, so hits in
    // different modules that share a line number never interleave or collapse together.
    List<UsageHit> sorted = new ArrayList<>(hits);
    sorted.sort(Comparator.comparing((UsageHit h) -> h.module.getLibraryName())
        .thenComparing(h -> h.module.getModulePath().toString())
        .thenComparing(h -> h.module.getLocationKind())
        .thenComparingInt(h -> h.line)
        .thenComparingInt(h -> h.column));
    return sorted;
  }

  // ---- alias collection --------------------------------------------------

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

  /** Word-boundary scan against {@code names} over the module's source text. */
  private record TextHit(int line, int column) {}

  private static List<TextHit> scanText(String text, Set<String> names) {
    List<TextHit> hits = new ArrayList<>();
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

  /**
   * Reads {@code modulePath}'s raw source through the library's {@link Source} abstraction
   * (works for file and zip), decoded as UTF-8 to match the parser so byte offsets stay
   * line/col-aligned. {@code null} if the library exposes no such source (e.g. a zip's test
   * sources) or the read fails.
   */
  private static @Nullable String readModuleText(SourceLibrary lib, ModulePath modulePath, boolean inTests) {
    Source src = lib.getSource(modulePath, inTests);
    if (!(src instanceof StreamRawSource s)) return null;
    try (InputStream is = s.getInputStream()) {
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
  }

  /** Splits source text into lines (terminator excluded), indexable by {@code get(line - 1)}. */
  private static List<String> splitLines(@Nullable String text) {
    if (text == null) return List.of();
    return Arrays.asList(text.split("\n", -1));
  }

  private static @Nullable Pattern buildPattern(Set<String> names) {
    if (names.isEmpty()) return null;
    StringBuilder alt = new StringBuilder();
    for (String n : names) {
      if (!alt.isEmpty()) alt.append('|');
      alt.append(Pattern.quote(n));
    }
    // Word-boundary guard so a name embedded in a larger identifier isn't matched.
    String idChar = ArendNameCharSet.ID_CHAR_CLASS;
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

  private static Set<String> unionNames(Set<String> a, @Nullable Set<String> b) {
    if (b == null || b.isEmpty()) return a;
    Set<String> out = new LinkedHashSet<>(a);
    out.addAll(b);
    return out;
  }

  // ---- usage visitor -----------------------------------------------------

  private record VisitorContext(ModuleLocation module, String ambientName,
                                String ambientKind, List<String> lines) {}

  private static final class UsageVisitor extends BaseConcreteExpressionVisitor<Void> {
    private final LocatedReferable target;
    final Set<UsageHit> collected = new LinkedHashSet<>();
    VisitorContext context;

    UsageVisitor(LocatedReferable target) { this.target = target; }

    private void recordRef(@Nullable Referable ref, @Nullable Object data) {
      if (ref == null || ref != target) return;
      SourcePosition pos = SourcePositionUtils.of(data);
      if (pos == null) return;
      List<String> lines = context.lines;
      String sourceLine = (pos.line >= 1 && pos.line <= lines.size()) ? lines.get(pos.line - 1) : null;
      collected.add(new UsageHit(context.module, pos.line, pos.column,
          context.ambientName, context.ambientKind, sourceLine));
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
}
