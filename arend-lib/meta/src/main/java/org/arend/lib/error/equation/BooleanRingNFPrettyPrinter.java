package org.arend.lib.error.equation;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.arend.lib.ring.BooleanMonomial;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static org.arend.ext.prettyprinting.doc.DocFactory.text;

public class BooleanRingNFPrettyPrinter implements NFPrettyPrinter<List<BooleanMonomial>> {
  @Override
  public @NotNull LineDoc nfToDoc(@NotNull List<BooleanMonomial> poly, @NotNull List<Pair<String, CoreExpression>> names) {
    if (poly.isEmpty()) {
      return text("0");
    }

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < poly.size(); i++) {
      if (i > 0) {
        builder.append(" + ");
      }

      BooleanMonomial monomial = poly.get(i);
      boolean isFirst = true;
      for (int j = 0; j < monomial.size(); j++) {
        if (monomial.getElement(j)) {
          if (isFirst) {
            isFirst = false;
          } else {
            builder.append(" * ");
          }
          builder.append(names.get(j).proj1);
        }
      }
      if (isFirst) {
        builder.append("1");
      }
    }

    return text(builder.toString());
  }

  @Override
  public void getUsedIndices(@NotNull List<BooleanMonomial> poly, @NotNull Set<Integer> result) {
    for (BooleanMonomial monomial : poly) {
      monomial.getIndices(result);
    }
  }
}
