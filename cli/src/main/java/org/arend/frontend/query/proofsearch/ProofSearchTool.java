package org.arend.frontend.query.proofsearch;

import org.arend.frontend.ConsoleHelp;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.*;
import org.arend.frontend.query.symbolsearch.SymbolSearchTool;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.repl.Repl;
import org.arend.proof.ProofSearchQuery;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

/**
 * Drives a single {@code -ps} / {@code :proof-search} invocation: resolve the
 * libraries in scope, then match every definition's signature (parameters +
 * codomain) against the user pattern, and emit the hits.
 *
 * <p>Shared by the CLI ({@code -ps}, with optional {@code --json}) and the REPL
 * ({@code :ps}, plain text only), the same way {@link SymbolSearchTool} backs
 * {@code -ss} / {@code :ss}. The search itself lives in {@link ProofSearchEngine};
 * rendering in {@link TextProofSearchPrinter} / {@link JsonProofSearchPrinter}.
 */
public final class ProofSearchTool extends ConsoleQueryTool {
  /** Singleton shared by the CLI (its {@code -ps} flag) and the REPL (its {@code :ps} command). */
  public static final ProofSearchTool INSTANCE = new ProofSearchTool();
  private ProofSearchTool() {}

  @Override public String shortName() { return "ps"; }

  @Override public String longName() { return "proof-search"; }

  @Override public String argName() { return "sig-pattern"; }

  @Override public String cliDescription() {
    return "search by signature shape (parameters/codomain). Pass `-ps --help` for the full grammar.";
  }

  @Override public void printHelp() { ConsoleHelp.printProofSearch(); }

  // ---- REPL command (:ps / :proof-search) ---------------------------------

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Search registered libraries for definitions by signature shape (`:? ps` for the full grammar)";
  }

  @Override
  public @Nls @NotNull String help(@NotNull Repl api) {
    return ConsoleHelp.proofSearchReplHelp();
  }

  @Override protected String replLabel() { return "Proof search"; }

  /**
   * The whole line is one structured pattern with spaces ({@code Monoid -> _ = _}), so
   * unlike {@code :ss} we do NOT treat separate tokens as separate patterns: pull out the
   * option tokens ({@code print-full}, {@code limit=N}) and rejoin the rest as the single
   * pattern before parsing.
   */
  @Override
  protected @Nullable ConsoleQueryTool.ConsoleToolRunner parseReplLine(@NotNull List<String> tokens, @NotNull CommonCliRepl repl) {
    List<String> optionTokens = new ArrayList<>();
    List<String> patternTokens = new ArrayList<>();
    for (String t : tokens) {
      if (t.equals(PRINT_FULL) || t.equals("self") || t.startsWith("limit=")) optionTokens.add(t);
      else patternTokens.add(t);
    }
    String pattern = String.join(" ", patternTokens).trim();
    List<String> psArgs = new ArrayList<>();
    if (!pattern.isEmpty()) psArgs.add(pattern);
    psArgs.addAll(optionTokens);
    return parseArgs(psArgs.toArray(new String[0]));
  }

  private static final String PRINT_FULL = "print-full";

  public static final class Options extends QueryOptions {
    /** Print each match's full signature (as {@code -ss}) instead of the matching slice. */
    public boolean printFull = false;
  }

  public record ToolRunner(String pattern, Options options) implements ConsoleToolRunner {
    @Override public int run(QueryContext ctx) {
      ctx.applyTo(options);
      try {
        ProofSearchQuery.ParsingResult<ProofSearchQuery> queryResult = ProofSearchQuery.fromString(pattern);
        if (queryResult == null) return 1;
        if (queryResult instanceof ProofSearchQuery.ParsingResult.Error<ProofSearchQuery> error) {
          System.err.println("Search pattern error at " + error.range + ": " + error.message);
          return 1;
        }
        ProofSearchQuery query = ((ProofSearchQuery.ParsingResult.OK<ProofSearchQuery>) queryResult).value;

        List<SourceLibrary> searchLibs = ctx.searchLibraries(options.self);
        // Mirror -ss: the library field is redundant (and so omitted) whenever only a
        // single non-prelude library is loaded, and shown when a match could come from any
        // of several (QualifiedName.containsMultipleNonPreludeLibraries is the shared rule).
        boolean omitLibrary = !QualifiedName.containsMultipleNonPreludeLibraries(searchLibs);

        ProofSearchEngine.Result found = ProofSearchEngine.find(query, searchLibs, ctx.server(),
            ctx.excludeLibraries(), options.self, options.limit);

        // Text output goes to System.out (unchanged in the REPL); JSON to the context stream.
        PrintStream out = options.json ? ctx.out() : System.out;
        ProofSearchPrinter printer = options.json ? new JsonProofSearchPrinter() : new TextProofSearchPrinter();
        printer.print(out, found, options, ctx.libraryManager(), omitLibrary);
        return 0;
      } catch (RuntimeException e) {
        System.err.println("[ERROR] -ps internal error: " + e);
        if (options.json) ResultJson.write(ctx.out(), List.of(), 0);
        return 1;
      }
    }
  }

  /** Renders a completed proof search; implemented by {@link TextProofSearchPrinter} and {@link JsonProofSearchPrinter}. */
  interface ProofSearchPrinter {
    void print(PrintStream out, ProofSearchEngine.Result found, Options options, LibraryManager lm, boolean omitLibrary);
  }

  /**
   * Parses the tokens passed alongside {@code -ps}. Every token is part of the
   * (single) pattern except a standalone {@code print-full} / {@code self} flag or a
   * {@code limit=N} option. Returns {@code null} with a diagnostic on stderr when no
   * pattern is given, more than one is, or {@code limit} is malformed.
   */
  @Override
  public @Nullable ProofSearchTool.ToolRunner parseArgs(String[] args) {
    Options opts = new Options();
    List<String> patterns = new ArrayList<>();
    boolean ok = new QueryArgs.Tokens()
        .flag(PRINT_FULL, () -> opts.printFull = true)
        .self(opts)
        .limit(opts)
        .positional(patterns::add)
        .parse("-ps", args);
    if (!ok) return null;
    if (patterns.isEmpty()) {
      System.err.println("[ERROR] Missing proof search pattern");
      return null;
    }
    if (patterns.size() > 1) {
      System.err.println("[ERROR] Only one proof search pattern is allowed. Use quotes if the pattern contains spaces.");
      return null;
    }
    return new ToolRunner(patterns.getFirst(), opts);
  }

}
