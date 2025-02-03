package org.arend.naming.reference;

import org.arend.core.definition.Definition;
import org.arend.ext.reference.Precedence;
import org.arend.module.ModuleLocation;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocatedReferableImpl implements TCDefReferable {
  private Object myData;
  private final AccessModifier myAccessModifier;
  private Precedence myPrecedence;
  private final String myName;
  private final Precedence myAliasPrecedence;
  private final String myAliasName;
  private final LocatedReferable myParent;
  private Kind myKind;
  private Definition myTypechecked;

  public LocatedReferableImpl(@Nullable Object data, @NotNull AccessModifier accessModifier, @NotNull Precedence precedence, @NotNull String name, @NotNull Precedence aliasPrecedence, @Nullable String aliasName, @Nullable LocatedReferable parent, @NotNull Kind kind) {
    myData = data;
    myAccessModifier = accessModifier;
    myPrecedence = precedence;
    myName = name;
    myAliasPrecedence = aliasPrecedence;
    myAliasName = aliasName;
    myParent = parent;
    myKind = kind;
  }

  public boolean isPrecedenceSet() {
    return myPrecedence != null;
  }

  @NotNull
  @Override
  public Precedence getPrecedence() {
    return myPrecedence == null ? Precedence.DEFAULT : myPrecedence;
  }

  public void setPrecedence(Precedence precedence) {
    myPrecedence = precedence;
  }

  @NotNull
  @Override
  public String textRepresentation() {
    return myName;
  }

  @Override
  public @Nullable String getAliasName() {
    return myAliasName;
  }

  @Override
  public @NotNull Precedence getAliasPrecedence() {
    return myAliasPrecedence;
  }

  @Override
  public @NotNull TCDefReferable getTypecheckable() {
    return myKind.isTypecheckable() || myKind == Kind.OTHER || !(myParent instanceof TCDefReferable) ? this : (TCDefReferable) myParent;
  }

  @Override
  public void setTypechecked(Definition definition) {
    myTypechecked = definition;
  }

  @Override
  public Definition getTypechecked() {
    return myTypechecked;
  }

  @Override
  public void setData(Object data) {
    myData = data;
  }

  @NotNull
  @Override
  public Kind getKind() {
    return myKind;
  }

  public void setKind(Kind kind) {
    myKind = kind;
  }

  @Override
  public @NotNull AccessModifier getAccessModifier() {
    return myAccessModifier;
  }

  @Nullable
  @Override
  public ModuleLocation getLocation() {
    return myParent == null ? null : myParent.getLocation();
  }

  @Nullable
  @Override
  public LocatedReferable getLocatedReferableParent() {
    return myParent;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Nullable
  @Override
  public Object getData() {
    return myData;
  }
}
