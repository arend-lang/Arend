package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;

import java.util.List;

public class MulOfNegativesRule extends LocalSimplificationRuleBase {
  private final FunctionMatcher mulMatcher;
  private final FunctionMatcher negativeMatcher;

  public MulOfNegativesRule(TypedExpression instance, CoreClassCallExpression classCall, SimplifyMeta meta, ConcreteReferenceExpression refExpr, ExpressionTypechecker typechecker) {
    super(instance, classCall, meta, refExpr, typechecker);
    this.mulMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.mul, typechecker, factory, refExpr, 2);
    this.negativeMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.negative, typechecker, factory, refExpr, 1);
  }

  @Override
  protected Pair<CoreExpression, ConcreteExpression> simplifySubexpression(CoreExpression subexpr) {
    List<CoreExpression> args = mulMatcher.match(subexpr);
    if (args != null) {
      var left = args.get(args.size() - 2);
      var right = args.getLast();
      args = negativeMatcher.match(left);
      boolean isNegOnTheLeft = args != null;
      if (args == null) {
        args = negativeMatcher.match(right);
        if (args == null) {
          return null;
        }
      }
      var firstValue = isNegOnTheLeft ? args.getFirst() : left;
      var secondValue = isNegOnTheLeft ? right : args.getFirst();
      ConcreteExpression negPath = factory.ref(isNegOnTheLeft ? meta.negMulLeft : meta.negMulRight);

      if (firstValue != null) {
        var subexprPath = factory.appBuilder(negPath)
                .app(factory.hole(), false)
                .app(factory.core(firstValue.computeTyped()), false)
                .app(factory.core(secondValue.computeTyped()), false).build();
        var newExpr = factory.appBuilder(factory.ref(meta.negative.getRef())).
                app(factory.appBuilder(factory.ref(meta.mul.getRef())).app(factory.core(firstValue.computeTyped())).app(factory.core(secondValue.computeTyped())).build()).build();
        var checkedNewExpr = typechecker.typecheck(newExpr, subexpr.computeType());
        if (checkedNewExpr == null) return null;
        return new Pair<>(checkedNewExpr.getExpression(), subexprPath);
      }
    }
    return null;
  }
}
