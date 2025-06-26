package org.arend.lib.error.equation;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class EquationFindError<NF> extends BaseEquationError<NF> {
  private final NF expectedLeft;
  private final NF expectedRight;
  private final NF subNF;

  public EquationFindError(NFPrettyPrinter<NF> prettyPrinter, NF expectedLeft, NF expectedRight, NF subNF, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super("Cannot find subexpression", prettyPrinter, values, cause);
    this.expectedLeft = expectedLeft;
    this.expectedRight = expectedRight;
    this.subNF = subNF;
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return vList(
        hList(text("Expected type: "), nfToDoc(expectedLeft), text(" = "), nfToDoc(expectedRight)),
        hList(text("Subexpression: "), nfToDoc(subNF)),
        getWhereDoc(Arrays.asList(expectedLeft, expectedRight, subNF), ppConfig));
  }
}
