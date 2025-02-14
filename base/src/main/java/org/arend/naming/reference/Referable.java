package org.arend.naming.reference;

import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.DataContainer;
import org.arend.term.abs.AbstractReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO[server2]: Simplify the whole Referable hierarchy; PSI elements shouldn't implement this interface.
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

  // TODO[server2]: Remove this
  @NotNull
  default Referable getUnderlyingReferable() {
    return this;
  }

  static Referable getUnderlyingReferable(Referable ref) {
    return ref == null ? null : ref.getUnderlyingReferable();
  }

  default @Nullable AbstractReferable getAbstractReferable() {
    if (this instanceof DataContainer) {
      Object data = ((DataContainer) this).getData();
      if (data instanceof AbstractReferable) {
        return (AbstractReferable) data;
      }
    }
    return getUnderlyingReferable() instanceof AbstractReferable result ? result : null;
  }

  @Override
  default boolean isLocalRef() {
    return true;
  }
}
