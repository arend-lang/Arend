package org.arend.typechecking.error.local;

import org.arend.core.definition.ClassDefinition;
import org.arend.core.expr.Expression;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.jetbrains.annotations.Nullable;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class FieldImplementationMismatchError extends TypecheckingError {
  public final ClassDefinition superClass;
  public final Expression oldImplementation;

  public FieldImplementationMismatchError(ClassDefinition superClass, Expression oldImplementation, @Nullable ConcreteSourceNode cause) {
    super("", cause);
    this.superClass = superClass;
    this.oldImplementation = oldImplementation;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    return hList(text("Field implementation does not match the implementation in "), refDoc(superClass.getRef()));
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    return hang(text("Existing implementation:"), termDoc(oldImplementation, ppConfig));
  }

  @Override
  public boolean hasExpressions() {
    return true;
  }
}
