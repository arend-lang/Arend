package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.monoid.BaseCommutativeMonoidEquationMeta;
import org.arend.lib.meta.equationNew.term.*;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseSemiringEquationMeta extends BaseAlgebraEquationMeta {
  @Dependency(name = "SolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "SolverModel.terms-equality-conv") ArendRef termsEqualityConv;
  @Dependency(name = "SemiringSolverModel.Term.:zro")   ArendRef zroTerm;
  @Dependency(name = "SemiringSolverModel.Term.:ide")   ArendRef ideTerm;
  @Dependency(name = "SemiringSolverModel.Term.:+")     ArendRef addTerm;
  @Dependency(name = "SemiringSolverModel.Term.:*")     ArendRef mulTerm;
  @Dependency(name = "SemiringSolverModel.Term.coef")   ArendRef coefTerm;
  @Dependency(name = "SemiringSolverModel.Term.var")    ArendRef varTerm;

  protected abstract @NotNull ArendRef getApplyAxiom();

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
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEquality(@NotNull Lazy<ArendRef> solverRef, @Nullable ConcreteExpression solver, @NotNull ConcreteFactory factory) {
    return factory.app(factory.ref(termsEquality), false, solver == null || solverRef.isUsed() ? factory.ref(solverRef.get()) : solver);
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEqualityConv(@NotNull Lazy<ArendRef> solverRef, @NotNull ConcreteFactory factory) {
    return factory.app(factory.ref(termsEqualityConv), false, factory.ref(solverRef.get()));
  }

  @Override
  protected @Nullable Hint<List<Monomial>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    return BaseCommutativeMonoidEquationMeta.parseHint(hint, hintType, operations, values, typechecker, this);
  }

  protected record AbstractNFResult(List<Monomial> leftMultiplier, List<Monomial> rightMultiplier, List<Monomial> addition, List<Monomial> newNF) {}

  // This implementation applies a hint without multipliers
  protected @Nullable AbstractNFResult abstractNF(@NotNull BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
    List<Monomial> addition = new ArrayList<>(nf);
    for (Monomial monomial : hint.leftNF) {
      if (!addition.remove(monomial.multiply(hint.count))) {
        return null;
      }
    }

    return new AbstractNFResult(
        Collections.singletonList(new Monomial(BigInteger.valueOf(hint.count), Collections.emptyList())),
        Collections.singletonList(new Monomial(BigInteger.ONE, Collections.emptyList())),
        addition,
        normalizeNF(addNF(addition, mulCoefNF(hint.count, hint.rightNF))));
  }

  @Override
  protected @Nullable BaseEquationMeta.HintResult<List<Monomial>> applyHint(@NotNull BaseEquationMeta.Hint<List<Monomial>> hint, @NotNull List<Monomial> current, int[] position, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull ConcreteFactory factory) {
    position[0]++;
    BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> myHint = (BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>>) hint;
    if (position[0] == 1 && !myHint.applyToLeft || position[0] == 2 && !myHint.applyToRight) {
      return null;
    }
    if (myHint.count == 1 && hint.leftNF.equals(current)) {
      return super.applyHint(hint, current, position, solverRef, envRef, factory);
    }

    AbstractNFResult result = abstractNF(myHint, current);
    if (result == null) return null;
    ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getApplyAxiom()))
        .app(factory.ref(envRef.get()))
        .app(hint.left.generateReflectedTerm(factory, getVarTerm()))
        .app(hint.right.generateReflectedTerm(factory, getVarTerm()))
        .app(factory.core(hint.typed));
    if (result.leftMultiplier != null) builder.app(nfToConcrete(result.leftMultiplier, factory));
    if (result.rightMultiplier != null) builder.app(nfToConcrete(result.rightMultiplier, factory));
    if (result.addition != null) builder.app(nfToConcrete(result.addition, factory));
    return new BaseEquationMeta.HintResult<>(builder.build(), result.newNF);
  }
}
