package org.arend.lib.meta.equationNew.semigroup;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.error.equation.CommutativeGroupNFPrettyPrinter;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class CommutativeSemigroupEquationMeta extends BaseSemigroupEquationMeta<List<Integer>> {
  private void normalize(EquationTerm term, List<Integer> result) {
    switch (term) {
      case OpTerm opTerm -> {
        for (EquationTerm argument : opTerm.arguments()) {
          normalize(argument, result);
        }
      }
      case VarTerm(int index) -> {
        while (index >= result.size()) {
          result.add(0);
        }
        result.set(index, result.get(index) + 1);
      }
      default -> throw new IllegalStateException();
    }
  }

  private void trimZeros(List<Integer> nf) {
    while (!nf.isEmpty() && nf.getLast() == 0) {
      nf.removeLast();
    }
  }

  // | The normal form is the variable-count vector (the multiset of variables); it is non-empty
  //   because every semigroup word has at least one variable.
  @Override
  protected @NotNull List<Integer> normalize(EquationTerm term) {
    List<Integer> result = new ArrayList<>();
    normalize(term, result);
    trimZeros(result);
    return result;
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<Integer> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    List<ConcreteExpression> arguments = new ArrayList<>();
    for (int i = 0; i < nf.size(); i++) {
      int count = nf.get(i);
      ConcreteExpression arg = count == 0 ? null : factory.core(values.getValue(i).computeTyped());
      for (int j = 0; j < count; j++) {
        arguments.add(arg);
      }
    }

    ConcreteExpression result = arguments.getLast();
    for (int i = arguments.size() - 2; i >= 0; i--) {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getMul().getRef()));
      if (instance != null) {
        builder.app(factory.core(instance), false);
      }
      result = builder.app(arguments.get(i)).app(result).build();
    }
    return result;
  }

  @Override
  protected @NotNull NFPrettyPrinter<List<Integer>> getNFPrettyPrinter() {
    return new CommutativeGroupNFPrettyPrinter(true);
  }
}
