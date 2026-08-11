package org.arend.core.expr;

import org.arend.core.definition.CallableDefinition;
import org.arend.core.subst.Levels;
import org.arend.ext.core.level.LevelSubstitution;
import org.jetbrains.annotations.NotNull;

public abstract class LeveledDefCallExpression extends DefCallExpression {
  private Levels myLevels;

  public LeveledDefCallExpression(CallableDefinition definition, Levels levels) {
    super(definition);
    myLevels = levels;
  }

  @NotNull
  public Levels getLevels() {
    return myLevels;
  }

  public void setLevels(Levels levels) {
    myLevels = levels;
  }

  @Override
  public LevelSubstitution getLevelSubstitution() {
    return myLevels.makeSubstitution(getDefinition());
  }

  public void substSort(LevelSubstitution substitution) {
    myLevels = myLevels.subst(substitution);
  }
}
