package org.arend.core.expr;

import org.arend.core.definition.Constructor;
import org.arend.core.definition.DataDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface BaseDataCallExpression extends BaseDefCallExpression {
  @Override @NotNull DataDefinition getDefinition();
  @Nullable List<ConCallExpression> getMatchedConstructors();
  boolean getMatchedConCall(Constructor constructor, List<ConCallExpression> conCalls);
}
