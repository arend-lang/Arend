package org.arend.typechecking;

import org.arend.core.context.binding.LevelVariable;
import org.arend.naming.reference.LevelReferable;

import java.util.Map;

public class LevelContext {
  private final Map<LevelReferable, LevelVariable> myVariables;

  public LevelContext(Map<LevelReferable, LevelVariable> variables) {
    myVariables = variables;
  }

  public Map<LevelReferable, LevelVariable> getVariables() {
    return myVariables;
  }

  public LevelVariable getVariable(LevelReferable ref) {
    return myVariables.get(ref);
  }
}
