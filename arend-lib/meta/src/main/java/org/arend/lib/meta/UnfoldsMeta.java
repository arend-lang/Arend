package org.arend.lib.meta;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnfoldsMeta extends BaseMetaDefinition {
  @Override
  public boolean[] argumentExplicitness() {
    return new boolean[] { true };
  }

  @Override
  public boolean allowExcessiveArguments() {
    return false;
  }

  private CoreExpression unfold(CoreExpression expr) {
    while (true) {
      expr = expr.normalize(NormalizationMode.WHNF);
      if (expr instanceof CoreFunCallExpression && ((CoreFunCallExpression) expr).getDefinition().getActualBody() instanceof CoreExpression) {
        CoreExpression newExpr = ((CoreFunCallExpression) expr).evaluate();
        if (newExpr != null) {
          expr = newExpr;
          continue;
        }
      }
      break;
    }
    return expr;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    if (contextData.getExpectedType() != null) {
      return typechecker.typecheck(contextData.getArguments().getLast().getExpression(), unfold(contextData.getExpectedType()));
    } else {
      TypedExpression arg = typechecker.typecheck(contextData.getArguments().getLast().getExpression(), null);
      return arg == null ? null : typechecker.replaceType(arg, unfold(arg.getType()), contextData.getMarker(), true);
    }
  }
}
