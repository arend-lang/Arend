package org.arend.frontend.cli.commands;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.reference.Precedence;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.Scope;
import org.arend.proof.ArendExpressionMatcher;
import org.arend.proof.ProofSearchQuery;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.Triple;
import org.arend.frontend.TimedProgressReporter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.arend.proof.Utils.getSignatures;

/**
 * The {@code -ps} (proof-search) handler. Moved verbatim out of {@code ConsoleMain}.
 */
public final class ProofSearch {
  /** Marker token in {@code -ps} args that switches on whole-definition pretty-printing. */
  public static final String PRINT_FULL = "print-full";

  private static final String ANSI_GREEN = "[32m";
  private static final String ANSI_RESET = "[0m";

  private ProofSearch() {}

  /** Parse {@code -ps} args + run the search. Returns false on parse error or pattern misuse. */
  public static boolean run(CommandContext ctx, String[] psArgs) {
    boolean printFull = false;
    List<String> patterns = new ArrayList<>();
    for (String arg : psArgs) {
      if (arg.equals(PRINT_FULL)) {
        printFull = true;
      } else {
        patterns.add(arg);
      }
    }
    if (patterns.isEmpty()) {
      System.err.println("[ERROR] Missing proof search pattern");
      return false;
    }
    if (patterns.size() > 1) {
      System.err.println("[ERROR] Only one proof search pattern is allowed. Use quotes if the pattern contains spaces.");
      return false;
    }
    return matchAndPrint(ctx.server, ctx.libraryManager, ctx.requestedLibraries, patterns.getFirst(), printFull);
  }

  private static boolean matchAndPrint(ArendServer server, LibraryManager libraryManager,
                                       List<SourceLibrary> requestedLibraries, String pattern, boolean printFull) {
    ProofSearchQuery.ParsingResult<ProofSearchQuery> queryResult = ProofSearchQuery.fromString(pattern);
    if (queryResult == null) return false;
    if (queryResult instanceof ProofSearchQuery.ParsingResult.Error<ProofSearchQuery> error) {
      System.err.println("Search pattern error at " + error.range + ": " + error.message);
      return false;
    }
    ProofSearchQuery query = ((ProofSearchQuery.ParsingResult.OK<ProofSearchQuery>) queryResult).value;
    ArendExpressionMatcher matcher = new ArendExpressionMatcher(query);

    for (SourceLibrary library : requestedLibraries) {
      System.out.println("[INFO] Resolving " + library.getLibraryName());
      long time = System.currentTimeMillis();
      server.getCheckerFor(library.findModules(false).stream()
              .map(modulePath -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath))
              .toList())
          .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      System.out.println("[INFO] " + "Resolved " + library.getLibraryName()
          + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
    }

    int matches = 0;
    for (ModuleLocation moduleLocation : server.getModules()) {
      for (DefinitionData data : server.getResolvedDefinitions(moduleLocation)) {
        for (Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression> signature
            : getSignatures(data.definition())) {
          TCDefReferable referable = signature.first().getData();
          List<Concrete.Expression> parameters = signature.second();
          Concrete.Expression codomain = signature.third();

          Scope scope = server.getReferableScope(data.definition().getData());

          ArendExpressionMatcher.ProofSearchMatchingResult result = matcher.match(parameters, codomain, scope);
          if (result == null) continue;
          matches++;

          Set<Concrete.SourceNode> highlightedNodes = new HashSet<>(result.inCodomain());
          if (result.inPattern() != null) {
            for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
              highlightedNodes.addAll(parameterData.proj2);
            }
          }

          // Render the signature into a single buffer so we can indent it uniformly.
          StringBuilder sigBuilder = new StringBuilder();
          Precedence topPrec = new Precedence(Concrete.Expression.PREC);
          if (printFull) {
            HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(sigBuilder, 0, highlightedNodes);
            data.definition().accept(visitor, null);
          } else {
            if (result.inPattern() != null) {
              for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
                HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(sigBuilder, 0, highlightedNodes);
                sigBuilder.append("(");
                parameterData.proj1.prettyPrint(visitor, topPrec);
                sigBuilder.append(") -> ");
              }
            }
            HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(sigBuilder, 0, highlightedNodes);
            codomain.prettyPrint(visitor, topPrec);
          }

          System.out.println(headerLineFor(moduleLocation, referable, libraryManager));
          System.out.println(moduleLocation.getLibraryName() + "::" + referable.getRefLongName()
              + "  [" + kindLabel(referable, signature.first()) + "]");
          System.out.println(indentMultiline(sigBuilder.toString(), "  "));
          System.out.println();
        }
      }
    }

    if (matches == 0) {
      System.out.println("No matches.");
    } else {
      System.out.println("Found " + matches + " match" + (matches == 1 ? "" : "es"));
    }
    return true;
  }

  private static String headerLineFor(ModuleLocation moduleLocation, TCDefReferable referable, LibraryManager libraryManager) {
    String libName = moduleLocation.getLibraryName();
    SourceLibrary lib = libraryManager.getLibrary(libName);
    if (lib instanceof org.arend.frontend.library.FileSourceLibrary fl
        && moduleLocation.getLocationKind() == ModuleLocation.LocationKind.SOURCE) {
      Path src = fl.getSourceBasePath();
      if (src != null) {
        try {
          Path abs = org.arend.util.FileUtils.sourceFile(src, moduleLocation.getModulePath()).toAbsolutePath().normalize();
          int line = 0, col = 0;
          if (referable.getData() instanceof org.arend.error.SourcePosition sp) {
            line = sp.line;
            col = sp.column;
          }
          return line > 0 ? abs + ":" + line + ":" + col : abs.toString();
        } catch (RuntimeException ignored) {
          // fall through to synthetic label
        }
      }
    }
    return "<" + libName + ":" + moduleLocation.getModulePath() + ">";
  }

  private static String kindLabel(TCDefReferable ref, Concrete.GeneralDefinition def) {
    if (def instanceof Concrete.BaseFunctionDefinition fdef) {
      return switch (fdef.getKind()) {
        case FUNC -> "FUNCTION";
        case SFUNC -> "SFUNC";
        case LEMMA -> "LEMMA";
        case TYPE -> "TYPE";
        case AXIOM -> "AXIOM";
        case INSTANCE -> "INSTANCE";
        case COERCE -> "COERCE";
        case LEVEL -> "LEVEL";
        case FUNC_COCLAUSE, CLASS_COCLAUSE -> "COCLAUSE";
        case CONS -> "CONSTRUCTOR";
      };
    }
    if (def instanceof Concrete.MetaDefinition) return "META";
    if (def instanceof Concrete.DataDefinition) return "DATA";
    if (def instanceof Concrete.ClassDefinition cdef) return cdef.isRecord() ? "RECORD" : "CLASS";
    if (def instanceof Concrete.Constructor) return "CONSTRUCTOR";
    if (def instanceof Concrete.ClassField) return "FIELD";
    return ref.getKind().name();
  }

  private static String indentMultiline(String s, String prefix) {
    if (s.isEmpty()) return prefix;
    StringBuilder out = new StringBuilder(s.length() + prefix.length() * 4);
    out.append(prefix);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      out.append(c);
      if (c == '\n' && i + 1 < s.length()) out.append(prefix);
    }
    return out.toString();
  }

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
