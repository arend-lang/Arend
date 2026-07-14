package org.arend.frontend.symbol;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.Scope.ScopeContext;
import org.arend.server.ArendServer;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements `-sc` referable-scope dump, intended for debugging
 * reference-resolution issues.
 *
 * Pipeline:
 *   1. Resolve a {@code MODULE_PATH:GROUP_PATH} (or bare-name via the symbol
 *      index) into a {@link LocatedReferable}.
 *   2. Call {@link ArendServer#getReferableScope(LocatedReferable)} for the
 *      ambient scope at that referable's position.
 *   3. List entries (optionally filtered by a {@link SymbolPattern}), one per
 *      line, as {@code SHORT_NAME -> LIB::MODULE:LONG_NAME}.
 */
public final class ReferableScope {

  // ---- options + arg parsing ---------------------------------------------

  public enum Ctx { STATIC, DYNAMIC, STATIC_AND_DYNAMIC, ALL }

  public static final class Options {
    // No context= token: static and dynamic entries merged into one sorted list.
    public Ctx context = Ctx.STATIC_AND_DYNAMIC;
    /** Emit the scope as a single JSON object instead of the human-readable listing. */
    public boolean json = false;
    /**
     * Library names to drop from the search scope. Set programmatically (not from a
     * user token): the REPL uses it to exclude its synthetic {@code Repl} library,
     * which mirrors the real ones and would otherwise make bare-name resolution
     * ambiguous.
     */
    public final Set<String> excludeLibraries = new HashSet<>();
  }

  public record Parsed(@NotNull String spec, @Nullable SymbolPattern pattern, @NotNull Options options) {}

  /**
   * The first non-option positional arg is the referable spec; the second is
   * an optional name-filter pattern (same grammar as {@code -ss}).
   */
  public static @Nullable Parsed parseArgs(String[] args) {
    Options opts = new Options();
    String spec = null;
    String patternSrc = null;
    for (String arg : args) {
      switch (arg) {
        case "context=static" -> opts.context = Ctx.STATIC;
        case "context=dynamic" -> opts.context = Ctx.DYNAMIC;
        case "context=all" -> opts.context = Ctx.ALL;
        default -> {
          if (arg.startsWith("context=")) {
            System.err.println("[ERROR] Unknown -sc context: " + arg);
            return null;
          } else if (spec == null) {
            spec = arg;
          } else if (patternSrc == null) {
            patternSrc = arg;
          } else {
            System.err.println("[ERROR] -sc accepts at most a spec and a pattern: extra '" + arg + "'");
            return null;
          }
        }
      }
    }
    if (spec == null) {
      System.err.println("[ERROR] -sc requires a <MODULE_PATH>:<GROUP_PATH> (or bare-name) spec");
      return null;
    }
    SymbolPattern pattern = null;
    if (patternSrc != null) {
      try {
        pattern = SymbolPattern.compile(patternSrc);
      } catch (IllegalArgumentException e) {
        System.err.println("[ERROR] Bad -sc pattern: " + e.getMessage());
        return null;
      }
    }
    return new Parsed(spec, pattern, opts);
  }

  // ---- top-level driver --------------------------------------------------

  public static int run(@NotNull String spec,
                        @Nullable SymbolPattern pattern,
                        @NotNull Options options,
                        @NotNull List<SourceLibrary> requestedLibraries,
                        @NotNull LibraryManager libraryManager,
                        @NotNull ArendServer server,
                        @NotNull ErrorReporter errorReporter,
                        @NotNull PrintStream out) {
    List<SourceLibrary> libsInScope = librariesInScope(libraryManager, options);
    if (libsInScope.isEmpty()) {
      System.err.println("[ERROR] No libraries in scope.");
      if (options.json) writeJson(out, null, List.of());
      return 0;
    }
    boolean showLibrary = QualifiedName.showLibrary(libsInScope);

    // Refresh symbol indexes so bare-name lookup works AND every source module
    // is registered on the server (the server needs the raw group of the
    // target's module for getReferableScope to walk into).
    Map<SourceLibrary, SymbolIndex> indexes = new LinkedHashMap<>();
    for (SourceLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      for (ModulePath mp : lib.findModules(false)) {
        if (idx.isStale(lib, mp)) {
          server.findModule(mp, lib.getLibraryName(), false, false);
        }
      }
      idx.refresh(lib, server, false);
      idx.save();
      indexes.put(lib, idx);
    }

    Resolved target = resolveTarget(spec, server, libsInScope, indexes, showLibrary);
    if (target == null) {
      if (options.json) writeJson(out, null, List.of());
      return 0;
    }

    Scope scope = server.getReferableScope(target.referable);
    if (scope == null) {
      System.err.println("[ERROR] No scope available at " + label(target, showLibrary));
      if (options.json) writeJson(out, label(target, showLibrary), List.of());
      return 0;
    }

    return dumpScope(scope, label(target, showLibrary), pattern, options, showLibrary, out);
  }

  /**
   * REPL entry point for {@code :sc} with no spec: dumps the caller-supplied
   * current session {@code scope} directly, skipping target resolution and index
   * refresh. {@code libsInScope} only decides whether the {@code LIBRARY::} prefix
   * is shown.
   */
  public static int runCurrentScope(@NotNull Scope scope, @Nullable SymbolPattern pattern,
      @NotNull Options options, @NotNull List<SourceLibrary> libsInScope, @NotNull PrintStream out) {
    return dumpScope(scope, "current REPL scope", pattern, options,
        QualifiedName.showLibrary(libsInScope), out);
  }

  /**
   * Dumps {@code scope} under the header/target {@code targetLabel}, honouring the
   * context selection, optional {@code pattern} filter and JSON mode. Shared by the
   * {@code -sc <spec>} path and the REPL {@code :sc} current-scope path.
   */
  private static int dumpScope(@NotNull Scope scope, @NotNull String targetLabel,
      @Nullable SymbolPattern pattern, @NotNull Options options, boolean showLibrary, @NotNull PrintStream out) {
    if (options.json) return runJson(scope, targetLabel, pattern, options, showLibrary, out);

    System.out.println("--- Scope at " + targetLabel + " ---");
    int total = 0;
    int matched = 0;
    if (options.context == Ctx.ALL) {
      // Print each context section in turn.
      matched += dumpSection(scope, ScopeContext.STATIC,  "STATIC",  pattern, showLibrary);
      matched += dumpSection(scope, ScopeContext.DYNAMIC, "DYNAMIC", pattern, showLibrary);
      matched += dumpSection(scope, ScopeContext.PLEVEL,  "PLEVEL",  pattern, showLibrary);
      matched += dumpSection(scope, ScopeContext.HLEVEL,  "HLEVEL",  pattern, showLibrary);
      total = scope.getElements(null).size();
    } else if (options.context == Ctx.STATIC_AND_DYNAMIC) {
      // Default: static and dynamic entries as a single sorted list (not two sections).
      List<Referable> elements = new ArrayList<>();
      elements.addAll(scope.getElements(ScopeContext.STATIC));
      elements.addAll(scope.getElements(ScopeContext.DYNAMIC));
      total = distinctCount(elements, showLibrary);
      matched = printEntries(elements, pattern, showLibrary);
    } else {
      ScopeContext ctx = options.context == Ctx.DYNAMIC ? ScopeContext.DYNAMIC : ScopeContext.STATIC;
      Collection<? extends Referable> elements = scope.getElements(ctx);
      total = distinctCount(elements, showLibrary);
      matched = printEntries(elements, pattern, showLibrary);
    }

    if (pattern != null) {
      System.out.println("--- " + matched + " match(es) of " + total + " entries (pattern: " + pattern.source() + ") ---");
    } else {
      System.out.println("--- " + total + " entries ---");
    }
    return 0;
  }

  private static int dumpSection(Scope scope, ScopeContext ctx, String header,
      @Nullable SymbolPattern pattern, boolean showLibrary) {
    Collection<? extends Referable> elements = scope.getElements(ctx);
    if (elements.isEmpty()) return 0;
    System.out.println();
    System.out.println("[" + header + "]");
    return printEntries(elements, pattern, showLibrary);
  }

  private static int printEntries(Collection<? extends Referable> elements,
      @Nullable SymbolPattern pattern, boolean showLibrary) {
    // Sort by short name (then full line) so the dump has a stable, readable order,
    // and dedup identical lines: a merged scope (e.g. the REPL's, or STATIC+DYNAMIC)
    // can surface the same binding twice.
    Set<String> lines = new LinkedHashSet<>();
    for (Referable ref : elements) {
      String name = ref.textRepresentation();
      if (pattern != null && !pattern.matches(name)) continue;
      lines.add(name + " -> " + targetLabel(ref, showLibrary));
    }
    List<String> sorted = new ArrayList<>(lines);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    for (String line : sorted) System.out.println(line);
    return sorted.size();
  }

  /** Number of DISTINCT rendered entries (same key used by {@link #printEntries}), unfiltered. */
  private static int distinctCount(Collection<? extends Referable> elements, boolean showLibrary) {
    Set<String> lines = new HashSet<>();
    for (Referable ref : elements) lines.add(ref.textRepresentation() + " -> " + targetLabel(ref, showLibrary));
    return lines.size();
  }

  private static String targetLabel(Referable ref, boolean showLibrary) {
    if (ref instanceof LocatedReferable lr) {
      ModuleLocation loc = lr.getLocation();
      LongName ln = lr.getRefLongName();
      if (loc == null) return "?:" + ln + " [" + lr.getKind() + "]";
      return QualifiedName.format(showLibrary, loc.getLibraryName(), loc.getModulePath().toString(),
          ln.toString()) + " [" + lr.getKind() + "]";
    }
    return "(local " + ref.getClass().getSimpleName() + ")";
  }

  // ---- JSON output -------------------------------------------------------

  /** One rendered scope entry plus its in-scope name, used to sort the JSON array deterministically. */
  private record JsonRow(String name, String body) {}

  /**
   * Emits {@code {"target": "...", "entries": [...], "count": N}} on {@code out}.
   * Each entry carries the in-scope {@code name} (the key you would write to
   * reference it here), the {@code context} it lives in (STATIC/DYNAMIC/PLEVEL/
   * HLEVEL), and either the resolved target ({@code kind}, {@code module},
   * {@code longName}, optional {@code library}) or {@code "local": true} with a
   * {@code refType} for locally-bound referables that have no global location.
   */
  private static int runJson(Scope scope, String targetLabel, @Nullable SymbolPattern pattern,
                             Options options, boolean showLibrary, PrintStream out) {
    List<JsonRow> rows = new ArrayList<>();
    if (options.context == Ctx.ALL) {
      collectJson(scope, ScopeContext.STATIC,  "STATIC",  pattern, showLibrary, rows);
      collectJson(scope, ScopeContext.DYNAMIC, "DYNAMIC", pattern, showLibrary, rows);
      collectJson(scope, ScopeContext.PLEVEL,  "PLEVEL",  pattern, showLibrary, rows);
      collectJson(scope, ScopeContext.HLEVEL,  "HLEVEL",  pattern, showLibrary, rows);
    } else if (options.context == Ctx.STATIC_AND_DYNAMIC) {
      collectJson(scope, ScopeContext.STATIC,  "STATIC",  pattern, showLibrary, rows);
      collectJson(scope, ScopeContext.DYNAMIC, "DYNAMIC", pattern, showLibrary, rows);
    } else {
      ScopeContext ctx = options.context == Ctx.DYNAMIC ? ScopeContext.DYNAMIC : ScopeContext.STATIC;
      collectJson(scope, ctx, ctx.name(), pattern, showLibrary, rows);
    }
    // Dedup identical entries (a merged scope can surface the same binding twice);
    // entries in different contexts keep distinct bodies, so they survive.
    LinkedHashMap<String, JsonRow> uniq = new LinkedHashMap<>();
    for (JsonRow r : rows) uniq.putIfAbsent(r.body(), r);
    rows = new ArrayList<>(uniq.values());
    // Same order as the text dump: by in-scope name (case-insensitive), then body.
    rows.sort(Comparator.comparing(JsonRow::name, String.CASE_INSENSITIVE_ORDER).thenComparing(JsonRow::body));
    writeJson(out, targetLabel, rows);
    return rows.size();
  }

  private static void collectJson(Scope scope, ScopeContext ctx, String context,
      @Nullable SymbolPattern pattern, boolean showLibrary, List<JsonRow> rows) {
    for (Referable ref : scope.getElements(ctx)) {
      String name = ref.textRepresentation();
      if (pattern != null && !pattern.matches(name)) continue;
      rows.add(new JsonRow(name, jsonEntry(context, ref, showLibrary)));
    }
  }

  private static String jsonEntry(String context, Referable ref, boolean showLibrary) {
    StringBuilder sb = new StringBuilder("{");
    sb.append("\"name\":\"").append(ResultJson.escape(ref.textRepresentation())).append('"');
    sb.append(",\"context\":\"").append(context).append('"');
    if (ref instanceof LocatedReferable lr) {
      sb.append(",\"kind\":\"").append(ResultJson.escape(String.valueOf(lr.getKind()))).append('"');
      ModuleLocation loc = lr.getLocation();
      if (loc != null) {
        if (showLibrary) sb.append(",\"library\":\"").append(ResultJson.escape(loc.getLibraryName())).append('"');
        sb.append(",\"module\":\"").append(ResultJson.escape(loc.getModulePath().toString())).append('"');
      }
      sb.append(",\"longName\":\"").append(ResultJson.escape(lr.getRefLongName().toString())).append('"');
    } else {
      sb.append(",\"local\":true,\"refType\":\"").append(ResultJson.escape(ref.getClass().getSimpleName())).append('"');
    }
    return sb.append('}').toString();
  }

  private static void writeJson(PrintStream out, @Nullable String target, List<JsonRow> rows) {
    String targetField = target == null ? "" : "\"target\": \"" + ResultJson.escape(target) + "\", ";
    if (rows.isEmpty()) {
      out.println("{" + targetField + "\"entries\": [], \"count\": 0}");
      return;
    }
    out.println("{");
    if (target != null) out.println("  \"target\": \"" + ResultJson.escape(target) + "\",");
    out.println("  \"entries\": [");
    for (int i = 0; i < rows.size(); i++) {
      out.println("    " + rows.get(i).body() + (i < rows.size() - 1 ? "," : ""));
    }
    out.println("  ],");
    out.println("  \"count\": " + rows.size());
    out.println("}");
  }

  // ---- target resolution -------------------------------------------------

  private record Resolved(LocatedReferable referable, ModuleLocation module, String libraryName) {}

  private static String label(Resolved r, boolean showLibrary) {
    return QualifiedName.format(showLibrary, r.libraryName, r.module.getModulePath().toString(),
        r.referable.getRefLongName().toString());
  }

  private static @Nullable Resolved resolveTarget(String spec, ArendServer server,
      List<SourceLibrary> libsInScope, Map<SourceLibrary, SymbolIndex> indexes, boolean showLibrary) {
    // Accept an optional `library::` prefix (see QualifiedName.splitLibrary); the
    // library scopes module resolution below.
    QualifiedName.Split split = QualifiedName.splitLibrary(spec);
    String fromLibrary = split.library();
    String rest = split.rest();
    int colon = rest.indexOf(':');
    if (colon >= 0) {
      String modStr = rest.substring(0, colon);
      String defStr = rest.substring(colon + 1);
      if (modStr.isEmpty() || defStr.isEmpty()) {
        System.err.println("[ERROR] empty module or definition path in -sc spec '" + spec + "'");
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
      ModuleLocation found = server.findModule(mp, fromLibrary, true, true);
      if (found == null) {
        System.err.println("[ERROR] Module not found: " + modStr);
        return null;
      }
      QualifiedName.warnAmbiguousModule(fromLibrary != null, mp, found.getLibraryName(), libsInScope);
      ConcreteGroup group = server.getRawGroup(found);
      if (group == null) {
        System.err.println("[ERROR] Module not loaded: " + modStr);
        return null;
      }
      LocatedReferable ref = walkLongName(group, ln);
      if (ref == null) {
        System.err.println("[ERROR] Definition not found: " + spec);
        return null;
      }
      return new Resolved(ref, found, found.getLibraryName());
    }

    // Bare-name lookup via the symbol index, accepting any kind.
    List<SymbolIndex.Entry> matches = new ArrayList<>();
    Map<SymbolIndex.Entry, SourceLibrary> libOf = new HashMap<>();
    for (Map.Entry<SourceLibrary, SymbolIndex> e : indexes.entrySet()) {
      for (SymbolIndex.Entry entry : e.getValue().allEntries()) {
        if (entry.shortName().equals(spec)) {
          matches.add(entry);
          libOf.put(entry, e.getKey());
        }
      }
    }
    if (matches.isEmpty()) {
      System.err.println("[ERROR] No definition named '" + spec + "' in scope. Use -ss to find candidates.");
      return null;
    }
    if (matches.size() > 1) {
      System.err.println("[ERROR] '" + spec + "' is ambiguous. Use one of:");
      List<String> labels = new ArrayList<>();
      for (SymbolIndex.Entry e : matches) {
        labels.add("  " + QualifiedName.format(showLibrary, libOf.get(e).getLibraryName(),
            e.modulePath().toString(), e.longName()));
      }
      Collections.sort(labels);
      for (String l : labels) System.err.println(l);
      return null;
    }
    SymbolIndex.Entry only = matches.getFirst();
    SourceLibrary lib = libOf.get(only);
    ModulePath mp = only.modulePath();
    ModuleLocation moduleLoc = server.findModule(mp, lib.getLibraryName(), true, true);
    if (moduleLoc == null) {
      System.err.println("[ERROR] Could not load module " + mp);
      return null;
    }
    LongName ln = LongName.fromString(only.longName());
    ConcreteGroup group = server.getRawGroup(moduleLoc);
    if (group == null) {
      System.err.println("[ERROR] Module not loaded: " + mp);
      return null;
    }
    LocatedReferable ref = walkLongName(group, ln);
    if (ref == null) {
      System.err.println("[ERROR] Definition not found via index: " + only.longName());
      return null;
    }
    System.out.println("[INFO] Resolved '" + spec + "' -> "
        + QualifiedName.format(showLibrary, lib.getLibraryName(), mp.toString(), only.longName()));
    return new Resolved(ref, moduleLoc, lib.getLibraryName());
  }

  private static @Nullable LocatedReferable walkLongName(ConcreteGroup group, LongName ln) {
    LocatedReferable result = group.referable();
    List<String> names = ln.toList();
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
        return i == names.size() - 1 ? internal : null;
      }
      return null;
    }
    return result;
  }

  private static @Nullable ConcreteGroup findChildGroup(ConcreteGroup group, String name) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null && sub.referable() != null
          && name.equals(sub.referable().textRepresentation())) {
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

  private static List<SourceLibrary> librariesInScope(LibraryManager manager, Options options) {
    List<SourceLibrary> all = new ArrayList<>();
    for (String name : manager.getLibraries()) {
      if (options.excludeLibraries.contains(name)) continue;
      SourceLibrary lib = manager.getLibrary(name);
      if (lib != null) all.add(lib);
    }
    return all;
  }
}
