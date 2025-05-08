package org.arend.naming.reference;

import org.arend.naming.resolving.typing.AbstractBody;
import org.arend.term.abs.AbstractReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ParameterReferable implements Referable {
  private final TCDefReferable myDefinition;
  private final int myIndex;
  private final Referable myReferable;
  private AbstractBody myAbstractBody;

  public ParameterReferable(TCDefReferable definition, int index, Referable referable, AbstractBody body) {
    myDefinition = definition;
    myIndex = index;
    myReferable = referable;
    myAbstractBody = body;
  }

  public AbstractBody getAbstractBody() {
    return myAbstractBody;
  }

  public void setAbstractBody(AbstractBody body) {
    myAbstractBody = body;
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

  public @NotNull Referable getOriginalReferable() {
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
