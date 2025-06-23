package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.equationNew.monoid.BaseCommutativeMonoidEquationMeta;
import org.arend.lib.ring.Monomial;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class CSemiringEquationMeta extends BaseSemiringEquationMeta {
  @Dependency CoreClassDefinition CSemiring;
  @Dependency ArendRef CSemiringSolverModel;
  @Dependency(name = "CSemiringSolverModel.apply-axiom") ArendRef applyAxiom;

  @Override
  protected boolean isCommutative() {
    return true;
  }

  @Override
  protected @NotNull ArendRef getApplyAxiom() {
    return applyAxiom;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return CSemiring;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CSemiringSolverModel;
  }

  private static Monomial divideMonomial(Monomial m1, Monomial m2) {
    if (m2.elements().size() > m1.elements().size()) return null;
    BigInteger[] divRem = m1.coefficient().divideAndRemainder(m2.coefficient());
    if (!divRem[1].equals(BigInteger.ZERO)) return null;

    int i = 0;
    List<Integer> result = new ArrayList<>();
    for (int j = 0; j < m2.elements().size(); i++) {
      if (i >= m1.elements().size() || m1.elements().get(i) > m2.elements().get(j)) return null;
      if (m1.elements().get(i).equals(m2.elements().get(j))) {
        j++;
      } else {
        result.add(m1.elements().get(i));
      }
    }
    result.addAll(m1.elements().subList(i, m1.elements().size()));
    return new Monomial(divRem[0], result);
  }

  @Override
  protected @Nullable AbstractNFResult abstractNF(BaseCommutativeMonoidEquationMeta.@NotNull MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
    if (!hint.applyToLeft || !hint.applyToRight || hint.leftNF.isEmpty()) {
      AbstractNFResult result = super.abstractNF(hint, nf);
      return result == null ? null : new AbstractNFResult(result.leftMultiplier(), null, result.addition(), result.newNF());
    }

    Monomial first = hint.leftNF.getFirst();
    List<Monomial> factor = new ArrayList<>(); // the largest polynomial such that factor * first + a = nf for some a
    for (Monomial monomial : nf) {
      Monomial factorMonomial = divideMonomial(monomial, first);
      if (factorMonomial != null) {
        factor.add(factorMonomial);
      }
    }

    factor.removeIf(factorMonomial -> {
      for (int i = 1; i < hint.leftNF.size(); i++) {
        if (!nf.contains(factorMonomial.multiplyComm(hint.leftNF.get(i)))) {
          return true;
        }
      }
      return false;
    });
    if (factor.isEmpty()) return null;

    List<Monomial> leftMultiplied = new ArrayList<>();
    Monomial.multiplyComm(hint.leftNF, factor, leftMultiplied);
    leftMultiplied = normalizeNF(leftMultiplied);
    List<Monomial> addition = new ArrayList<>();
    for (Monomial monomial : nf) {
      if (!leftMultiplied.remove(monomial)) {
        addition.add(monomial);
      }
    }
    if (!leftMultiplied.isEmpty()) return null;

    return new AbstractNFResult(factor, null, addition, normalizeNF(addNF(addition, hint.rightNF)));
  }
}
