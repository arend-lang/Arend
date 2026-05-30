package org.arend.lib.meta.equationNew.semigroup;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.error.equation.MonoidNFPrettyPrinter;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class NonCommutativeSemigroupEquationMeta extends BaseSemigroupEquationMeta<List<Integer>> {
  @Override
  protected @NotNull List<Integer> normalize(EquationTerm term) {
    List<Integer> result = new ArrayList<>();
    normalize(term, result);
    return result;
  }

  private void normalize(EquationTerm term, List<Integer> result) {
    switch (term) {
      case OpTerm opTerm -> {
        for (EquationTerm argument : opTerm.arguments()) {
          normalize(argument, result);
        }
      }
      case VarTerm(int index) -> result.add(index);
      default -> throw new IllegalStateException();
    }
  }

  // | The normal form of a semigroup word is always non-empty (every term has at least one variable),
  //   so there is no `ide` fallback as in the monoid solver.
  @Override
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<Integer> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    ConcreteExpression result = factory.core(values.getValue(nf.getLast()).computeTyped());
    for (int i = nf.size() - 2; i >= 0; i--) {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getMul().getRef()));
      if (instance != null) {
        builder.app(factory.core(instance), false);
      }
      result = builder.app(factory.core(values.getValue(nf.get(i)).computeTyped())).app(result).build();
    }
    return result;
  }

  @Override
  protected @NotNull NFPrettyPrinter<List<Integer>> getNFPrettyPrinter() {
    return new MonoidNFPrettyPrinter(true);
  }
}
