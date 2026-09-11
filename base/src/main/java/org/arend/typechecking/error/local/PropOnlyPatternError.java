package org.arend.typechecking.error.local;

import org.arend.core.definition.DataDefinition;
import org.arend.core.expr.Expression;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.term.concrete.Concrete;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class PropOnlyPatternError extends TypecheckingError {
  public final DataDefinition dataDef;
  public final Expression expectedType;

  public PropOnlyPatternError(DataDefinition dataDef, Expression expectedType, Concrete.SourceNode cause) {
    super("", cause);
    this.dataDef = dataDef;
    this.expectedType = expectedType;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig src) {
    return hList(
      text("Pattern matching on '"),
      refDoc(dataDef.getReferable()),
      text("' with a non-covariant parameter is allowed only if the result type is a proposition"));
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return expectedType == null ? nullDoc() : hang(indent(text("Eliminator type:")), termDoc(expectedType, ppConfig));
  }

  @Override
  public boolean hasExpressions() {
    return true;
  }
}
