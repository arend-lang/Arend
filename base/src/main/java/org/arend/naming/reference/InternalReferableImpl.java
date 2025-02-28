package org.arend.naming.reference;

import org.arend.ext.reference.Precedence;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class InternalReferableImpl extends LocatedReferableImpl implements InternalReferable {
  private final boolean myVisible;

  public InternalReferableImpl(Object data, AccessModifier accessModifier, Precedence precedence, @NotNull String name, Precedence aliasPrecedence, @Nullable String aliasName, boolean isVisible, @NotNull TCDefReferable parent, Kind kind) {
    super(data, accessModifier, precedence, name, aliasPrecedence, aliasName, parent, kind);
    myVisible = isVisible;
  }

  @Override
  public @NotNull TCDefReferable getLocatedReferableParent() {
    return (TCDefReferable) Objects.requireNonNull(super.getLocatedReferableParent());
  }

  @Override
  public @NotNull TCDefReferable getTypecheckable() {
    return getLocatedReferableParent();
  }

  @Override
  public boolean isVisible() {
    return myVisible;
  }

  @Override
  public boolean isSimilar(@NotNull TCDefReferable referable) {
    return super.isSimilar(referable) && myVisible == ((InternalReferableImpl) referable).myVisible;
  }
}
