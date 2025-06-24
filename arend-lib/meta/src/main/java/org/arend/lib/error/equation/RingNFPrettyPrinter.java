package org.arend.lib.error.equation;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.arend.lib.ring.Monomial;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class RingNFPrettyPrinter implements NFPrettyPrinter<List<Monomial>> {
  private void printMonomial(Monomial monomial, List<Pair<String, CoreExpression>> names, StringBuilder builder) {
    List<Integer> elements = monomial.elements();
    if (elements.isEmpty()) {
      builder.append(monomial.coefficient().toString());
    } else {
      if (monomial.coefficient().equals(BigInteger.ONE)) {
        builder.append(names.get(elements.getFirst()).proj1);
      } else if (monomial.coefficient().equals(BigInteger.ONE.negate())) {
        builder.append("-").append(names.get(elements.getFirst()).proj1);
      } else {
        builder.append(monomial.coefficient()).append(" * ").append(names.get(elements.getFirst()).proj1);
      }
      for (int i = 1; i < elements.size(); i++) {
        builder.append(" * ").append(names.get(elements.get(i)).proj1);
      }
    }
  }

  @Override
  public @NotNull LineDoc nfToDoc(@NotNull List<Monomial> poly, @NotNull List<Pair<String, CoreExpression>> names) {
    if (poly.isEmpty()) {
      return text("0");
    }

    StringBuilder builder = new StringBuilder();
    printMonomial(poly.getFirst(), names, builder);
    for (int i = 1; i < poly.size(); i++) {
      Monomial monomial = poly.get(i);
      if (monomial.coefficient().signum() < 0) {
        builder.append(" - ");
        printMonomial(new Monomial(monomial.coefficient().negate(), monomial.elements()), names, builder);
      } else {
        builder.append(" + ");
        printMonomial(monomial, names, builder);
      }
    }

    return text(builder.toString());
  }

  @Override
  public void getUsedIndices(@NotNull List<Monomial> poly, @NotNull Set<Integer> result) {
    for (Monomial monomial : poly) {
      result.addAll(monomial.elements());
    }
  }
}
