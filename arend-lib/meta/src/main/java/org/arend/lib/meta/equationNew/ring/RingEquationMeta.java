package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
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

public class RingEquationMeta extends BaseRingEquationMeta {
  @Dependency                                         CoreClassDefinition Ring;
  @Dependency                                               ArendRef RingSolverModel;
  @Dependency(name = "RingSolverModel.apply-axioms")        ArendRef applyAxioms;
  @Dependency(name = "RingSolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "RingSolverModel.terms-equality-conv") ArendRef termsEqualityConv;

  @Override
  protected boolean isCommutative() {
    return false;
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
      axioms.add(applyHint(hint, newNF, factory));
    }

    newNF = normalizeNF(newNF);
    return new Pair<>(new BaseEquationMeta.HintResult<>(factory.app(factory.ref(applyAxioms), true, factory.ref(envRef.get()), Utils.makeArray(axioms, factory), nfToConcrete(newNF, factory)), newNF), new BaseEquationMeta.HintResult<>(null, Collections.emptyList()));
  }
}
