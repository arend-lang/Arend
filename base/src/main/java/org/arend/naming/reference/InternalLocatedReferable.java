package org.arend.naming.reference;

import org.arend.ext.reference.Precedence;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.Group;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InternalLocatedReferable extends LocatedReferableImpl implements Group.InternalReferable {
  private final boolean myVisible;

  public InternalLocatedReferable(Object data, AccessModifier accessModifier, Precedence precedence, @NotNull String name, Precedence aliasPrecedence, @Nullable String aliasName, boolean isVisible, TCDefReferable parent, Kind kind) {
    super(data, accessModifier, precedence, name, aliasPrecedence, aliasName, parent, kind);
    myVisible = isVisible;
  }

  @Override
  public LocatedReferable getReferable() {
    return this;
  }

  @Override
  public boolean isVisible() {
    return myVisible;
  }
}
