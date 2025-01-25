package org.arend.naming.reference;

import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.resolving.typing.ReferableInfo;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.typing.TypingInfoVisitor;
import org.arend.naming.scope.Scope;
import org.arend.term.abs.AbstractReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ParameterReferable implements Referable {
  private final TCDefReferable myDefinition;
  private final int myIndex;
  private final Referable myReferable;
  private final GlobalTypingInfo.Builder.MyInfo myTypeInfo;
  private ReferableInfo myReferableInfo;

  public ParameterReferable(TCDefReferable definition, int index, Referable referable, GlobalTypingInfo.Builder.MyInfo typeInfo) {
    myDefinition = definition;
    myIndex = index;
    myReferable = referable;
    myTypeInfo = typeInfo;
  }

  public void resolve(Scope scope, TypingInfo typingInfo) {
    if (myReferableInfo != null || myTypeInfo == null) return;
    myReferableInfo = GlobalTypingInfo.Builder.makeReferableInfo(typingInfo, GlobalTypingInfo.Builder.makeMyInfo(myTypeInfo.parameters(), TypingInfoVisitor.tryResolve(myTypeInfo.referable(), scope), myTypeInfo.arguments()));
  }

  public ReferableInfo getReferableInfo() {
    return myReferableInfo;
  }

  @Override
  public @NotNull String textRepresentation() {
    return myReferable.textRepresentation();
  }

  public TCDefReferable getDefinition() {
    return myDefinition;
  }

  public int getIndex() {
    return myIndex;
  }

  @Override
  public @NotNull Referable getUnderlyingReferable() {
    return myReferable;
  }

  @Override
  public @Nullable AbstractReferable getAbstractReferable() {
    return myReferable.getAbstractReferable();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ParameterReferable that = (ParameterReferable) o;
    return myIndex == that.myIndex && myDefinition.equals(that.myDefinition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDefinition, myIndex);
  }
}
