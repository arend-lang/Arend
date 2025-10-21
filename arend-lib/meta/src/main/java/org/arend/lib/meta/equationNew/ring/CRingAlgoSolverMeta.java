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
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.arend.lib.util.algorithms.ComMonoidWP;
import org.arend.lib.util.algorithms.groebner.Buchberger;
import org.arend.lib.util.algorithms.idealmem.GroebnerIM;
import org.arend.lib.util.algorithms.polynomials.Poly;
import org.arend.lib.util.algorithms.polynomials.Ring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CRingAlgoSolverMeta extends BaseAlgebraEquationMeta {
  @Dependency(name = "AddGroup.negative")                   CoreClassField negative;

  @Dependency(name = "RingSolverModel.Term.:zro")           ArendRef zroTerm;
  @Dependency(name = "RingSolverModel.Term.:ide")           ArendRef ideTerm;
  @Dependency(name = "RingSolverModel.Term.:+")             ArendRef addTerm;
  @Dependency(name = "RingSolverModel.Term.:negative")      ArendRef negativeTerm;
  @Dependency(name = "RingSolverModel.Term.:*")             ArendRef mulTerm;
  @Dependency(name = "RingSolverModel.Term.coef")           ArendRef coefTerm;
  @Dependency(name = "RingSolverModel.Term.var")            ArendRef varTerm;

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
  protected @NotNull CoreClassField getNegative() {
    return negative;
  }

  @Override
  protected @NotNull ArendRef getNegativeTerm() {
    return negativeTerm;
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
  protected @NotNull CoreClassDefinition getClassDef() {
    return CRing;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CRingSolverModel;
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

  private int numVarsInNF(List<Monomial> nf) {
    if (nf.isEmpty()) return 0;
    return Collections.max(nf.stream().map(m -> m.elements().isEmpty() ? 0 : Collections.max(m.elements())).toList()) + 1;
  }

  @Override
  protected @Nullable Pair<HintResult<List<Monomial>>, HintResult<List<Monomial>>> applyHints(@NotNull List<Hint<List<Monomial>>> hints, @NotNull List<Monomial> left, @NotNull List<Monomial> right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    int numVars = Stream.of(hints.stream().map(ax -> Math.max(numVarsInNF(ax.leftNF), numVarsInNF(ax.rightNF))).max(Integer::compareTo).orElse(0),
        numVarsInNF(left), numVarsInNF(right))
      .max(Integer::compareTo)
      .orElse(0);

    var p = nfToPoly(left, numVars).subtr(nfToPoly(right, numVars));
    var idealGen = new ArrayList<Poly<BigInteger>>();

    for (var axiom : hints) {
      Poly<BigInteger> poly = nfToPoly(axiom.leftNF, numVars).subtr(nfToPoly(axiom.rightNF, numVars));
      if (!poly.isZero()) {
        idealGen.add(poly);
      }
    }

    var idealCoeffs = new GroebnerIM(new Buchberger()).computeGenDecomposition(p, idealGen);

    if (idealCoeffs == null) {
      return null;
    }

    // intNF sum_i idealCoeffs(i) * (hints(i).left - hints(i).right) = 0
    var idealGenDecompEqZero = factory.app(factory.ref(applyAxioms), true, factory.ref(envRef.get()),
      Utils.makeArray(IntStream.range(0, idealGen.size()).mapToObj(i -> BaseRingEquationMeta.getConcreteAxiom(nfToConcrete(polyToNF(idealCoeffs.get(i)), factory), hints.get(i), getVarTerm(), factory)).collect(Collectors.toList()), factory),
      nfToConcrete(new ArrayList<>(), factory));

    return new Pair<>(new HintResult<>(idealGenDecompEqZero, right),
      new HintResult<>(factory.ref(typechecker.getPrelude().getIdpRef()), right));
  }

  private static Poly<BigInteger> nfToPoly(List<Monomial> nf, int numVars) {
    var poly = Poly.constant(BigInteger.ZERO, numVars, Ring.Z);

    for (Monomial m : nf) {
      poly = poly.add(new org.arend.lib.util.algorithms.polynomials.Monomial<>(m.coefficient(), ComMonoidWP.elemsSeqToPowersSeq(m.elements(), numVars), Ring.Z));
    }
    return poly;
  }

  private static List<Monomial> polyToNF(Poly<BigInteger> poly) {
    var nf =  poly.monomials.stream().map(m -> new Monomial(m.coefficient, ComMonoidWP.powersSeqToElemsSeq(m.degreeVector))).collect(Collectors.toList());
    if (nf.size() > 1 && nf.getFirst().elements().isEmpty() && nf.getFirst().coefficient().equals(BigInteger.ZERO)) {
      nf.removeFirst();
    }
    return nf;
  }
}
