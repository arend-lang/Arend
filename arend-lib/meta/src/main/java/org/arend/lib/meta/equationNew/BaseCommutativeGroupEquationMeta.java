package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteAppExpression;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.CommutativeGroupNFPrettyPrinter;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseCommutativeGroupEquationMeta extends BaseGroupEquationMeta<List<Integer>> {
  protected abstract @NotNull ArendRef getApplyAxiom();

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

  private void trimZeros(List<Integer> nf) {
    while (!nf.isEmpty() && nf.getLast() == 0) {
      nf.removeLast();
    }
  }

  @Override
  protected @NotNull List<Integer> normalize(EquationTerm term) {
    List<Integer> result = new ArrayList<>();
    normalize(term, true, result);
    trimZeros(result);
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

  protected static class MyHint<NF> extends Hint<NF> {
    final int coefficient;

    MyHint(int coefficient, TypedExpression typed, EquationTerm left, EquationTerm right, NF leftNF, NF rightNF, ConcreteExpression originalExpression) {
      super(typed, left, right, leftNF, rightNF, originalExpression);
      this.coefficient = coefficient;
    }
  }

  @Override
  protected @Nullable MyHint<List<Integer>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    Integer number;
    if (hint instanceof ConcreteAppExpression appExpr && appExpr.getArguments().getFirst().isExplicit()) {
      number = Utils.getNumber(appExpr.getFunction(), null, false);
      if (number != null) {
        if (number == 0) {
          typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is redundant", hint));
        }
        hint = appExpr.getArguments().getFirst().getExpression();
        if (appExpr.getArguments().size() > 1) {
          hint = typechecker.getFactory().withData(hint).app(hint, appExpr.getArguments().subList(1, appExpr.getArguments().size()));
        }
      }
    } else {
      number = null;
    }

    Hint<List<Integer>> result = super.parseHint(hint, hintType, operations, values, typechecker);
    return result == null ? null : new MyHint<>(number == null ? 1 : number, result.typed, result.left, result.right, result.leftNF, result.rightNF, result.originalExpression);
  }

  private void addNF(List<Integer> nf1, int coefficient, List<Integer> nf2) {
    int n = nf2.size();
    while (nf1.size() < n) {
      nf1.add(0);
    }
    for (int i = 0; i < n; i++) {
      nf1.set(i, nf1.get(i) + coefficient * nf2.get(i));
    }
  }

  private ConcreteExpression nfToConcrete(List<Integer> list, int size, ConcreteFactory factory) {
    List<ConcreteExpression> concreteList = new ArrayList<>(size);
    for (Integer number : list) {
      concreteList.add(factory.number(number));
    }
    while (concreteList.size() < size) {
      concreteList.add(factory.number(0));
    }
    return Utils.makeArray(concreteList, factory);
  }

  @Override
  protected @Nullable Pair<HintResult<List<Integer>>, HintResult<List<Integer>>> applyHints(@NotNull List<Hint<List<Integer>>> hints, @NotNull List<Integer> left, @NotNull List<Integer> right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    List<Integer> newNF = new ArrayList<>(left);
    addNF(newNF, -1, right);

    List<ConcreteExpression> axioms = new ArrayList<>();
    for (Hint<List<Integer>> hint : hints) {
      int c = ((MyHint<List<Integer>>) hint).coefficient;
      addNF(newNF, -c, hint.leftNF);
      addNF(newNF, c, hint.rightNF);
      axioms.add(factory.tuple(factory.number(c), hint.left.generateReflectedTerm(factory, getVarTerm()), hint.right.generateReflectedTerm(factory, getVarTerm()), factory.core(hint.typed)));
    }

    trimZeros(newNF);
    return new Pair<>(new HintResult<>(factory.app(factory.ref(getApplyAxiom()), true, factory.ref(envRef.get()), Utils.makeArray(axioms, factory), nfToConcrete(newNF, values.getValues().size(), factory)), newNF), new HintResult<>(null, Collections.emptyList()));
  }
}
