package org.arend.naming.reference;

import org.arend.ext.reference.Precedence;
import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TCLevelReferable implements LocatedReferable, LevelReferable {
  private final Object myData;
  private final String myName;
  private final LevelDefinition myParent;

  public TCLevelReferable(Object data, String name, LevelDefinition parent) {
    myData = data;
    myName = name;
    myParent = parent;
  }

  @Override
  public @Nullable Object getData() {
    return myData;
  }

  @Override
  public @NotNull RefKind getRefKind() {
    return myParent.isPLevels() ? RefKind.PLEVEL : RefKind.HLEVEL;
  }

  public LevelDefinition getDefParent() {
    return myParent;
  }

  @Override
  public @NotNull Precedence getPrecedence() {
    return Precedence.DEFAULT;
  }

  @Override
  public @NotNull Kind getKind() {
    return Kind.LEVEL;
  }

  @Override
  public @Nullable ModuleLocation getLocation() {
    LocatedReferable parent = myParent.getParent();
    return parent == null ? null : parent.getLocation();
  }

  @Override
  public @Nullable LocatedReferable getLocatedReferableParent() {
    return myParent.getParent();
  }

  @Override
  public @NotNull String textRepresentation() {
    return myName;
  }
}
