package org.arend.frontend.symbol;

import org.arend.error.SourcePosition;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.reference.Precedence;
import org.arend.ext.util.Pair;
import org.arend.frontend.TimedProgressReporter;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.proof.ArendExpressionMatcher;
import org.arend.proof.ProofSearchQuery;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.FileUtils;
import org.arend.util.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;

import static org.arend.proof.Utils.getSignatures;

/**
 * Drives a single {@code -ps} / {@code :proof-search} invocation: resolve the
 * libraries in scope, then match every definition's signature (parameters +
 * codomain) against the user pattern and emit the hits.
 *
 * <p>Shared by the CLI ({@code -ps}, with optional {@code --json}) and the REPL
 * ({@code :ps}, plain text only), the same way {@link SymbolSearch} backs
 * {@code -ss} / {@code :ss}.
 */
public final class ProofSearch {
  // Same green -ps uses to highlight matched sub-terms (ConsoleMain.ANSI_*).
  private static final String ANSI_GREEN = "\u001B[32m";
  private static final String ANSI_RESET = "\u001B[0m";
  private static final String PRINT_FULL = "print-full";

  public static final class Options {
    /** Cap on printed/emitted matches; 0 = unlimited. Same default as {@code -ss}. */
    public int limit = 200;
    /** Print each match's full signature (as {@code -ss}) instead of the matching slice. */
    public boolean printFull = false;
    /** Emit results as a single JSON object instead of the human-readable listing. */
    public boolean json = false;
    /**
     * Library names to drop from the search scope. Set programmatically: the REPL
     * uses it to exclude its synthetic {@code Repl} library, which mirrors the real
     * libraries and would otherwise duplicate every hit.
     */
    public final Set<String> excludeLibraries = new HashSet<>();
  }

  public record Parsed(String pattern, Options options) {}

  /**
   * Parses the tokens passed alongside {@code -ps}. Every token is part of the
   * (single) pattern except a standalone {@code print-full} or a {@code limit=N}
   * option. Returns {@code null} with a diagnostic on stderr when no pattern is
   * given, more than one is, or {@code limit} is malformed.
   */
  public static @Nullable Parsed parseArgs(String[] args) {
    Options opts = new Options();
    List<String> patterns = new ArrayList<>();
    for (String arg : args) {
      if (arg.equals(PRINT_FULL)) {
        opts.printFull = true;
      } else if (arg.startsWith("limit=")) {
        try {
          opts.limit = Integer.parseInt(arg.substring("limit=".length()));
        } catch (NumberFormatException e) {
          System.err.println("[ERROR] Bad -ps limit: " + arg);
          return null;
        }
      } else {
        patterns.add(arg);
      }
    }
    if (patterns.isEmpty()) {
      System.err.println("[ERROR] Missing proof search pattern");
      return null;
    }
    if (patterns.size() > 1) {
      System.err.println("[ERROR] Only one proof search pattern is allowed. Use quotes if the pattern contains spaces.");
      return null;
    }
    return new Parsed(patterns.getFirst(), opts);
  }

