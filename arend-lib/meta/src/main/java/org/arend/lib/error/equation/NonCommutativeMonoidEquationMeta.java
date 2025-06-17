package org.arend.lib.error.equation;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.meta.equationNew.BaseMonoidEquationMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class NonCommutativeMonoidEquationMeta extends BaseMonoidEquationMeta {
  @Dependency(name = "SolverModel.apply-axiom") ArendRef applyAxiom;
  @Dependency(name = "List.::")                 ArendRef cons;
  @Dependency(name = "List.nil")                ArendRef nil;

  protected NonCommutativeMonoidEquationMeta() {
    super(false);
  }

  @Override
  protected @Nullable ArendRef getApplyAxiom() {
    return applyAxiom;
  }

  @Override
  protected @Nullable Pair<List<Integer>, List<Integer>> abstractNF(@NotNull Hint<List<Integer>> hint, @NotNull List<Integer> nf) {
    if (hint.leftNF().isEmpty()) return null;

    boolean found = false;
    List<Integer> abstracted = new ArrayList<>();
    List<Integer> right = new ArrayList<>();
    for (int i = 0; i < nf.size();) {
      int n = i + hint.leftNF().size();
      if (n <= nf.size() && nf.subList(i, n).equals(hint.leftNF())) {
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
