package org.arend.typechecking.error.local;

import org.arend.core.definition.DataDefinition;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class SquashedDataError extends TypecheckingError {
  public final DataDefinition dataDef;

  public SquashedDataError(DataDefinition dataDef, @NotNull Concrete.SourceNode cause) {
    super("", cause);
    this.dataDef = dataDef;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    return hList(text("Pattern matching on " + (dataDef.isTruncated() ? "truncated" : "squashed") + " data type '"), refDoc(dataDef.getReferable()), text("' is allowed only in \\sfunc and \\scase"));
  }
}