  /**
   * Runs the search. Human-readable output and the {@code [INFO] Resolving ...}
   * chatter go to {@code System.out}/{@code System.err} (the caller redirects or
   * captures them); the JSON document, in {@code json} mode, goes to {@code jsonOut}.
   * Returns {@code false} only on a malformed pattern.
   */
  public static boolean run(@NotNull String pattern,
                            @NotNull Options options,
                            @NotNull List<SourceLibrary> requestedLibraries,
                            @NotNull LibraryManager libraryManager,
                            @NotNull ArendServer server,
                            @NotNull PrintStream jsonOut) {
    ProofSearchQuery.ParsingResult<ProofSearchQuery> queryResult = ProofSearchQuery.fromString(pattern);
    if (queryResult == null) return false;
    if (queryResult instanceof ProofSearchQuery.ParsingResult.Error<ProofSearchQuery> error) {
      System.err.println("Search pattern error at " + error.range + ": " + error.message);
      return false;
    }
    ProofSearchQuery query = ((ProofSearchQuery.ParsingResult.OK<ProofSearchQuery>) queryResult).value;
    ArendExpressionMatcher matcher = new ArendExpressionMatcher(query);

    // Mirror -ss: the library field is redundant (and so omitted) whenever only a
    // single non-prelude library is loaded, and shown when a match could come from
    // any of several.
    int nonPreludeLibs = 0;
    for (String name : libraryManager.getLibraries()) {
      if (!options.excludeLibraries.contains(name) && !Prelude.LIBRARY_NAME.equals(name)) nonPreludeLibs++;
    }
    boolean omitLibrary = nonPreludeLibs <= 1;
    List<ResultJson.Row> rows = options.json ? new ArrayList<>() : null;

    for (SourceLibrary library : requestedLibraries) {
      if (options.excludeLibraries.contains(library.getLibraryName())) continue;
      System.out.println("[INFO] Resolving " + library.getLibraryName());
      long time = System.currentTimeMillis();
      server.getCheckerFor(library.findModules(false).stream().map(modulePath -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath)).toList())
              .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      System.out.println("[INFO] Resolved " + library.getLibraryName() + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
    }

    // Count every match for the total, but only emit up to `limit` (0 = all). The
    // matcher still runs for every definition, so the total is exact even when the
    // printed/collected list is truncated -- same contract as -ss.
    int total = 0;
    int printed = 0;
    for (ModuleLocation moduleLocation : server.getModules()) {
      if (options.excludeLibraries.contains(moduleLocation.getLibraryName())) continue;
      String file = null;                 // computed lazily, only in JSON mode
      boolean fileComputed = false;
      for (DefinitionData data : server.getResolvedDefinitions(moduleLocation)) {
        for (Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression> signature : getSignatures(data.definition())) {
          TCDefReferable referable = signature.first().getData();
          List<Concrete.Expression> parameters = signature.second();
          Concrete.Expression codomain = signature.third();

          Scope scope = server.getReferableScope(data.definition().getData());

          ArendExpressionMatcher.ProofSearchMatchingResult result = matcher.match(parameters, codomain, scope);
          if (result == null) continue;
          total++;
          if (options.limit > 0 && printed >= options.limit) continue;
          printed++;

          if (options.json) {
            if (!fileComputed) { file = sourceFileFor(moduleLocation, libraryManager); fileComputed = true; }
            int line = 0, col = 0;
            if (referable.getData() instanceof SourcePosition sp) { line = sp.line; col = sp.column; }
            // print-full -> the -ss-style signature of the matched definition;
            // otherwise -> the matched type slice (parameters -> codomain).
            String sig = options.printFull ? SignaturePrintVisitor.render(signature.first()) : null;
            String expr = options.printFull ? null : buildProofExpression(result, codomain);
            rows.add(new ResultJson.Row(
                omitLibrary ? null : moduleLocation.getLibraryName(),
                moduleLocation.getModulePath().toString(),
                referable.getRefLongName().toString(),
                SymbolIndex.kindOf(referable, signature.first()).name(),
                sig, expr, file, line, col));
            continue;
          }

          if (referable.getData() != null) {
            System.out.println(referable.getRefName() + " " + referable.getData().toString());
          } else {
            System.out.println(referable.getRefFullName().toString());
          }

          // print-full prints the definition's signature (same as -ss), not its full body.
          if (options.printFull) {
            System.out.println(SignaturePrintVisitor.render(signature.first()));
            System.out.println();
            continue;
          }

          Set<Concrete.SourceNode> highlightedNodes = new HashSet<>(result.inCodomain());
          if (result.inPattern() != null) {
            for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
              highlightedNodes.addAll(parameterData.proj2);
            }
          }
          highlightedNodes.addAll(result.inCodomain());

          Precedence topPrec = new Precedence(Concrete.Expression.PREC);
          if (result.inPattern() != null) {
            for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
              StringBuilder builder = new StringBuilder();
              HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(builder, 0, highlightedNodes);
              // Route through printExpr (not Expression.prettyPrint, which dispatches
              // straight to accept()) so the whole parameter node is checked against the
              // highlight set -- otherwise a match at the top of the parameter is missed.
              visitor.printExpr(parameterData.proj1, topPrec);
              System.out.print("(" + builder + ") -> ");
            }
          }
          StringBuilder builder = new StringBuilder();
          HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(builder, 0, highlightedNodes);
          visitor.printExpr(codomain, topPrec);
          System.out.println(builder);
          System.out.println();
        }
      }
    }
    // `rows` is already capped at `limit` by the emit guard; `count` is the exact
    // total so callers can tell how many matches were truncated.
    if (options.json) {
      ResultJson.write(jsonOut, rows, total);
    } else if (total == 0) {
      System.out.println("No matches.");
    } else {
      System.out.println("Found " + total + " match" + (total == 1 ? "" : "es")
          + (options.limit > 0 && total > options.limit
              ? " (showing " + options.limit + "; pass `limit=0` for all)" : ""));
    }
    return true;
  }

  /**
   * Renders the matched type slice for a JSON {@code expression} field:
   * {@code (param) -> ... -> codomain}, matching the plain-text
   * (non-{@code print-full}) listing but without ANSI highlighting.
   */
  private static String buildProofExpression(ArendExpressionMatcher.ProofSearchMatchingResult result,
                                             Concrete.Expression codomain) {
    Precedence topPrec = new Precedence(Concrete.Expression.PREC);
    StringBuilder sb = new StringBuilder();
    if (result.inPattern() != null) {
      for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
        StringBuilder b = new StringBuilder();
        parameterData.proj1.prettyPrint(new PrettyPrintVisitor(b, 0), topPrec);
        sb.append('(').append(b).append(") -> ");
      }
    }
    StringBuilder cb = new StringBuilder();
    codomain.prettyPrint(new PrettyPrintVisitor(cb, 0), topPrec);
    sb.append(cb);
    return sb.toString();
  }

  /**
   * The source file of {@code moduleLocation}, shortened to library-relative form
   * (as {@code -ss} reports it), or {@code null} for generated modules / when the
   * path can't be resolved.
   */
  private static @Nullable String sourceFileFor(ModuleLocation moduleLocation, LibraryManager libraryManager) {
    if (moduleLocation.getLocationKind() != ModuleLocation.LocationKind.SOURCE) return null;
    SourceLibrary lib = libraryManager.getLibrary(moduleLocation.getLibraryName());
    if (!(lib instanceof FileSourceLibrary fl)) return null;
    Path base = fl.getSourceBasePath();
    if (base == null) return null;
    try {
      Path abs = FileUtils.sourceFile(base, moduleLocation.getModulePath()).toAbsolutePath().normalize();
      return PathDisplay.shorten(abs, libraryManager);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Pretty-printer that wraps matched sub-terms/parameters in ANSI green. */
  private static final class HighlightingPrettyPrintVisitor extends PrettyPrintVisitor {
    private final Set<Concrete.SourceNode> highlightedNodes;
    private int highlightCount = 0;

    HighlightingPrettyPrintVisitor(StringBuilder builder, int indent, Set<Concrete.SourceNode> highlightedNodes) {
      super(builder, indent);
      this.highlightedNodes = highlightedNodes;
    }

    @Override
    protected PrettyPrintVisitor copy(StringBuilder builder, int indent, boolean doIndent) {
      return new HighlightingPrettyPrintVisitor(builder, indent, highlightedNodes);
    }

    @Override
    public void printExpr(Concrete.Expression expr, Precedence prec) {
      if (highlightedNodes.contains(expr)) {
        myBuilder.append(ANSI_GREEN);
        highlightCount++;
      }
      super.printExpr(expr, prec);
      if (highlightedNodes.contains(expr)) {
        highlightCount--;
        if (highlightCount == 0) {
          myBuilder.append(ANSI_RESET);
        }
      }
    }

    @Override
    public void prettyPrintParameter(Concrete.Parameter parameter) {
      if (highlightedNodes.contains(parameter)) {
        myBuilder.append(ANSI_GREEN);
        highlightCount++;
      }
      super.prettyPrintParameter(parameter);
      if (highlightedNodes.contains(parameter)) {
        highlightCount--;
        if (highlightCount == 0) {
          myBuilder.append(ANSI_RESET);
        }
      }
    }
  }
}
