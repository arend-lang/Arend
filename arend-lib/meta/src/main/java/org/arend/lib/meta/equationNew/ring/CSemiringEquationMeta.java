package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.equationNew.monoid.BaseCommutativeMonoidEquationMeta;
import org.arend.lib.ring.Monomial;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  protected @Nullable AbstractNFResult abstractNF(BaseCommutativeMonoidEquationMeta.@NotNull MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
    if (!hint.applyToLeft || !hint.applyToRight || hint.leftNF.isEmpty()) {
      AbstractNFResult result = super.abstractNF(hint, nf);
      return result == null ? null : new AbstractNFResult(result.leftMultiplier(), null, result.addition(), result.newNF());
    }

    var divRem = Monomial.divideAndRemainder(nf, hint.leftNF);
    return divRem.proj1.isEmpty() ? null : new AbstractNFResult(divRem.proj1, null, divRem.proj2, normalizeNF(addNF(divRem.proj2, hint.rightNF)));
  }
}
