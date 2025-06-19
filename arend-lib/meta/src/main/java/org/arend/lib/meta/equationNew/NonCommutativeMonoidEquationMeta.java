package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.MonoidNFPrettyPrinter;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class NonCommutativeMonoidEquationMeta extends BaseMonoidEquationMeta<List<Integer>> {
  @Dependency(name = "SubstSolverModel.apply-axiom")  ArendRef applyAxiom;
  @Dependency(name = "List.::")                       ArendRef cons;
  @Dependency(name = "List.nil")                      ArendRef nil;

  @Override
  protected @Nullable ArendRef getApplyAxiom() {
    return applyAxiom;
  }

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
    }
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<Integer> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    if (nf.isEmpty()) return factory.ref(getIde().getRef());
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
    return new MonoidNFPrettyPrinter(isMultiplicative());
  }

  @Override
  protected @Nullable Pair<List<Integer>, List<Integer>> abstractNF(@NotNull Hint<List<Integer>> hint, @NotNull List<Integer> nf, int[] position) {
    if (hint.leftNF().isEmpty()) return null;

    boolean found = false;
    List<Integer> abstracted = new ArrayList<>();
    List<Integer> right = new ArrayList<>();
    for (int i = 0; i < nf.size();) {
      int n = i + hint.leftNF().size();
      if (n <= nf.size() && nf.subList(i, n).equals(hint.leftNF()) && (hint.positions() == null || hint.positions().contains(++position[0]))) {
        abstracted.add(0);
        right.addAll(hint.rightNF());
        i = n;
        found = true;
      } else {
        abstracted.add(nf.get(i) + 1);
        right.add(nf.get(i));
        i++;
      }
    }

    return found ? new Pair<>(abstracted, right) : null;
  }

  @Override
  protected ConcreteExpression nfToConcrete(List<Integer> values, ConcreteFactory factory) {
    ConcreteExpression result = factory.ref(nil);
    for (int i = values.size() - 1; i >= 0; i--) {
      result = factory.app(factory.ref(cons), true, factory.number(values.get(i)), result);
    }
    return result;
  }
}
