package org.arend.frontend.query.findusages;

import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.ConsoleHelp;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.*;
import org.arend.frontend.query.findusages.UsageFinder.UsageHit;
import org.arend.repl.Repl;
import org.arend.naming.reference.LocatedReferable;
import org.arend.server.ArendServer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

/**
 * The {@code -fu} query tool: finds every usage of a definition across the libraries
 * in scope and prints them as an {@code -ss}-style listing (grouped by source row) or
 * as JSON ({@code --json}). A usage is a name match confirmed by an identity check
 * after name-resolution; local {@code \open} / {@code \import} renamings are followed.
 */
public final class FindUsagesTool extends ConsoleQueryTool {
  /** Singleton shared by the CLI (its {@code -fu} flag) and the REPL (its {@code :fu} command). */
  public static final FindUsagesTool INSTANCE = new FindUsagesTool();
  private FindUsagesTool() {}

  @Override public String shortName() { return "fu"; }

  @Override public String longName() { return "find-usages"; }

  @Override public String argName() { return "MODULE:DEF"; }

  @Override public String cliDescription() {
    return "find every usage of a definition. Pass `-fu --help` for full grammar.";
  }
  @Override public void printHelp() { ConsoleHelp.printFindUsages(); }

  // ---- REPL command (:fu / :find-usages) ----------------------------------

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Find every usage of a definition across registered libraries (`:? fu` for the full grammar)";
  }

  @Override
  public @Nls @NotNull String help(@NotNull Repl api) {
    return ConsoleHelp.findUsagesReplHelp();
  }

  @Override protected String replLabel() { return "Find usages"; }

  // ---- options ------------------------------------------------------------

  public static final class Options extends QueryOptions {
    public boolean withTests = false;
    public boolean printLine = true;
    public boolean useAliases = true;
    { limit = 500; }   // usage sets run larger than the shared default
  }

  public record ToolRunner(String spec, Options options) implements ConsoleToolRunner {
    @Override public int run(QueryContext ctx) {
      ctx.applyTo(options);
      try {
        LibraryManager libraryManager = ctx.libraryManager();
        ArendServer server = ctx.server();
        PrintStream out = ctx.out();
        // Two scopes: the target is resolved against the full scope (so a usage query can
        // name any loaded definition, including one in a dependency), while usages are
        // hunted only in the search scope — narrowed to the requested libraries by `self`.
        List<SourceLibrary> fullScope = ctx.librariesInScope();
        if (fullScope.isEmpty()) {
          System.err.println("[ERROR] No libraries in scope.");
          if (options.json) ResultJson.write(out, List.of(), 0);
          return 1;
        }
        List<SourceLibrary> searchScope = ctx.searchLibraries(options.self);
        boolean showLibrary = QualifiedName.containsMultipleNonPreludeLibraries(fullScope);

        // 1) Refresh symbol indexes for in-scope libraries. This backs bare-name
        //    lookup and gets the server to register every source module, so
        //    getRawGroup works during resolution and the text scan below.
        Map<SourceLibrary, SymbolIndex> indexes = SymbolIndex.refreshAll(fullScope, server, options.withTests);

        // 2) Resolve the spec (qualified `MODULE:DEF` or bare long-name) to a single
        //    target referable through the shared resolver.
        TargetResolver.Target resolved = TargetResolver.resolve(spec, "-fu", server, fullScope, indexes,
            showLibrary, null, "definition");
        if (resolved == null) {
          if (options.json) ResultJson.write(out, List.of(), 0);
          return 1;
        }
        LocatedReferable targetReferable = resolved.referable();
        ModuleLocation targetModule = resolved.module();

        // 3) Find usages: name-scan candidate modules, resolve, keep identity matches
        //    (declaration site dropped), sorted by source position.
        List<UsageHit> sorted = UsageFinder.find(targetReferable, targetModule, searchScope, server,
            options.withTests, options.useAliases);

        // 4) This search resolves but never typechecks, so a field usage whose receiver type is
        //    only inferred stays an unresolved field reference and is invisible here — warn for fields.
        if (isField(targetReferable)) {
          System.err.println("[WARN] '" + targetReferable.getRefLongName()
              + "' is a field; usages whose receiver type is only inferred during typechecking"
              + " are NOT found (this search performs name-resolving but not typechecking).");
        }

        // 5) Render (JSON or -ss-style text) through the uniform Printer dispatch.
        Target target = new Target(targetReferable, targetModule, kindLabel(targetReferable), showLibrary);
        FindUsagesPrinter printer = options.json ? new JsonFindUsagesPrinter() : new TextFindUsagesPrinter();
        printer.print(out, target, sorted, options, libraryManager);
        return 0;
      } catch (RuntimeException e) {
        System.err.println("[ERROR] -fu internal error: " + e);
        if (options.json) ResultJson.write(ctx.out(), List.of(), 0);
        return 1;
      }
    }
  }

  /**
   * Parses sub-tokens passed alongside {@code -fu}. The first token that
   * doesn't look like an option is the target spec.
   */
  @Override
  public @Nullable FindUsagesTool.ToolRunner parseArgs(String[] args) {
    Options opts = new Options();
    String[] spec = {null};
    boolean ok = new QueryArgs.Tokens()
        .flag("with-tests", () -> opts.withTests = true)
        .flag("no-line", () -> opts.printLine = false)
        .flag("aliases=true", () -> opts.useAliases = true)
        .flag("aliases=false", () -> opts.useAliases = false)
        .self(opts)
        .limit(opts)
        .positional(arg -> {
          if (spec[0] == null) spec[0] = arg;
          else throw new QueryArgs.ArgError("Multiple specs: '" + spec[0] + "' and '" + arg + "'.");
        })
        .parse("-fu", args);
    if (!ok) return null;
    if (spec[0] == null) {
      System.err.println("[ERROR] -fu requires a <MODULE_PATH>:<GROUP_PATH> spec");
      return null;
    }
    return new ToolRunner(spec[0], opts);
  }

  // ---- target + printers -------------------------------------------------

  private static String kindLabel(LocatedReferable ref) {
    if (!(ref instanceof org.arend.naming.reference.GlobalReferable g)) return "OTHER";
    return g.getKind().name();
  }

  private static boolean isField(LocatedReferable ref) {
    return ref instanceof org.arend.naming.reference.GlobalReferable g
        && g.getKind() == org.arend.naming.reference.GlobalReferable.Kind.FIELD;
  }

  /** The resolved target definition plus the display flags its rendering needs. */
  record Target(LocatedReferable referable, ModuleLocation module, String kind, boolean showLibrary) {}

  interface FindUsagesPrinter {
    void print(PrintStream out, Target target, List<UsageHit> hits, Options options, LibraryManager lm);
  }

}
