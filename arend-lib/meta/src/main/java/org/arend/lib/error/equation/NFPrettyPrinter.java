package org.arend.lib.error.equation;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public interface NFPrettyPrinter<NF> {
  @NotNull LineDoc nfToDoc(@NotNull NF nf, @NotNull List<Pair<String, CoreExpression>> names);
  void getUsedIndices(@NotNull NF nf, @NotNull Set<Integer> result);
}
