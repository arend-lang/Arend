package org.arend.lib.error;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class EquationError extends TypecheckingError {
  private static final String BASE_NAME = "v";
  private final List<Integer> nf1;
  private final List<Integer> nf2;
  private final List<CoreExpression> values;

  public EquationError(List<Integer> nf1, List<Integer> nf2, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super("Cannot solve equation", cause);
    this.nf1 = nf1;
    this.nf2 = nf2;
    this.values = values;
  }

  private LineDoc nfToDoc(List<Integer> nf) {
    if (nf.isEmpty()) {
      return text("1");
    }

    List<LineDoc> docs = new ArrayList<>(nf.size());
    for (Integer index : nf) {
      docs.add(text(BASE_NAME + index));
    }
    return hSep(text(" * "), docs);
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    LineDoc equationDoc = hList(text("Equation: "), nfToDoc(nf1), text(" = "), hList(nfToDoc(nf2)));

    Doc whereDoc;
    if (!values.isEmpty()) {
      List<Doc> whereDocs = new ArrayList<>();
      for (int i = 0; i < values.size(); i++) {
        whereDocs.add(hang(text(BASE_NAME + i + " ="), termDoc(values.get(i), ppConfig)));
      }
      whereDoc = hang(text("where"), vList(whereDocs));
    } else {
      whereDoc = nullDoc();
    }

    return vList(equationDoc, whereDoc);
  }

  @Override
  public boolean hasExpressions() {
    return true;
  }
}
