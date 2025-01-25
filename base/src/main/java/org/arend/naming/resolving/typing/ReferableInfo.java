package org.arend.naming.resolving.typing;

import org.arend.naming.reference.ClassReferable;

public class ReferableInfo {
  private final int myParameters;
  private final ClassReferable myClassRef;

  public ReferableInfo(int parameters, ClassReferable classRef) {
    myParameters = parameters;
    myClassRef = classRef;
  }

  public int getParameters() {
    return myParameters;
  }

  public ClassReferable getClassReferable() {
    return myClassRef;
  }
}
