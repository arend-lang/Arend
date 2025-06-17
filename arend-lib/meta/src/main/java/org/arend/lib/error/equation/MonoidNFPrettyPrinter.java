package org.arend.lib.error.equation;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.arend.ext.prettyprinting.doc.DocFactory.hSep;
import static org.arend.ext.prettyprinting.doc.DocFactory.text;

public class MonoidNFPrettyPrinter implements NFPrettyPrinter<List<Integer>> {
  private final boolean isMultiplicative;

  public MonoidNFPrettyPrinter(boolean isMultiplicative) {
    this.isMultiplicative = isMultiplicative;
  }

  @Override
  public @NotNull LineDoc nfToDoc(@NotNull List<Integer> nf, @NotNull List<Pair<String, CoreExpression>> names) {
    if (nf.isEmpty()) {
      return text(isMultiplicative ? "1" : "0");
    }

    List<LineDoc> docs = new ArrayList<>(nf.size());
    for (Integer index : nf) {
      docs.add(text(names.get(index).proj1));
    }
    return hSep(text(isMultiplicative ? " * " : " + "), docs);
  }

  @Override
  public void getUsedIndices(@NotNull List<Integer> nf, @NotNull Set<Integer> result) {
    result.addAll(nf);
  }
}
