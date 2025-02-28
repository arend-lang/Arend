package org.arend.naming.reference;

import org.jetbrains.annotations.NotNull;

public interface InternalReferable extends TCDefReferable {
  @Override @NotNull TCDefReferable getLocatedReferableParent();
  boolean isVisible();
}
