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
  private final NF subNF;
  private final NF nf;

  public EquationFindError(NFPrettyPrinter<NF> prettyPrinter, NF subNF, NF nf, List<CoreExpression> values, @Nullable ConcreteSourceNode cause) {
    super("Cannot find subexpression", prettyPrinter, values, cause);
    this.subNF = subNF;
    this.nf = nf;
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return vList(
        hList(text("   Expression:"), nfToDoc(nf)),
        hList(text("Subexpression:"), nfToDoc(subNF)),
        getWhereDoc(Arrays.asList(subNF, nf), ppConfig));
  }
}
