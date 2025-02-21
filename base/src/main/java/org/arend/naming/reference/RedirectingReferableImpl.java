package org.arend.naming.reference;

import org.arend.ext.reference.DataContainer;
import org.arend.ext.reference.Precedence;
import org.arend.term.abs.AbstractReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedirectingReferableImpl implements RedirectingReferable {
  private final Referable myOriginalReferable;
  private final Precedence myPrecedence;
  private final String myName;

  public RedirectingReferableImpl(Referable originalReferable, Precedence precedence, String name) {
    myOriginalReferable = originalReferable;
    myPrecedence = precedence;
    myName = name;
  }

  @Override
  public @Nullable AbstractReferable getAbstractReferable() {
    if (this instanceof DataContainer) {
      Object data = ((DataContainer) this).getData();
      if (data instanceof AbstractReferable) {
        return (AbstractReferable) data;
      }
    }
    return myOriginalReferable.getAbstractReferable();
  }

  @NotNull
  @Override
  public Referable getOriginalReferable() {
    return myOriginalReferable;
  }

  @NotNull
  @Override
  public Precedence getPrecedence() {
    return myPrecedence != null ? myPrecedence : myOriginalReferable instanceof GlobalReferable ? ((GlobalReferable) myOriginalReferable).getPrecedence() : Precedence.DEFAULT;
  }

  @NotNull
  @Override
  public String textRepresentation() {
    return myName;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof RedirectingReferable && ((RedirectingReferable) obj).getOriginalReferable().equals(getOriginalReferable());
  }
}
