package org.arend.lib.error.equation;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static org.arend.ext.prettyprinting.doc.DocFactory.text;

public class BooleanRingNFPrettyPrinter implements NFPrettyPrinter<List<List<Boolean>>> {
  @Override
  public @NotNull LineDoc nfToDoc(@NotNull List<List<Boolean>> poly, @NotNull List<Pair<String, CoreExpression>> names) {
    if (poly.isEmpty()) {
      return text("0");
    }

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < poly.size(); i++) {
      if (i > 0) {
        builder.append(" + ");
      }

      List<Boolean> monomial = poly.get(i);
      if (monomial.isEmpty()) {
        builder.append("1");
      } else {
        boolean isFirst = true;
        for (int j = 0; j < monomial.size(); j++) {
          if (monomial.get(j)) {
            if (isFirst) {
              isFirst = false;
            } else {
              builder.append(" * ");
            }
            builder.append(names.get(j).proj1);
          }
        }
      }
    }

    return text(builder.toString());
  }

  @Override
  public void getUsedIndices(@NotNull List<List<Boolean>> poly, @NotNull Set<Integer> result) {
    for (List<Boolean> monomial : poly) {
      for (int i = 0; i < monomial.size(); i++) {
        if (monomial.get(i)) {
          result.add(i);
        }
      }
    }
  }
}
