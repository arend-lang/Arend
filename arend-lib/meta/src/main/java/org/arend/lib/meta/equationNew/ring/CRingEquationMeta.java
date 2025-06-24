package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CRingEquationMeta extends BaseRingEquationMeta {
  @Dependency                                                 CoreClassDefinition CRing;
  @Dependency                                                 ArendRef CRingSolverModel;
  @Dependency(name = "CRingSolverModel.terms-equality")       ArendRef termsEquality;
  @Dependency(name = "CRingSolverModel.terms-equality-conv")  ArendRef termsEqualityConv;

  @Override
  protected boolean isCommutative() {
    return true;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return CRing;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CRingSolverModel;
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEquality(@NotNull Lazy<ArendRef> solverRef, @Nullable ConcreteExpression solver, @NotNull ConcreteFactory factory) {
    return factory.ref(termsEquality);
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEqualityConv(@NotNull Lazy<ArendRef> solverRef, @NotNull ConcreteFactory factory) {
    return factory.ref(termsEqualityConv);
  }

  @Override
  protected @Nullable Pair<HintResult<List<Monomial>>, HintResult<List<Monomial>>> applyHints(@NotNull List<Hint<List<Monomial>>> hints, @NotNull List<Monomial> left, @NotNull List<Monomial> right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    return super.applyHints(hints, normalizeNF(addNF(left, mulCoefNF(-1, right))), Collections.emptyList(), solverRef, envRef, values, typechecker, factory);
  }
}
