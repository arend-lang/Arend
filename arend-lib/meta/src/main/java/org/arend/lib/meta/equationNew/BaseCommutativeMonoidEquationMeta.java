package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
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

public abstract class BaseCommutativeMonoidEquationMeta extends BaseMonoidEquationMeta<List<Integer>> {
  @Dependency(name = "List.::")                         ArendRef cons;
  @Dependency(name = "List.nil")                        ArendRef nil;

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
    }
  }

  @Override
  protected @NotNull List<Integer> normalize(EquationTerm term) {
    List<Integer> result = new ArrayList<>();
    normalize(term, result);
    return result;
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<Integer> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    List<ConcreteExpression> arguments = new ArrayList<>();
    for (int i = 0; i < nf.size(); i++) {
      int count = nf.get(i);
      if (count == 0) continue;
      ConcreteExpression arg = factory.core(values.getValue(i).computeTyped());
      for (int j = 0; j < count; j++) {
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

  @Override
  protected boolean needSolverForApplyAxiom() {
    return false;
  }

  private static int getCount(List<Integer> nf, int index) {
    return index < nf.size() ? nf.get(index) : 0;
  }

  @Override
  protected @Nullable Pair<List<Integer>, List<Integer>> abstractNF(@NotNull Hint<List<Integer>> hint, @NotNull List<Integer> nf, int[] position) {
    List<Integer> addition = new ArrayList<>();
    List<Integer> newNF = new ArrayList<>();

    int n = Math.max(nf.size(), Math.max(hint.leftNF().size(), hint.rightNF().size()));
    for (int i = 0; i < n; i++) {
      int d = getCount(nf, i) - getCount(hint.leftNF(), i);
      if (d >= 0) {
        addition.add(d);
        newNF.add(d + getCount(hint.rightNF(), i));
      } else {
        return null;
      }
    }

    return hint.positions() == null || hint.positions().contains(++position[0]) ? new Pair<>(addition, newNF) : null;
  }

  @Override
  protected ConcreteExpression nfToConcrete(List<Integer> values, ConcreteFactory factory) {
    ConcreteExpression result = factory.ref(nil);
    for (int i = values.size() - 1; i >= 0; i--) {
      int count = values.get(i);
      for (int j = 0; j < count; j++) {
        result = factory.app(factory.ref(cons), true, factory.number(i), result);
      }
    }
    return result;
  }
}
