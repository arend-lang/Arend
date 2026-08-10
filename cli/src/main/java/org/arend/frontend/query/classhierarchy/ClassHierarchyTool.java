package org.arend.frontend.query.classhierarchy;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.ConsoleHelp;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.*;
import org.arend.frontend.query.classhierarchy.ClassHierarchy.ClassNode;
import org.arend.repl.Repl;
import org.arend.naming.reference.LocatedReferable;
import org.arend.server.ArendServer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;

/**
 * The {@code -ch} query tool: reports the super/subclass lattice around a class or
 * record, together with its {@code \new} and {@code \instance} sites. Parses and resolves
 * the target, builds the {@link ClassHierarchy} graph, then dispatches to a {@link ClassHierarchyPrinter}
 * — {@link TreeClassHierarchyPrinter} (default), {@link FlatClassHierarchyPrinter} ({@code format=flat}), or
 * {@link JsonClassHierarchyPrinter} ({@code --json}) — each its own class.
 */
public final class ClassHierarchyTool extends ConsoleQueryTool {
  /** Singleton shared by the CLI (its {@code -ch} flag) and the REPL (its {@code :ch} command). */
  public static final ClassHierarchyTool INSTANCE = new ClassHierarchyTool();
  private ClassHierarchyTool() {}

  @Override public String shortName() { return "ch"; }

  @Override public String longName() { return "class-hierarchy"; }

  @Override public String argName() { return "MODULE:CLASS|name"; }

  @Override public String cliDescription() {
    return "print super/sub-class trees plus \\new and \\instance sites. Pass `-ch --help` for full grammar.";
  }
  @Override public void printHelp() { ConsoleHelp.printClassHierarchy(); }

