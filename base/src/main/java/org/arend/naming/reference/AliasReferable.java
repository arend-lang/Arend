package org.arend.naming.reference;

import org.arend.ext.reference.DataContainer;
import org.arend.ext.reference.Precedence;
import org.arend.term.abs.AbstractReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AliasReferable implements RedirectingReferable {
  private final GlobalReferable myReferable;

  public AliasReferable(GlobalReferable referable) {
    myReferable = referable;
  }

  @Override
  public @Nullable AbstractReferable getAbstractReferable() {
    if (this instanceof DataContainer) {
      Object data = ((DataContainer) this).getData();
      if (data instanceof AbstractReferable) {
        return (AbstractReferable) data;
      }
    }
    return myReferable.getAbstractReferable();
  }

  @Override
  public @NotNull GlobalReferable getOriginalReferable() {
    return myReferable;
  }

  @Override
  public @NotNull Precedence getPrecedence() {
    return myReferable.getAliasPrecedence();
  }

  @Override
  public @NotNull String textRepresentation() {
    String name = myReferable.getAliasName();
    return name == null ? myReferable.textRepresentation() : name;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof RedirectingReferable && ((RedirectingReferable) obj).getOriginalReferable().equals(getOriginalReferable());
  }
}
