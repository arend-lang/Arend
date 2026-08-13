package org.arend.core.context.param;

import org.arend.core.expr.DataCallExpression;
import org.arend.core.subst.Levels;
import org.arend.prelude.Prelude;

import java.util.Collections;

public final class UnusedDirectedIntervalDependentLink extends TypedSingleDependentLink {
  public static final UnusedDirectedIntervalDependentLink INSTANCE = new UnusedDirectedIntervalDependentLink();

  private UnusedDirectedIntervalDependentLink() {
    super(true, null, DataCallExpression.make(Prelude.DI, Levels.EMPTY, Collections.emptyList()));
  }

  @Override
  public boolean isUnused() {
    return true;
  }
}
