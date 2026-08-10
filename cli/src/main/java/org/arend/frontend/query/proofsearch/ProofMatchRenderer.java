package org.arend.frontend.query.proofsearch;

import org.arend.ext.reference.Precedence;
import org.arend.ext.util.Pair;
import org.arend.frontend.query.MatchHighlighter;
import org.arend.proof.ArendExpressionMatcher;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders a proof-search hit's matched type slice ({@code (param) -> ... -> codomain}) for
 * {@code -ps}: {@link #plainSlice} for the JSON {@code expression} field, and
 * {@link #highlightedSlice} for the text listing, which wraps the matched sub-terms in ANSI
 * green via {@link HighlightingPrettyPrintVisitor}.
 */
final class ProofMatchRenderer {
  private ProofMatchRenderer() {}

  /**
   * The matched type slice for a JSON {@code expression} field:
   * {@code (param) -> ... -> codomain}, matching the plain-text
   * (non-{@code print-full}) listing but without ANSI highlighting.
   */
  static String plainSlice(ArendExpressionMatcher.ProofSearchMatchingResult result,
                           Concrete.Expression codomain) {
    Precedence topPrecedence = new Precedence(Concrete.Expression.PREC);
    StringBuilder sb = new StringBuilder();
    if (result.inPattern() != null) {
      for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
        StringBuilder b = new StringBuilder();
        parameterData.proj1.prettyPrint(new PrettyPrintVisitor(b, 0), topPrecedence);
        sb.append('(').append(b).append(") -> ");
      }
    }
    StringBuilder cb = new StringBuilder();
    codomain.prettyPrint(new PrettyPrintVisitor(cb, 0), topPrecedence);
    sb.append(cb);
    return sb.toString();
  }

  /** The matched type slice with every matched sub-term wrapped in ANSI green (the text listing). */
  static String highlightedSlice(ArendExpressionMatcher.ProofSearchMatchingResult result,
                                 Concrete.Expression codomain) {
    Set<Concrete.SourceNode> highlightedNodes = new HashSet<>(result.inCodomain());
    if (result.inPattern() != null) {
      for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
        highlightedNodes.addAll(parameterData.proj2);
      }
    }

    Precedence topPrecedence = new Precedence(Concrete.Expression.PREC);
    StringBuilder builder = new StringBuilder();
    if (result.inPattern() != null) {
      for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
        StringBuilder paramBuilder = new StringBuilder();
        // Route through printExpr (not Expression.prettyPrint, which dispatches
        // straight to accept()) so the whole parameter node is checked against the
        // highlight set -- otherwise a match at the top of the parameter is missed.
        new HighlightingPrettyPrintVisitor(paramBuilder, 0, highlightedNodes).printExpr(parameterData.proj1, topPrecedence);
        builder.append('(').append(paramBuilder).append(") -> ");
      }
    }
    new HighlightingPrettyPrintVisitor(builder, 0, highlightedNodes).printExpr(codomain, topPrecedence);
    return builder.toString();
  }

  /** Pretty-printer that wraps matched sub-terms in ANSI green. */
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
    public void printExpr(Concrete.Expression expr, Precedence precedence) {
      if (highlightedNodes.contains(expr)) {
        myBuilder.append(MatchHighlighter.green());
        highlightCount++;
      }
      super.printExpr(expr, precedence);
      if (highlightedNodes.contains(expr)) {
        highlightCount--;
        if (highlightCount == 0) {
          myBuilder.append(MatchHighlighter.reset());
        }
      }
    }
  }
}
