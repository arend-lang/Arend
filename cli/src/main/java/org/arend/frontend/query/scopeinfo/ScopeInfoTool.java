package org.arend.frontend.query.scopeinfo;

import org.arend.frontend.ConsoleHelp;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.*;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.repl.Repl;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.scope.Scope;
import org.arend.server.ArendServer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements `-sc` referable-scope dump, intended for debugging
 * reference-resolution issues.
 * Pipeline:
 *   1. Resolve a {@code MODULE_PATH:GROUP_PATH} (or bare-name via the symbol
 *      index) into a {@link LocatedReferable}.
 *   2. Call {@link ArendServer#getReferableScope(LocatedReferable)} for the
 *      ambient scope at that referable's position.
 *   3. List entries (optionally filtered by a {@link SymbolPattern}), one per
 *      line, as {@code SHORT_NAME -> LIB::MODULE:LONG_NAME}.
 */
public final class ScopeInfoTool extends ConsoleQueryTool {
  /** Singleton shared by the CLI (its {@code -sc} flag) and the REPL (its {@code :sc} command). */
  public static final ScopeInfoTool INSTANCE = new ScopeInfoTool();
  private ScopeInfoTool() {}

  @Override public String shortName() { return "sc"; }

  @Override public String longName() { return "scope"; }

  @Override public String argName() { return "MODULE:PATH|name"; }

  @Override public String cliDescription() {
    return "dump the ambient scope at a referable's position. Pass `-sc --help` for full grammar.";
  }  @Override public void printHelp() { ConsoleHelp.printScope(); }

  // ---- REPL command (:sc / :scope) ----------------------------------------

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Dump the ambient scope at a referable (`:sc <spec>`), or the current session scope (plain `:sc`); `:? sc` for the grammar";
  }

  @Override
  public @Nls @NotNull String help(@NotNull Repl api) {
    return ConsoleHelp.scopeHelp();
  }

  @Override protected String replLabel() { return "Scope dump"; }

  /**
   * Plain {@code :sc} (no tokens) dumps the current session scope directly, skipping
   * target resolution; anything else parses as usual.
   */
  @Override
  protected @Nullable ConsoleQueryTool.ConsoleToolRunner parseReplLine(@NotNull List<String> tokens, @NotNull CommonCliRepl repl) {
    if (!tokens.isEmpty()) return parseArgs(tokens.toArray(new String[0]));
    Scope scope = repl.getServerScope();
    if (scope == null) {
      repl.eprintln("[ERROR] No current scope available.");
      return null;
    }
    return ctx -> {
      @NotNull Options options = new Options();
      @NotNull List<SourceLibrary> libsInScope = ctx.librariesInScope();
      return dumpScope(scope, "current REPL scope", null, options,
          QualifiedName.containsMultipleNonPreludeLibraries(libsInScope), ctx.out());
    };
  }

  // ---- options + arg parsing ---------------------------------------------

  public enum ScopeSelection { STATIC, DYNAMIC, STATIC_AND_DYNAMIC, ALL }

  public static final class Options extends QueryOptions {
    // No context= token: static and dynamic entries merged into one sorted list.
    public ScopeSelection context = ScopeSelection.STATIC_AND_DYNAMIC;
  }

  public record ToolRunner(@NotNull String spec, @Nullable SymbolPattern pattern, @NotNull Options options) implements ConsoleToolRunner {
    @Override public int run(QueryContext ctx) {
      ctx.applyTo(options);
      try {
        ArendServer server = ctx.server();
        PrintStream out = ctx.out();
        List<SourceLibrary> libsInScope = ctx.librariesInScope();
        if (libsInScope.isEmpty()) {
          System.err.println("[ERROR] No libraries in scope.");
          if (options.json) JsonScopePrinter.writeEmpty(out, null);
          return 1;
        }
        boolean showLibrary = QualifiedName.containsMultipleNonPreludeLibraries(libsInScope);

        // Refresh symbol indexes so bare-name lookup works AND every source module
        // is registered on the server (the server needs the raw group of the
        // target's module for getReferableScope to walk into).
        Map<SourceLibrary, SymbolIndex> indexes = SymbolIndex.refreshAll(libsInScope, server, false);

        TargetResolver.Target resolved = TargetResolver.resolve(this.spec(), "-sc", server, libsInScope, indexes,
            showLibrary, null, "definition");
        if (resolved == null) {
          if (options.json) JsonScopePrinter.writeEmpty(out, null);
          return 1;
        }
        Scope scope = server.getReferableScope(resolved.referable());
        if (scope == null) {
          System.err.println("[ERROR] No scope available at " + targetLabel(resolved, showLibrary));
          if (options.json) JsonScopePrinter.writeEmpty(out, targetLabel(resolved, showLibrary));
          return 1;
        }

        dumpScope(scope, targetLabel(resolved, showLibrary), this.pattern(), options, showLibrary, out);
        return 0;
      } catch (RuntimeException e) {
        System.err.println("[ERROR] -sc internal error: " + e);
        if (options.json) JsonScopePrinter.writeEmpty(ctx.out(), null);
        return 1;
      }
    }
  }

  /**
   * The first non-option positional arg is the referable spec; the second is
   * an optional name-filter pattern (same grammar as {@code -ss}).
   */
  @Override
  public @Nullable ScopeInfoTool.ToolRunner parseArgs(String[] args) {
    Options opts = new Options();
    String[] spec = {null};
    String[] patternSrc = {null};
    boolean ok = new QueryArgs.Tokens()
        .param("context", v -> {
          switch (v) {
            case "static" -> opts.context = ScopeSelection.STATIC;
            case "dynamic" -> opts.context = ScopeSelection.DYNAMIC;
            case "all" -> opts.context = ScopeSelection.ALL;
            default -> throw new QueryArgs.ArgError("Unknown context: context=" + v);
          }
        })
        .positional(arg -> {
          if (spec[0] == null) spec[0] = arg;
          else if (patternSrc[0] == null) patternSrc[0] = arg;
          else throw new QueryArgs.ArgError("accepts at most a spec and a pattern: extra '" + arg + "'");
        })
        .parse("-sc", args);
    if (!ok) return null;
    if (spec[0] == null) {
      System.err.println("[ERROR] -sc requires a <MODULE_PATH>:<GROUP_PATH> (or bare-name) spec");
      return null;
    }
    SymbolPattern pattern = null;
    if (patternSrc[0] != null) {
      try {
        pattern = SymbolPattern.compile(patternSrc[0]);
      } catch (IllegalArgumentException e) {
        System.err.println("[ERROR] Bad -sc pattern: " + e.getMessage());
        return null;
      }
    }
    return new ToolRunner(spec[0], pattern, opts);
  }

  // ---- printer dispatch --------------------------------------------------

  /** Renders {@code scope} as text, or JSON when {@code --json} is set; returns the entry count. */
  private static int dumpScope(@NotNull Scope scope, @NotNull String targetLabel,
      @Nullable SymbolPattern pattern, @NotNull Options options, boolean showLibrary, @NotNull PrintStream out) {
    ScopePrinter printer = options.json ? new JsonScopePrinter() : new TextScopePrinter();
    return printer.print(scope, targetLabel, pattern, options, showLibrary, out);
  }

  /**
   * Dumps {@code scope} under the header/target {@code targetLabel}, honouring the context
   * selection and optional {@code pattern} filter, and returns the entry count. Implemented by
   * {@link TextScopePrinter} and {@link JsonScopePrinter}.
   */
  interface ScopePrinter {
    int print(Scope scope, String targetLabel, @Nullable SymbolPattern pattern,
              Options options, boolean showLibrary, PrintStream out);
  }

  /** The resolved target's own qualified label, used as the dump header. */
  private static String targetLabel(TargetResolver.Target r, boolean showLibrary) {
    return QualifiedName.format(showLibrary, r.library(), r.module().getModulePath().toString(),
        r.referable().getRefLongName().toString());
  }
}
