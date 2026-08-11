package org.arend.frontend.query;

import org.arend.ext.error.ErrorReporter;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.repl.Repl;
import org.arend.repl.action.AliasableCommand;
import org.arend.server.ArendServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One console query tool ({@code -ss}/{@code -ps}/{@code -fu}/{@code -sc}/{@code -ch}):
 * its CLI option identity, argument parsing, help text, and execution. Bundling these
 * lets {@code ConsoleMain} register, help-print, and dispatch every tool by iterating a
 * single registry instead of repeating a near-identical block per tool.
 *
 * <p>Parsing yields an {@link ConsoleToolRunner} -- a parsed, self-runnable query -- so the
 * dispatcher can execute any tool's result through one interface, with no per-tool
 * parsed-type parameter.
 *
 * <p>Extends {@link AliasableCommand} because every query tool is also a REPL command
 * (it supplies its own {@code invoke}/{@code description}/{@code help}). The shared
 * parameterless constructor seeds the empty, mutable alias list the REPL clears on each
 * (re)registration, so {@code ConsoleMain} can hold the tools as {@code ConsoleQueryTool}
 * and the REPL can register them as {@link AliasableCommand}s with no downcast.
 */
public abstract class ConsoleQueryTool extends AliasableCommand {
  protected ConsoleQueryTool() {
    super(new ArrayList<>());
  }

  /**
   * Split a REPL command line into arguments, honouring double quotes the way a
   * shell would (the REPL does no shell processing of its own, so without this a
   * pattern like {@code "re:.-comm"} would keep its quotes and be rejected). A
   * double quote is never a valid Arend identifier character, so a {@code "..."}
   * group is unambiguous: its contents become one argument with the quotes
   * removed and interior spaces preserved. Single quotes are left as-is --
   * apostrophe IS a valid Arend name character ({@code f'}, {@code iabs_-'}),
   * not a quote.
   */
  public static List<String> tokenizeArgs(String line) {
    List<String> tokens = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuote = false, started = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuote = !inQuote;
        started = true;
      } else if (!inQuote && Character.isWhitespace(c)) {
        if (started) {
          tokens.add(cur.toString());
          cur.setLength(0);
          started = false;
        }
      } else {
        cur.append(c);
        started = true;
      }
    }
    if (started) tokens.add(cur.toString());
    return tokens;
  }

  /** Short flag / Commons CLI option key, e.g. {@code "fu"} for {@code -fu}. */
  public abstract String shortName();

  /** Long option, e.g. {@code "find-usages"} for {@code --find-usages}. */
  public abstract String longName();

  /** The {@code argName} shown for the option's argument in help, e.g. {@code "MODULE:DEF"}. */
  public abstract String argName();

  /**
   * One-line option description for the CLI's grouped {@code --help} listing. Named
   * distinctly from {@link org.arend.repl.action.ReplCommand#description()} (the REPL
   * {@code :help} sentence) so a tool can supply both without a signature clash.
   */
  public abstract String cliDescription();

  /** Prints this tool's full grammar (the {@code <flag> --help} text). */
  public abstract void printHelp();

  /**
   * Parses the tool's sub-arguments into a runnable {@link ConsoleToolRunner}; returns
   * {@code null} (after a diagnostic) on failure. Each tool narrows the return type
   * to its own {@code Parsed} record.
   */
  public abstract @Nullable ConsoleQueryTool.ConsoleToolRunner parseArgs(String[] args);

  // ---- REPL command (shared invoke) --------------------------------------

  /**
   * Shared REPL entry point for every tool ({@code :ss}/{@code :ps}/…): run the shared
   * {@link CommonCliRepl#runSearchCommand search scaffolding}, parse the line into a
   * {@link ConsoleToolRunner}, and run it against the REPL's query context. Tools vary only
   * in {@link #replLabel} and {@link #parseReplLine}.
   */
  @Override
  public final void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
    if (!(api instanceof CommonCliRepl repl)) return;
    repl.runSearchCommand("[ERROR] " + replLabel() + " is unavailable (no library manager on this server).",
        (manager, libs, capture) -> {
          ConsoleToolRunner parsed = parseReplLine(tokenizeArgs(line), repl);
          if (parsed != null) parsed.run(repl.replQueryContext(manager, libs, capture));
        });
  }

  /** Human label for the REPL "&lt;X&gt; is unavailable" diagnostic, e.g. {@code "Symbol search"}. */
  protected abstract String replLabel();

  /**
   * Turns a {@code :}-command's already-tokenized arguments into a runnable
   * {@link ConsoleToolRunner}, or {@code null} (after any diagnostic) when there is nothing to
   * run. The default parses the tokens exactly as the CLI does; tools whose REPL line
   * needs pre-processing (a rejoined pattern, an empty-input shortcut) override this.
   */
  protected @Nullable ConsoleQueryTool.ConsoleToolRunner parseReplLine(@NotNull List<String> tokens, @NotNull CommonCliRepl repl) {
    return parseArgs(tokens.toArray(new String[0]));
  }

  public interface ConsoleToolRunner {
    int run(QueryContext ctx);
  }

  /**
   * The shared dependencies a tool runs against; built by both the CLI (fresh server) and
   * the REPL (hot {@link ArendServer}), so the tool need not know which it queries. The two
   * library sets it exposes — the full {@link #librariesInScope()} vs the top-level
   * {@link #requestedLibraries()} — are chosen between by {@link #searchLibraries(boolean)}.
   *
   * @param requestedLibraries the top-level libraries (drives {@code self})
   * @param excludeLibraries   library names to drop from scope (the REPL excludes its
   *                           synthetic {@code Repl} library; the CLI passes an empty set)
   */
  public record QueryContext(List<SourceLibrary> requestedLibraries,
                             LibraryManager libraryManager,
                             ArendServer server,
                             ErrorReporter errorReporter,
                             PrintStream out,
                             boolean json,
                             Set<String> excludeLibraries) {
    /**
     * Applies the environment flags this context carries onto a tool's parsed options.
     * (Scope exclusion is not among them: it is applied authoritatively in
     * {@link #librariesInScope()}, which reads {@code excludeLibraries} directly.)
     */
    public void applyTo(QueryOptions options) {
      options.json = json;
    }

    /**
     * The full search scope: every library registered in the manager except those named
     * in {@code excludeLibraries}. Includes dependencies (Prelude, transitive libraries).
     * Computed fresh from the manager, so it is authoritative regardless of what the
     * caller passed as {@code requestedLibraries}.
     */
    public List<SourceLibrary> librariesInScope() {
      List<SourceLibrary> all = new ArrayList<>();
      for (String name : libraryManager.getLibraries()) {
        if (excludeLibraries.contains(name)) continue;
        SourceLibrary lib = libraryManager.getLibrary(name);
        if (lib != null) all.add(lib);
      }
      return all;
    }

    /**
     * The libraries a tool should examine for results: the full {@link #librariesInScope()}
     * normally, or just the top-level {@link #requestedLibraries()} when {@code self} drops
     * dependencies. In the REPL the two coincide, so {@code self} is a no-op there.
     */
    public List<SourceLibrary> searchLibraries(boolean self) {
      return self ? requestedLibraries : librariesInScope();
    }
  }
}
