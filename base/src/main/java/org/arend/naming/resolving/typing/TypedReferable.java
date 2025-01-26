package org.arend.naming.resolving.typing;

import org.arend.naming.reference.Referable;

public class TypedReferable {
  private final Referable myReferable;
  private final AbstractBody myAbstractBody;

  public TypedReferable(Referable referable, AbstractBody abstractBody) {
    myReferable = referable;
    myAbstractBody = abstractBody;
  }

  public Referable getReferable() {
    return myReferable;
  }

  public AbstractBody getAbstractBody() {
    return myAbstractBody;
  }
}
