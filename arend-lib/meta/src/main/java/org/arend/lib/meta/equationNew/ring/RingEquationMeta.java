package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
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

public class RingEquationMeta extends BaseAlgebraEquationMeta {
  @Dependency                                               CoreClassDefinition Ring;
  @Dependency                                               ArendRef RingSolverModel;
  @Dependency(name = "AddGroup.negative")                   CoreClassField negative;
  @Dependency(name = "RingSolverModel.Term.:zro")           ArendRef zroTerm;
  @Dependency(name = "RingSolverModel.Term.:ide")           ArendRef ideTerm;
  @Dependency(name = "RingSolverModel.Term.:+")             ArendRef addTerm;
  @Dependency(name = "RingSolverModel.Term.:*")             ArendRef mulTerm;
  @Dependency(name = "RingSolverModel.Term.:negative")      ArendRef negativeTerm;
  @Dependency(name = "RingSolverModel.Term.coef")           ArendRef coefTerm;
  @Dependency(name = "RingSolverModel.Term.var")            ArendRef varTerm;
  @Dependency(name = "RingSolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "RingSolverModel.terms-equality-conv") ArendRef termsEqualityConv;

  @Override
  protected boolean isCommutative() {
    return false;
  }

  @Override
  protected @NotNull ArendRef getZroTerm() {
    return zroTerm;
  }

  @Override
  protected @NotNull ArendRef getIdeTerm() {
    return ideTerm;
  }

  @Override
  protected @NotNull ArendRef getAddTerm() {
    return addTerm;
  }

  @Override
  protected @NotNull ArendRef getMulTerm() {
    return mulTerm;
  }

  @Override
  protected @NotNull ArendRef getCoefTerm() {
    return coefTerm;
  }

  @Override
  protected @Nullable ArendRef getNegativeTerm() {
    return negativeTerm;
  }

  @Override
  protected @Nullable CoreClassField getNegative() {
    return negative;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return Ring;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return RingSolverModel;
  }

  @Override
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
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
