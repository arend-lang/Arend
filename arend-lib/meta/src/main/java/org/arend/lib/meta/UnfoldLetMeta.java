package org.arend.lib.meta;

import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class UnfoldLetMeta extends BaseMetaDefinition {
  @Override
  public boolean[] argumentExplicitness() {
    return new boolean[] { true };
  }

  @Override
  public boolean allowExcessiveArguments() {
    return false;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    if (contextData.getExpectedType() != null) {
      return typechecker.typecheck(contextData.getArguments().getFirst().getExpression(), contextData.getExpectedType().normalize(NormalizationMode.RNF).unfold(Collections.emptySet(), null, true, false));
    } else {
      TypedExpression arg = typechecker.typecheck(contextData.getArguments().getFirst().getExpression(), null);
      if (arg == null) {
        return null;
      }
      return typechecker.replaceType(arg, arg.getType().normalize(NormalizationMode.RNF).unfold(Collections.emptySet(), null, true, false), contextData.getMarker(), false);
    }
  }
}
