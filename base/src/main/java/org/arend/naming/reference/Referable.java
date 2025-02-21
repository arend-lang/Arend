package org.arend.naming.reference;

import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.DataContainer;
import org.arend.term.abs.AbstractReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Referable extends ArendRef {
  enum RefKind { EXPR, PLEVEL, HLEVEL }

  @NotNull String textRepresentation();

  @NotNull
  @Override
  default String getRefName() {
    return textRepresentation();
  }

  @NotNull
  default Referable.RefKind getRefKind() {
    return RefKind.EXPR;
  }

  default @Nullable AbstractReferable getAbstractReferable() {
    if (this instanceof DataContainer) {
      Object data = ((DataContainer) this).getData();
      if (data instanceof AbstractReferable) {
        return (AbstractReferable) data;
      }
    }
    return null;
  }

  @Override
  default boolean isLocalRef() {
    return true;
  }
}
