package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.monoid.BaseCommutativeMonoidEquationMeta;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SemiringEquationMeta extends BaseSemiringEquationMeta {
  @Dependency CoreClassDefinition Semiring;
  @Dependency ArendRef SemiringSolverModel;

  @Override
  protected boolean isCommutative() {
    return false;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return Semiring;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return SemiringSolverModel;
  }

  @Override
  protected @Nullable Hint<List<Monomial>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    return BaseCommutativeMonoidEquationMeta.parseHint(hint, hintType, operations, values, typechecker, this);
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

    AbstractNFResult result = // myHint.applyToLeft || myHint.applyToRight ? abstractExactNF(myHint, current) : abstractNF(myHint, current);
        abstractExactNF(myHint, current);
    return result == null ? null : new BaseEquationMeta.HintResult<>(factory.appBuilder(factory.ref(applyAxiom))
        .app(factory.ref(envRef.get()))
        .app(hint.left.generateReflectedTerm(factory, getVarTerm()))
        .app(hint.right.generateReflectedTerm(factory, getVarTerm()))
        .app(factory.core(hint.typed))
        .app(nfToConcrete(result.leftMultiplier, factory))
        .app(nfToConcrete(result.rightMultiplier, factory))
        .app(nfToConcrete(result.addition, factory))
        .build(), result.newNF);
  }

  private record AbstractNFResult(List<Monomial> leftMultiplier, List<Monomial> rightMultiplier, List<Monomial> addition, List<Monomial> newNF) {}

  // applies a hint without multipliers
  private @Nullable AbstractNFResult abstractExactNF(@NotNull BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
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

  /*
  private static List<Pair<Monomial,Monomial>> divideMonomial(Monomial m1, Monomial m2) {
    if (m2.elements().size() > m1.elements().size()) return null;
    BigInteger[] divRem = m1.coefficient().divideAndRemainder(m2.coefficient());
    if (!divRem[1].equals(BigInteger.ZERO)) return null;

    List<Pair<Monomial,Monomial>> result = new ArrayList<>();
    int n = m1.elements().size() - m2.elements().size();
    for (int i = 0; i <= n; i++) {
      if (m1.elements().subList(i, i + m2.elements().size()).equals(m2.elements())) {
        result.add(new Pair<>(new Monomial(divRem[0], m1.elements().subList(0, i)), new Monomial(BigInteger.ONE, m1.elements().subList(i + m2.elements().size(), m1.elements().size()))));
      }
    }
    return result;
  }

  private @Nullable AbstractNFResult abstractNF(@NotNull BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
    record Triple(Monomial left, Monomial right, List<Monomial> addition) {}

    List<Monomial> left = mulCoefNF(hint.count, hint.leftNF);
    Monomial first = left.getFirst();
    List<Triple> multipliers = new ArrayList<>(); // the list consist of triples (l,r,a) such that l * first * r + a = nf
    for (int i = 0; i < nf.size(); i++) {
      Monomial monomial = nf.get(i);
      List<Pair<Monomial,Monomial>> list = divideMonomial(monomial, first);
      if (list != null) {
        for (Pair<Monomial, Monomial> pair : list) {
          List<Monomial> addition = new ArrayList<>(nf.size() - 1);
          for (int j = 0; j < nf.size(); j++) {
            if (i != j) addition.add(nf.get(j));
          }
          multipliers.add(new Triple(pair.proj1, pair.proj2, addition));
        }
      }
    }

    for (int i = 1; i < left.size(); i++) {

    }
  }
  */
}
