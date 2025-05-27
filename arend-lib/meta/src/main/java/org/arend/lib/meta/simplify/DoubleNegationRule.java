package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.StdExtension;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;

import java.util.List;

public class DoubleNegationRule extends LocalSimplificationRuleBase {
  private final FunctionMatcher negativeMatcher;
  private final ArendRef negIsInv;

  public DoubleNegationRule(TypedExpression instance, CoreClassCallExpression classCall, StdExtension ext, SimplifyMeta meta, ConcreteReferenceExpression refExpr, ExpressionTypechecker typechecker, boolean isAdditive) {
    super(instance, classCall, meta, refExpr, typechecker);
    if (isAdditive) {
      this.negativeMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.negative, typechecker, factory, refExpr, ext, 1);
      this.negIsInv = meta.negIsInv;
    } else {
      this.negativeMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.inverse, typechecker, factory, refExpr, ext, 1);
      this.negIsInv = meta.invIsInv;
    }
  }

  @Override
  protected Pair<CoreExpression, ConcreteExpression> simplifySubexpression(CoreExpression subexpr) {
    List<CoreExpression> args = negativeMatcher.match(subexpr);
    if (args != null) {
      args = negativeMatcher.match(args.getFirst());
      if (args != null) {
        var path = factory.appBuilder(factory.ref(negIsInv))
          .app(factory.hole(), false)
          .app(factory.core(args.getFirst().computeTyped()), false)
          .build();
        return new Pair<>(args.getFirst(), path);
      }
    }
    return null;
  }
}
