package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.error.equation.CommutativeGroupNFPrettyPrinter;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCommutativeGroupEquationMeta extends BaseGroupEquationMeta<List<Integer>> {
  private void normalize(EquationTerm term, boolean isPositive, List<Integer> result) {
    switch (term) {
      case OpTerm opTerm -> {
        if (opTerm.operation().reflectionRef().equals(inverseTerm)) {
          isPositive = !isPositive;
        }
        for (EquationTerm argument : opTerm.arguments()) {
          normalize(argument, isPositive, result);
        }
      }
      case VarTerm(int index) -> {
        while (index >= result.size()) {
          result.add(0);
        }
        result.set(index, result.get(index) + (isPositive ? 1 : -1));
      }
    }
  }

  @Override
  protected @NotNull List<Integer> normalize(EquationTerm term) {
    List<Integer> result = new ArrayList<>();
    normalize(term, true, result);
    return result;
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<Integer> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    List<ConcreteExpression> arguments = new ArrayList<>();
    for (int i = 0; i < nf.size(); i++) {
      int count = nf.get(i);
      if (count == 0) continue;
      ConcreteExpression arg = indexToConcrete(count > 0, i, values, instance, factory);
      int n = Math.abs(count);
      for (int j = 0; j < n; j++) {
        arguments.add(arg);
      }
    }

    if (arguments.isEmpty()) {
      return factory.ref(getIde().getRef());
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
    return new CommutativeGroupNFPrettyPrinter(isMultiplicative());
  }

  private static int getCount(List<Integer> nf, int index) {
    return index < nf.size() ? nf.get(index) : 0;
  }

  @Override
  protected @NotNull ConcreteExpression getProofType(@NotNull List<Integer> left, @NotNull List<Integer> right, @NotNull Values<CoreExpression> values, @Nullable TypedExpression instance, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    List<Integer> diff = new ArrayList<>();
    int n = Math.max(left.size(), right.size());
    for (int i = 0; i < n; i++) {
      diff.add(getCount(left, i) - getCount(right, i));
    }
    return factory.app(factory.ref(typechecker.getPrelude().getEqualityRef()), true, nfToConcreteTerm(diff, values, instance, factory), factory.ref(getIde().getRef()));
  }
}
