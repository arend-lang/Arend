package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.EquationFindError;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CRingEquationMeta extends BaseRingEquationMeta {
  @Dependency                                                 CoreClassDefinition CRing;
  @Dependency                                                 ArendRef CRingSolverModel;
  @Dependency(name = "CRingSolverModel.terms-equality")       ArendRef termsEquality;
  @Dependency(name = "CRingSolverModel.terms-equality-conv")  ArendRef termsEqualityConv;
  @Dependency(name = "CRingSolverModel.apply-axioms")         ArendRef applyAxioms;

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
    List<Monomial> newNF = new ArrayList<>(left);
    addNF(newNF, -1, right);

    List<ConcreteExpression> axioms = new ArrayList<>();
    for (Hint<List<Monomial>> hint : hints) {
      Integer c = getHintCoefficient(hint);
      if (c == null && !hint.leftNF.isEmpty()) {
        newNF = normalizeNF(newNF);
        var divRem = Monomial.divideAndRemainder(newNF, hint.leftNF);
        if (divRem.proj1.isEmpty()) {
          typechecker.getErrorReporter().report(new EquationFindError<>(getNFPrettyPrinter(), newNF, Collections.emptyList(), hint.leftNF, values.getValues(), hint.originalExpression));
          return null;
        }
        axioms.add(getConcreteAxiom(divRem.proj1, hint, factory));
        newNF = new ArrayList<>();
        Monomial.multiplyComm(hint.rightNF, divRem.proj1, newNF);
        newNF.addAll(divRem.proj2);
      } else {
        axioms.add(applyHint(hint, newNF, factory));
      }
    }

    newNF = normalizeNF(newNF);
    return new Pair<>(new BaseEquationMeta.HintResult<>(factory.app(factory.ref(applyAxioms), true, factory.ref(envRef.get()), Utils.makeArray(axioms, factory), nfToConcrete(newNF, factory)), newNF), new BaseEquationMeta.HintResult<>(null, Collections.emptyList()));
  }
}