  // ---- REPL command (:ch / :class-hierarchy) ------------------------------

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Print the inheritance lattice around a class plus its \\instance / \\new sites (`:? ch` for the full grammar)";
  }

  @Override
  public @Nls @NotNull String help(@NotNull Repl api) {
    return ConsoleHelp.classHierarchyHelp();
  }

  @Override protected String replLabel() { return "Class hierarchy"; }

  // ---- options + arg parsing ---------------------------------------------

  public enum Direction { BOTH, UP, DOWN }
  public enum Format { TREE, FLAT }

  public static final class Options extends QueryOptions {
    public Direction direction = Direction.BOTH;
    public Format format = Format.TREE;
    public boolean noInstances = false;
    public boolean noNews = false;
    public boolean withFields = false;
    public boolean withTests = false;
  }

  public record ToolRunner(String spec, Options options) implements ConsoleToolRunner {
    @Override public int run(QueryContext ctx) {
      ctx.applyTo(options);
      try {
        LibraryManager libraryManager = ctx.libraryManager();
        ArendServer server = ctx.server();
        PrintStream out = ctx.out();
        // Two scopes: the target class is resolved against the full scope (so a class in
        // a dependency can be named), while the hierarchy graph is built by walking only
        // the search scope — narrowed to the requested libraries by `self`, which yields a
        // "my slice" view (subclasses / instances / \new sites in your own libraries).
        List<SourceLibrary> fullScope = ctx.librariesInScope();
        if (fullScope.isEmpty()) {
          System.err.println("[ERROR] No libraries in scope.");
          if (options.json) writeEmptyJson(out);
          return 1;
        }
        List<SourceLibrary> searchScope = ctx.searchLibraries(options.self);
        boolean showLibrary = QualifiedName.containsMultipleNonPreludeLibraries(fullScope);

        // 1) Refresh per-library symbol indexes (needed for bare-name lookup and
        //    cheaply registers every source module on the server so we can query
        //    raw groups below).
        Map<SourceLibrary, SymbolIndex> indexes = SymbolIndex.refreshAll(fullScope, server, options.withTests);

        // 2) Resolve target spec: qualified, or bare-name via index (bare names are
        //    restricted to classes/records; a qualified spec may resolve anything and is
        //    kind-checked just below).
        TargetResolver.Target resolved = TargetResolver.resolve(this.spec(), "-ch", server, fullScope, indexes,
            showLibrary, Set.of(SymbolIndex.Kind.CLASS, SymbolIndex.Kind.RECORD), "class or record");
        if (resolved == null) {
          if (options.json) writeEmptyJson(out);
          return 1;
        }
        ResolvedTarget target = new ResolvedTarget(resolved.referable(), resolved.module(),
            SymbolIndex.kindOf(resolved.referable(), null), resolved.library(), showLibrary);
        SymbolIndex.Kind targetKind = target.kind();
        if (targetKind != SymbolIndex.Kind.CLASS && targetKind != SymbolIndex.Kind.RECORD) {
          System.err.println("[ERROR] " + target.fullLabel() + " is " + targetKind + ", not a class/record.");
          if (options.json) writeEmptyJson(out);
          return 1;
        }

        // 3) Build the inheritance graph + \new / \instance sites over the search scope.
        List<ModuleLocation> allSources = new ArrayList<>();
        for (SourceLibrary lib : searchScope) {
          for (ModulePath mp : lib.findModules(false)) {
            allSources.add(new ModuleLocation(lib.getLibraryName(),
                ModuleLocation.LocationKind.SOURCE, mp));
          }
          if (options.withTests) {
            for (ModulePath mp : lib.findModules(true)) {
              allSources.add(new ModuleLocation(lib.getLibraryName(),
                  ModuleLocation.LocationKind.TEST, mp));
            }
          }
        }
        ClassHierarchy hierarchy = ClassHierarchy.build(allSources, server);

        // 4) Print (tree, flat, or JSON) through the uniform Printer dispatch.
        ClassHierarchyPrinter classHierarchyPrinter = options.json ? new JsonClassHierarchyPrinter()
            : options.format == Format.FLAT ? new FlatClassHierarchyPrinter() : new TreeClassHierarchyPrinter();
        classHierarchyPrinter.print(out, target, hierarchy, options, libraryManager);
        return 0;
      } catch (RuntimeException e) {
        System.err.println("[ERROR] -ch internal error: " + e);
        if (options.json) writeEmptyJson(ctx.out());
        return 1;
      }
    }
  }

  @Override
  public @Nullable ClassHierarchyTool.ToolRunner parseArgs(String[] args) {
    Options opts = new Options();
    String[] spec = {null};
    boolean ok = new QueryArgs.Tokens()
        .flag("up", () -> opts.direction = Direction.UP)
        .flag("down", () -> opts.direction = Direction.DOWN)
        .flag("no-instances", () -> opts.noInstances = true)
        .flag("no-news", () -> opts.noNews = true)
        .flag("with-fields", () -> opts.withFields = true)
        .flag("with-tests", () -> opts.withTests = true)
        .self(opts)
        .param("format", v -> {
          switch (v) {
            case "tree" -> opts.format = Format.TREE;
            case "flat" -> opts.format = Format.FLAT;
            default -> throw new QueryArgs.ArgError("Unknown format: format=" + v);
          }
        })
        .limit(opts)
        .positional(arg -> {
          if (spec[0] == null) spec[0] = arg;
          else throw new QueryArgs.ArgError("Multiple specs: '" + spec[0] + "' and '" + arg + "'.");
        })
        .parse("-ch", args);
    if (!ok) return null;
    if (spec[0] == null) {
      System.err.println("[ERROR] -ch requires a class spec");
      return null;
    }
    return new ToolRunner(spec[0], opts);
  }

  // ---- target resolution -------------------------------------------------

  record ResolvedTarget(LocatedReferable referable, ModuleLocation module, SymbolIndex.Kind kind,
                        String libraryName, boolean showLibrary) {

    String fullLabel() {
        return QualifiedName.format(showLibrary, libraryName, module.getModulePath().toString(),
                referable.getRefLongName().toString());
      }
    }

  // ---- printers ----------------------------------------------------------

  /** How one {@code -ch} run is rendered; implemented by {@link TreeClassHierarchyPrinter}, {@link FlatClassHierarchyPrinter}, {@link JsonClassHierarchyPrinter}. */
  interface ClassHierarchyPrinter {
    void print(PrintStream out, ResolvedTarget target, ClassHierarchy hierarchy, Options options, LibraryManager libraryManager);
  }

  // ---- shared render helpers (used by the printers) ----------------------

  /** Prints the "... N more" truncation notice and returns true once {@code printed}
   *  has reached a positive {@code limit}, signaling the caller to stop. */
  static boolean limitReached(PrintStream out, int printed, int total, int limit) {
    if (limit > 0 && printed >= limit) {
      out.println("  ... " + (total - printed) + " more (raise limit=N)");
      return true;
    }
    return false;
  }

  /** The site records in the target's closure, ordered by (module path, line, column). */
  static <T extends ClassHierarchy.Site> List<T> sortedSites(ClassHierarchy hierarchy, List<T> sites,
      LocatedReferable target, Direction direction, LibraryManager lm) {
    List<T> out = hierarchy.sitesInClosure(sites, ClassHierarchy.Site::targetClass, target, direction);
    out.sort(Comparator.comparing((T s) -> sortKeyPath(s.module(), lm))
        .thenComparingInt(ClassHierarchy.Site::line)
        .thenComparingInt(ClassHierarchy.Site::column));
    return out;
  }

  /** The site's location as {@code path:line:column}. */
  static String siteLocation(ClassHierarchy.Site s, LibraryManager lm) {
    return PathDisplayUtils.label(s.module(), lm) + ":" + s.line() + ":" + s.column();
  }

  static String qualifiedLabel(LocatedReferable ref, ClassHierarchy hierarchy, boolean showLibrary) {
    ModuleLocation moduleLoc = hierarchy.moduleOf(ref);
    if (moduleLoc == null) return "?:" + ref.getRefLongName();
    return QualifiedName.format(showLibrary, moduleLoc.getLibraryName(),
        moduleLoc.getModulePath().toString(), ref.getRefLongName().toString());
  }

  static String nodeLabel(ClassNode node, ClassHierarchy hierarchy,
      LibraryManager libraryManager, Options options) {
    StringBuilder sb = new StringBuilder();
    sb.append(node.referable.getRefLongName());
    String pos = positionLabel(node.referable, hierarchy.moduleOf(node.referable), libraryManager);
    if (!pos.isEmpty()) sb.append("    (").append(pos).append(")");
    if (options.withFields && !node.directFieldNames.isEmpty()) {
      sb.append("  fields: ").append(setLabel(node.directFieldNames));
    }
    return sb.toString();
  }

  static String positionLabel(LocatedReferable ref, @Nullable ModuleLocation moduleLoc,
      LibraryManager libraryManager) {
    if (moduleLoc == null) return "";
    String pathLabel = PathDisplayUtils.label(moduleLoc, libraryManager);
    int[] pos = SourcePositionUtils.lineColumn(ref);
    return pos[0] > 0 ? pathLabel + ":" + pos[0] + ":" + pos[1] : pathLabel;
  }


  /**
   * Sort key for a module location: the absolute source path string when available,
   * otherwise a synthetic {@code <lib:module>} label.
   */
  private static String sortKeyPath(ModuleLocation moduleLoc, LibraryManager libraryManager) {
    Path p = PathDisplayUtils.sourcePath(libraryManager.getLibrary(moduleLoc.getLibraryName()), moduleLoc);
    return p != null ? p.toString()
        : "<" + moduleLoc.getLibraryName() + ":" + moduleLoc.getModulePath() + ">";
  }

  static String setLabel(Set<String> set) {
    if (set.isEmpty()) return "{}";
    return String.join(",", new TreeSet<>(set));
  }

  // ---- JSON output -------------------------------------------------------

  private static void writeEmptyJson(PrintStream out) {
    out.println("{\"target\": null, \"superclasses\": [], \"subclasses\": [], "
        + "\"instances\": [], \"newSites\": [], \"counts\": {\"instances\":0,\"newSites\":0}}");
  }

}
