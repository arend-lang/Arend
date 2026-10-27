package org.arend.typechecking.error.local;

import org.arend.core.definition.DataDefinition;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.term.concrete.Concrete;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class CovariantDependentPatternError extends TypecheckingError {
  public final DataDefinition dataDef;

  public CovariantDependentPatternError(DataDefinition dataDef, Concrete.SourceNode cause) {
    super("", cause);
    this.dataDef = dataDef;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig src) {
    return hList(
      text("Covariant pattern matching on '"),
      refDoc(dataDef.getReferable()),
      text("' is not allowed since other parameters depend on the matched variable"));
  }
}
