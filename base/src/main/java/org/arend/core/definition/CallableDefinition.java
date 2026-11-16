package org.arend.core.definition;

import org.arend.core.context.param.DependentLink;
import org.arend.core.expr.Expression;
import org.arend.core.subst.Levels;
import org.arend.naming.reference.TCDefReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public abstract class CallableDefinition extends Definition {
  private boolean myHasPropertyParameters;

  public CallableDefinition(TCDefReferable referable, TypeCheckingStatus status) {
    super(referable, status);
  }

  public abstract Expression getDefCall(Levels levels, List<Expression> args);

  public boolean hasPropertyParameters() {
    return myHasPropertyParameters;
  }

  protected void updateHasPropertyParameters() {
    boolean hasProperties = false;
    for (DependentLink param = getParameters(); param.hasNext(); param = param.getNext()) {
      param = param.getNextTyped(null);
      if (param.isProperty()) {
        hasProperties = true;
        break;
      }
    }
    myHasPropertyParameters = hasProperties;
  }

  @Override
  public @Nullable Set<TCDefReferable> getUsedInstances() {
    return getTopLevelDefinition().getUsedInstances();
  }

  @Override
  public void setUsedInstances(@NotNull Set<TCDefReferable> usedInstances) {}
}
