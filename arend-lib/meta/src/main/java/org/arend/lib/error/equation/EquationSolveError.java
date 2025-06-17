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
  private final NF nf1;
  private final NF nf2;

  public EquationSolveError(NFPrettyPrinter<NF> prettyPrinter, NF nf1, NF nf2, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super("Cannot solve equation", prettyPrinter, values, cause);
    this.nf1 = nf1;
    this.nf2 = nf2;
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return vList(hList(text("Equation: "), nfToDoc(nf1), text(" = "), hList(nfToDoc(nf2))), getWhereDoc(Arrays.asList(nf1, nf2), ppConfig));
  }
}
