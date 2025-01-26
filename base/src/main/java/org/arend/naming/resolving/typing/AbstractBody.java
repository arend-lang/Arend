package org.arend.naming.resolving.typing;

import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;

public class AbstractBody {
  private final int myParameters;
  private final Referable myReferable;
  private final int myArguments;

  public AbstractBody(int parameters, Referable referable, int arguments) {
    myParameters = parameters;
    myReferable = referable;
    myArguments = referable instanceof GlobalReferable global && global.getKind() == GlobalReferable.Kind.CLASS ? 0 : arguments;
  }

  public int getParameters() {
    return myParameters;
  }

  public Referable getReferable() {
    return myReferable;
  }

  public int getArguments() {
    return myArguments;
  }
}
