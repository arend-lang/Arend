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
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.group.BaseCommutativeGroupEquationMeta;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
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
  @Dependency(name = "RingSolverModel.apply-axioms")        ArendRef applyAxioms;

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
  protected @Nullable BaseCommutativeGroupEquationMeta.MyHint<List<Monomial>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    return BaseCommutativeGroupEquationMeta.parseCoefHint(hint, hintType, operations, values, typechecker, this);
  }

  private void addNF(List<Monomial> poly, int coef, List<Monomial> add) {
    for (Monomial monomial : add) {
      poly.add(monomial.multiply(coef));
    }
  }

  @Override
  protected @Nullable Pair<HintResult<List<Monomial>>, HintResult<List<Monomial>>> applyHints(@NotNull List<Hint<List<Monomial>>> hints, @NotNull List<Monomial> left, @NotNull List<Monomial> right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    List<Monomial> newNF = new ArrayList<>(left);
    addNF(newNF, -1, right);

    List<ConcreteExpression> axioms = new ArrayList<>();
    for (Hint<List<Monomial>> hint : hints) {
      int c = ((BaseCommutativeGroupEquationMeta.MyHint<List<Monomial>>) hint).getCoefficient();
      addNF(newNF, -c, hint.leftNF);
      addNF(newNF, c, hint.rightNF);
      axioms.add(factory.tuple(
          nfToConcrete(Collections.singletonList(new Monomial(BigInteger.valueOf(c), Collections.emptyList())), factory),
          hint.left.generateReflectedTerm(factory, getVarTerm()),
          hint.right.generateReflectedTerm(factory, getVarTerm()),
          factory.core(hint.typed)));
    }

    newNF = normalizeNF(newNF);
    return new Pair<>(new BaseEquationMeta.HintResult<>(factory.app(factory.ref(applyAxioms), true, factory.ref(envRef.get()), Utils.makeArray(axioms, factory), nfToConcrete(newNF, factory)), newNF), new BaseEquationMeta.HintResult<>(null, Collections.emptyList()));
  }
}
