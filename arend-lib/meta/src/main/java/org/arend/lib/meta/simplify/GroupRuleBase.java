package org.arend.lib.meta.simplify;

import org.arend.ext.ArendPrelude;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equation.datafactory.GroupDataFactory;
import org.arend.lib.util.Values;

public abstract class GroupRuleBase implements SimplificationRule {
  private final ArendPrelude prelude;
  protected final Values<CoreExpression> values;
  protected final ConcreteFactory factory;
  protected final SimplifyMeta meta;
  protected final FunctionMatcher mulMatcher;
  protected final FunctionMatcher ideMatcher;
  protected final FunctionMatcher invMatcher;
  protected final boolean isAdditive;
  protected final boolean isCommutative;
  protected final TypedExpression instance;
  protected final ArendRef dataRef;

  public GroupRuleBase(TypedExpression instance, CoreClassCallExpression classCall, SimplifyMeta meta, ConcreteReferenceExpression refExpr, ExpressionTypechecker typechecker, boolean isAdditive, boolean isCommutative) {
    this.values = new Values<>(typechecker, refExpr);
    this.factory = typechecker.getFactory().withData(refExpr);
    this.prelude = typechecker.getPrelude();
    this.meta = meta;
    if (isAdditive) {
      var convertedInst = typechecker.typecheck(factory.appBuilder(factory.ref(isCommutative ? meta.fromAbGroupToCGroup : meta.fromAddGroupToGroup)).app(factory.core(instance)).build(), null);
      this.instance = convertedInst != null ? convertedInst : instance;
    } else {
      this.instance = instance;
    }
    this.isAdditive = isAdditive;
    this.isCommutative = isCommutative;
    this.dataRef = factory.local("d");
    if (isAdditive) {
      this.mulMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.plus, typechecker, factory, refExpr, 2);
      this.invMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.negative, typechecker, factory, refExpr, 1);
      this.ideMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.zro, typechecker, factory, refExpr, 0);
    } else {
      this.mulMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.mul, typechecker, factory, refExpr, 2);
      this.invMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.inverse, typechecker, factory, refExpr, 1);
      this.ideMatcher = FunctionMatcher.makeFieldMatcher(classCall, instance, meta.ide, typechecker, factory, refExpr, 0);
    }
  }

  @Override
  public ConcreteExpression finalizeEqProof(ConcreteExpression proof) {
    return new GroupDataFactory(meta, prelude, dataRef, values, factory, instance, isCommutative).wrapWithData(proof);
  }
}
