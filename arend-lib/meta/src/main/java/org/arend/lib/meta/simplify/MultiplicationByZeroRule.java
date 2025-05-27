package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.StdExtension;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;

import java.util.List;

public class MultiplicationByZeroRule extends LocalSimplificationRuleBase {
  private final FunctionMatcher mulMatcher;
  private final FunctionMatcher zroMatcher;

  public MultiplicationByZeroRule(TypedExpression instance, CoreClassCallExpression classCall, StdExtension ext, SimplifyMeta meta, ConcreteReferenceExpression refExpr, ExpressionTypechecker typechecker) {
    super(instance, classCall, meta, refExpr, typechecker);
    this.mulMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.mul, typechecker, factory, refExpr, ext, 2);
    this.zroMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, ext.zro, typechecker, factory, refExpr, ext, 0);
  }

  @Override
  protected Pair<CoreExpression, ConcreteExpression> simplifySubexpression(CoreExpression subexpr) {
    List<CoreExpression> args = mulMatcher.match(subexpr);
    if (args != null) {
      var left = args.get(args.size() - 2);
      var right = args.getLast();
      boolean isZroOnTheLeft = zroMatcher.match(left) != null;
      var zro = isZroOnTheLeft ? left : zroMatcher.match(right) != null ? right : null;
      var value = zro == left ? right : left;

      if (zro != null) {
        return new Pair<>(zro, factory.appBuilder(factory.ref(isZroOnTheLeft ? meta.zeroMulLeft : meta.zeroMulRight)).app(factory.hole(), false).app(factory.core(value.computeTyped()), false).build());
      }
    }
    return null;
  }
}
