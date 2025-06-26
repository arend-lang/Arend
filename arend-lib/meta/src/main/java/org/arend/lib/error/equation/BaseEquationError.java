package org.arend.lib.error.equation;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreReferenceExpression;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;
import static org.arend.ext.prettyprinting.doc.DocFactory.hang;
import static org.arend.ext.prettyprinting.doc.DocFactory.nullDoc;
import static org.arend.ext.prettyprinting.doc.DocFactory.text;
import static org.arend.ext.prettyprinting.doc.DocFactory.vList;

public abstract class BaseEquationError<NF> extends TypecheckingError {
  private static final String BASE_NAME = "v";
  private final NFPrettyPrinter<NF> prettyPrinter;
  private final List<Pair<String, CoreExpression>> names;
  private final boolean hasExpressions;

  public BaseEquationError(String message, NFPrettyPrinter<NF> prettyPrinter, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super(message, cause);
    names = new ArrayList<>(values.size());
    this.prettyPrinter = prettyPrinter;

    Map<String, Integer> count = new HashMap<>();
    for (CoreExpression value : values) {
      if (value instanceof CoreReferenceExpression refExpr) {
        count.compute(refExpr.getBinding().getName(), (s,c) -> c == null ? 1 : c + 1);
      }
    }

    boolean hasExpressions = false;
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i) instanceof CoreReferenceExpression refExpr && count.get(refExpr.getBinding().getName()) <= 1) {
        names.add(new Pair<>(refExpr.getBinding().getName(), null));
      } else {
        names.add(new Pair<>(BASE_NAME + i, values.get(i)));
        hasExpressions = true;
      }
    }

    this.hasExpressions = hasExpressions;
  }

  protected LineDoc nfToDoc(NF nf) {
    return nf == null ? text("_") : prettyPrinter.nfToDoc(nf, names);
  }

  protected Doc getWhereDoc(List<NF> nfList, PrettyPrinterConfig ppConfig) {
    List<Doc> whereDocs = new ArrayList<>();
    Set<Integer> usedIndices = new HashSet<>();
    for (NF nf : nfList) {
      if (nf != null) prettyPrinter.getUsedIndices(nf, usedIndices);
    }

    for (int i = 0; i < names.size(); i++) {
      Pair<String, CoreExpression> pair = names.get(i);
      if (pair.proj2 != null && usedIndices.contains(i)) {
        whereDocs.add(hang(text(pair.proj1 + " ="), termDoc(pair.proj2, ppConfig)));
      }
    }

    return whereDocs.isEmpty() ? nullDoc() : hang(text("where"), vList(whereDocs));
  }

  @Override
  public boolean hasExpressions() {
    return hasExpressions;
  }
}
