package org.arend.lib.meta.simplify.field;

import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreAppExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFieldCallExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Matches an application of a class field without unfolding its implementation.
 * For a transparent structure such as {@code ComplexField}, matching up to reduction would unfold
 * multiplication into its components and lose the original arguments.
 */
final class FieldOperationMatcher {
  private FieldOperationMatcher() {}

  static @Nullable List<CoreExpression> match(@NotNull CoreExpression expression, @NotNull CoreClassField field, @NotNull TypedExpression instance, int argumentCount, @NotNull ExpressionTypechecker typechecker) {
    List<CoreExpression> arguments = new ArrayList<>(argumentCount);
    CoreExpression function = expression.getUnderlyingExpression();
    for (int i = 0; i < argumentCount && function instanceof CoreAppExpression app; i++) {
      arguments.add(app.getArgument());
      function = app.getFunction().getUnderlyingExpression();
    }
    Collections.reverse(arguments);

    if (arguments.size() != argumentCount || !(function instanceof CoreFieldCallExpression fieldCall && fieldCall.getDefinition() == field)) return null;
    return Utils.safeCompare(typechecker, fieldCall.getArgument(), instance.getExpression(), CMP.EQ, null, false, true, false) ? arguments : null;
  }
}
