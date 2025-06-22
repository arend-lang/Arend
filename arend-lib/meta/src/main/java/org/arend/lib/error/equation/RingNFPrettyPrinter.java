package org.arend.lib.error.equation;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.arend.lib.ring.Monomial;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class RingNFPrettyPrinter implements NFPrettyPrinter<List<Monomial>> {
  @Override
  public @NotNull LineDoc nfToDoc(@NotNull List<Monomial> poly, @NotNull List<Pair<String, CoreExpression>> names) {
    if (poly.isEmpty()) {
      return text("0");
    }

    List<LineDoc> docs = new ArrayList<>(poly.size());
    for (Monomial monomial : poly) {
      docs.add(monomial.elements().isEmpty() ? text(monomial.coefficient().toString()) : hList(monomial.coefficient().equals(BigInteger.ONE) ? empty() : text(monomial.coefficient() + " * "), hSep(text(" * "), monomial.elements().stream().map(var -> text(names.get(var).proj1)).toList())));
    }
    return hSep(text(" + "), docs);
  }

  @Override
  public void getUsedIndices(@NotNull List<Monomial> poly, @NotNull Set<Integer> result) {
    for (Monomial monomial : poly) {
      result.addAll(monomial.elements());
    }
  }
}
