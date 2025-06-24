package org.arend.lib.error.equation;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static org.arend.ext.prettyprinting.doc.DocFactory.text;

public class GroupNFPrettyPrinter implements NFPrettyPrinter<List<Pair<Boolean,Integer>>> {
  private final boolean isMultiplicative;

  public GroupNFPrettyPrinter(boolean isMultiplicative) {
    this.isMultiplicative = isMultiplicative;
  }

  private void printPair(boolean isPos, int var, List<Pair<String, CoreExpression>> names, StringBuilder builder) {
    String name = names.get(var).proj1;
    if (isPos) {
      builder.append(name);
    } else {
      builder.append(isMultiplicative ? "" : "-").append(name).append(isMultiplicative ? "^-1" : "");
    }
  }

  @Override
  public @NotNull LineDoc nfToDoc(@NotNull List<Pair<Boolean,Integer>> nf, @NotNull List<Pair<String, CoreExpression>> names) {
    if (nf.isEmpty()) {
      return text(isMultiplicative ? "1" : "0");
    }

    StringBuilder builder = new StringBuilder();
    printPair(nf.getFirst().proj1, nf.getFirst().proj2, names, builder);

    for (int i = 1; i < nf.size(); i++) {
      var pair = nf.get(i);
      if (isMultiplicative) {
        builder.append(" * ");
        printPair(pair.proj1, pair.proj2, names, builder);
      } else {
        builder.append(pair.proj1 ? " + " : " - ");
        printPair(true, pair.proj2, names, builder);
      }
    }

    return text(builder.toString());
  }

  @Override
  public void getUsedIndices(@NotNull List<Pair<Boolean,Integer>> nf, @NotNull Set<Integer> result) {
    for (var pair : nf) {
      result.add(pair.proj2);
    }
  }
}
