package org.arend.lib.error.equation;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;
import static org.arend.ext.prettyprinting.doc.DocFactory.text;
import static org.arend.ext.prettyprinting.doc.DocFactory.vList;

public class EquationSolveError<NF> extends BaseEquationError<NF> {
  private final NF expectedLeft;
  private final NF expectedRight;
  private final NF reducedLeft;
  private final NF reducedRight;

  public EquationSolveError(NFPrettyPrinter<NF> prettyPrinter, NF expectedLeft, NF expectedRight, NF reducedLeft, NF reducedRight, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super("Cannot solve equation", prettyPrinter, values, cause);
    this.expectedLeft = expectedLeft;
    this.expectedRight = expectedRight;
    this.reducedLeft = reducedLeft;
    this.reducedRight = reducedRight;
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return vList(
        hList(text("Equation: "), nfToDoc(expectedLeft), text(" = "), nfToDoc(expectedRight)),
        reducedLeft == null && reducedRight == null ? nullDoc() : hList(text("Reduced to: "), nfToDoc(reducedLeft == null ? expectedLeft : reducedLeft), text(" = "), nfToDoc(reducedRight == null ? expectedRight : reducedRight)),
        getWhereDoc(Arrays.asList(expectedLeft, expectedRight, reducedLeft, reducedRight), ppConfig));
  }
}
