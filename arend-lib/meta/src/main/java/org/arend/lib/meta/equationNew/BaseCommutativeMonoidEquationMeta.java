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
import org.arend.ext.typechecking.meta.Dependency;
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

  protected static class MyHint<NF> extends Hint<NF> {
    final boolean applyToLeft;
    final boolean applyToRight;
    final int count;

    MyHint(boolean applyToLeft, boolean fromRight, int count, TypedExpression typed, EquationTerm left, EquationTerm right, NF leftNF, NF rightNF, ConcreteExpression originalExpression) {
      super(typed, left, right, leftNF, rightNF, originalExpression);
      this.applyToLeft = applyToLeft;
      this.applyToRight = fromRight;
      this.count = count;
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
    return result == null ? null : new MyHint<>(number == null || number >= 0, number == null || number < 0, number == null ? 1 : Math.abs(number), result.typed, result.left, result.right, result.leftNF, result.rightNF, result.originalExpression);
  }

  protected abstract @NotNull ArendRef getApplyAxiom();

  @Override
  protected @Nullable HintResult<List<Integer>> applyHint(@NotNull Hint<List<Integer>> hint, @NotNull List<Integer> current, int[] position, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull ConcreteFactory factory) {
    position[0]++;
    MyHint<List<Integer>> myHint = (MyHint<List<Integer>>) hint;
    if (position[0] == 1 && !myHint.applyToLeft || position[0] == 2 && !myHint.applyToRight) {
      return null;
    }
    if (hint.leftNF.equals(current)) {
      return super.applyHint(hint, current, position, solverRef, envRef, factory);
    }

    Pair<List<Integer>,List<Integer>> pair = abstractNF(myHint, current);
    return pair == null ? null : new HintResult<>(factory.appBuilder(factory.ref(getApplyAxiom()))
        .app(factory.ref(envRef.get()))
        .app(hint.left.generateReflectedTerm(factory, getVarTerm()))
        .app(hint.right.generateReflectedTerm(factory, getVarTerm()))
        .app(factory.core(hint.typed))
        .app(factory.number(myHint.count))
        .app(nfToConcrete(pair.proj1, factory))
        .build(), pair.proj2);
  }

  private static int getCount(List<Integer> nf, int index) {
    return index < nf.size() ? nf.get(index) : 0;
  }

  private @Nullable Pair<List<Integer>, List<Integer>> abstractNF(@NotNull MyHint<List<Integer>> hint, @NotNull List<Integer> nf) {
    List<Integer> addition = new ArrayList<>();
    List<Integer> newNF = new ArrayList<>();

    int n = Math.max(nf.size(), Math.max(hint.leftNF.size(), hint.rightNF.size()));
    for (int i = 0; i < n; i++) {
      int d = getCount(nf, i) - hint.count * getCount(hint.leftNF, i);
      if (d >= 0) {
        addition.add(d);
        newNF.add(d + hint.count * getCount(hint.rightNF, i));
      } else {
        return null;
      }
    }

    return new Pair<>(addition, newNF);
  }

  private ConcreteExpression nfToConcrete(List<Integer> values, ConcreteFactory factory) {
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
