package org.arend.lib.error;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class MonoidEquationError extends EquationError {
  private final boolean isMultiplicative;
  private final List<Integer> nf1;
  private final List<Integer> nf2;

  public MonoidEquationError(boolean isMultiplicative, List<Integer> nf1, List<Integer> nf2, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super(values, cause);
    this.isMultiplicative = isMultiplicative;
    this.nf1 = nf1;
    this.nf2 = nf2;
  }

  private LineDoc nfToDoc(List<Integer> nf) {
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
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return vList(hList(text("Equation: "), nfToDoc(nf1), text(" = "), hList(nfToDoc(nf2))), getWhereDoc(ppConfig));
  }

  @Override
  protected Set<Integer> getUsedIndices() {
    Set<Integer> result = new HashSet<>();
    result.addAll(nf1);
    result.addAll(nf2);
    return result;
  }
}
