package org.arend.naming.reference;

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
  private final int myTypeParameters;
  private final Referable myTypeReference;
  private ReferableInfo myReferableInfo;

  public ParameterReferable(TCDefReferable definition, int index, Referable referable, int typeParameters, Referable typeReference) {
    myDefinition = definition;
    myIndex = index;
    myReferable = referable;
    myTypeParameters = typeParameters;
    myTypeReference = typeReference;
  }

  public void resolve(Scope scope, TypingInfo typingInfo) {
    if (myReferableInfo != null) return;
    Referable referable = TypingInfoVisitor.tryResolve(myTypeReference, scope);
    if (referable instanceof ClassReferable classRef) {
      myReferableInfo = new ReferableInfo(myTypeParameters, classRef);
    } else {
      ReferableInfo bodyInfo = typingInfo.getBodyInfo(referable);
      if (bodyInfo != null) {
        myReferableInfo = new ReferableInfo(myTypeParameters + bodyInfo.getParameters(), bodyInfo.getClassReferable());
      }
    }
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
