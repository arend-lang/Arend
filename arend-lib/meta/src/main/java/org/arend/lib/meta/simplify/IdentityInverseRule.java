package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;

import java.util.List;

public class IdentityInverseRule extends LocalSimplificationRuleBase {
  private final FunctionMatcher invMatcher;
  private final FunctionMatcher ideMatcher;
  private final ArendRef invIde;

  public IdentityInverseRule(TypedExpression instance, CoreClassCallExpression classCall, SimplifyMeta meta, ConcreteReferenceExpression refExpr, ExpressionTypechecker typechecker, boolean isAdditive) {
    super(instance, classCall, meta, refExpr, typechecker);
    if (isAdditive) {
      this.invMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.negative, typechecker, factory, refExpr, 1);
      this.ideMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.zro, typechecker, factory, refExpr, 0);
      this.invIde = meta.negativeZro;
    } else {
      this.invMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.inverse, typechecker, factory, refExpr, 1);
      this.ideMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.ide, typechecker, factory, refExpr, 0);
      this.invIde = meta.invIde;
    }
  }

  @Override
  protected Pair<CoreExpression, ConcreteExpression> simplifySubexpression(CoreExpression subexpr) {
    List<CoreExpression> args = invMatcher.match(subexpr);
    if (args != null) {
      var arg = args.getFirst();
      if (ideMatcher.match(arg) != null) {
        return new Pair<>(arg, factory.ref(invIde));
      }
    }
    return null;
  }
}
