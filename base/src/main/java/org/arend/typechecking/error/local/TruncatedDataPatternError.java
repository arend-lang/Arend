package org.arend.typechecking.error.local;

import org.arend.core.definition.DataDefinition;
import org.arend.ext.core.level.ConstLevel;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.term.concrete.Concrete;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class TruncatedDataPatternError extends TypecheckingError {
  public final DataDefinition dataDef;
  public final ConstLevel truncatedLevel;
  public final ConstLevel dataLevel;

  public TruncatedDataPatternError(DataDefinition dataDef, ConstLevel truncatedLevel, ConstLevel dataLevel, Concrete.SourceNode cause) {
    super("", cause);
    this.dataDef = dataDef;
    this.truncatedLevel = truncatedLevel;
    this.dataLevel = dataLevel;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig src) {
    return hList(
      text("Data type '"),
      refDoc(dataDef.getReferable()),
      text("' is truncated to h-level " + truncatedLevel + ", but the h-level of the data type being defined is " + dataLevel));
  }
}
