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

public class GroupNFPrettyPrinter implements NFPrettyPrinter<List<Pair<Boolean,Integer>>> {
  private final boolean isMultiplicative;

  public GroupNFPrettyPrinter(boolean isMultiplicative) {
    this.isMultiplicative = isMultiplicative;
  }

  @Override
  public @NotNull LineDoc nfToDoc(@NotNull List<Pair<Boolean,Integer>> nf, @NotNull List<Pair<String, CoreExpression>> names) {
    if (nf.isEmpty()) {
      return text(isMultiplicative ? "1" : "0");
    }

    List<LineDoc> docs = new ArrayList<>(nf.size());
    for (var pair : nf) {
      String name = names.get(pair.proj2).proj1;
      docs.add(text(pair.proj1 ? name : (isMultiplicative ? "" : "-") + name + (isMultiplicative ? "^-1" : "")));
    }
    return hSep(text(isMultiplicative ? " * " : " + "), docs);
  }

  @Override
  public void getUsedIndices(@NotNull List<Pair<Boolean,Integer>> nf, @NotNull Set<Integer> result) {
    for (var pair : nf) {
      result.add(pair.proj2);
    }
  }
}
