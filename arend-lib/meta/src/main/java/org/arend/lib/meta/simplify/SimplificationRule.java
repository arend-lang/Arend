package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Pair;

public interface SimplificationRule {
  /**
   * @param expression
   * @return Pair (simplifiedExpr, p : simplifiedExpr = expression)
   */
  Pair<ConcreteExpression, ConcreteExpression> apply(TypedExpression expression);
}
