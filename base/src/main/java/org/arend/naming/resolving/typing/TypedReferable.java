package org.arend.naming.resolving.typing;

import org.arend.naming.reference.ClassReferable;
import org.arend.naming.reference.Referable;

public class TypedReferable extends ReferableInfo {
  private final Referable myReferable;

  public TypedReferable(Referable referable, int parameters, ClassReferable classRef) {
    super(parameters, classRef);
    myReferable = referable;
  }

  public Referable getReferable() {
    return myReferable;
  }
}
