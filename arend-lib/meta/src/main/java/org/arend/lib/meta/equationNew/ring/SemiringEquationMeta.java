package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class SemiringEquationMeta extends BaseSemiringEquationMeta {
  @Dependency CoreClassDefinition Semiring;
  @Dependency ArendRef SemiringSolverModel;
  @Dependency(name = "SemiringSolverModel.apply-axiom") ArendRef applyAxiom;

  @Override
  protected boolean isCommutative() {
    return false;
  }

  @Override
  protected @NotNull ArendRef getApplyAxiom() {
    return applyAxiom;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return Semiring;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return SemiringSolverModel;
  }

  /*
  @Override
  protected @Nullable AbstractNFResult abstractNF(@NotNull BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
    return hint.applyToLeft || hint.applyToRight ? super.abstractNF(hint, nf) : abstractNFMul(hint, nf);
  }

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

  private @Nullable AbstractNFResult abstractNFMul(@NotNull BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
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
