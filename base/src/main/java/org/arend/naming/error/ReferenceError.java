package org.arend.naming.error;

import org.arend.ext.error.LocalError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.UnresolvedReference;
import org.jetbrains.annotations.NotNull;

public class ReferenceError extends LocalError {
  public final Referable referable;

  public ReferenceError(String message, Referable referable) {
    this(Level.ERROR, message, referable);
  }

  public ReferenceError(Level level, String message, Referable referable) {
    super(level, message);
    this.referable = referable;
    definition = referable;
  }

  @Override
  public Object getCause() {
    if (referable instanceof UnresolvedReference) {
      Object data = ((UnresolvedReference) referable).getData();
      if (data != null) {
        return data;
      }
    }
    return referable;
  }

  @Override
  public Doc getCauseDoc(PrettyPrinterConfig src) {
    return DocFactory.refDoc(referable);
  }

  @NotNull
  @Override
  public Stage getStage() {
    return Stage.RESOLVER;
  }
}
