package org.arend.lib.error.equation;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;
import static org.arend.ext.prettyprinting.doc.DocFactory.hList;
import static org.arend.ext.prettyprinting.doc.DocFactory.text;

public class EquationTypeMismatchError<NF> extends BaseEquationError<NF> {
  private final NF expectedLeft;
  private final NF expectedRight;
  private final NF actualLeft;
  private final NF actualRight;

  public EquationTypeMismatchError(NFPrettyPrinter<NF> prettyPrinter, NF expectedLeft, NF expectedRight, NF actualLeft, NF actualRight, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super("Type mismatch", prettyPrinter, values, cause);
    this.expectedLeft = expectedLeft;
    this.expectedRight = expectedRight;
    this.actualLeft = actualLeft;
    this.actualRight = actualRight;
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return vList(
        hList(text("Expected type: "), nfToDoc(expectedLeft), text(" = "), nfToDoc(expectedRight)),
        hList(text("    Hint type: "), nfToDoc(actualLeft), text(" = "), nfToDoc(actualRight)),
        getWhereDoc(Arrays.asList(expectedLeft, expectedRight, actualLeft, actualRight), ppConfig));
  }
}
