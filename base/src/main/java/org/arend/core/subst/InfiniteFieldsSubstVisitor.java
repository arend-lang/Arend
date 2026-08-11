package org.arend.core.subst;

import org.arend.core.context.binding.Binding;
import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.Expression;
import org.arend.core.expr.FieldCallExpression;
import org.arend.core.expr.ReferenceExpression;
import org.arend.ext.core.level.LevelSubstitution;

public class InfiniteFieldsSubstVisitor extends SubstVisitor {
  private final Binding myOldBinding;
  private final Binding myNewBinding;

  public InfiniteFieldsSubstVisitor(Binding binding, ReferenceExpression refExpr, LevelSubstitution levelSubstitution) {
    super(new ExprSubstitution(binding, refExpr), levelSubstitution);
    myOldBinding = binding;
    myNewBinding = refExpr.getBinding();
  }

  @Override
  public Expression visitFieldCall(FieldCallExpression expr, Void params) {
    Expression arg = expr.getArgument().accept(this, null);
    if (expr.getDefinition().isInfiniteField() && expr.getArgument() instanceof ReferenceExpression refExpr1 && refExpr1.getBinding() == myOldBinding && arg instanceof ReferenceExpression refExpr && refExpr.getBinding() == myNewBinding && refExpr.getBinding().getType() instanceof ClassCallExpression classCall) {
      Expression result = classCall.getImplementation(expr.getDefinition(), arg);
      if (result != null) {
        return result;
      }
    }

    return FieldCallExpression.make(expr.getDefinition(), arg);
  }
}
