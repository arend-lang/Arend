package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteAppExpression;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.MonoidNFPrettyPrinter;
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

import java.util.*;

public abstract class NonCommutativeMonoidEquationMeta extends BaseMonoidEquationMeta<List<Integer>> {
  @Dependency(name = "SubstSolverModel.apply-axiom")  ArendRef applyAxiom;
  @Dependency(name = "List.::")                       ArendRef cons;
  @Dependency(name = "List.nil")                      ArendRef nil;

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

  protected static class MyHint<NF> extends Hint<NF> {
    final Set<Integer> positions;

    MyHint(Set<Integer> positions, TypedExpression typed, EquationTerm left, EquationTerm right, NF leftNF, NF rightNF, ConcreteExpression originalExpression) {
      super(typed, left, right, leftNF, rightNF, originalExpression);
      this.positions = positions;
    }
  }

  private @Nullable Set<Integer> getPositions(ConcreteExpression expression, ErrorReporter errorReporter) {
    Set<Integer> result = new HashSet<>();
    List<ConcreteExpression> zeros = new ArrayList<>();
    for (ConcreteExpression arg : Utils.getArgumentList(expression)) {
      Integer position = Utils.getNumber(arg, null, true);
      if (position == null) return null;
      if (position == 0) {
        zeros.add(arg);
      } else {
        result.add(position);
      }
    }
    for (ConcreteExpression arg : zeros) {
      errorReporter.report(new TypecheckingError(GeneralError.Level.WARNING, "Position 0 is ignored", arg));
    }
    return result;
  }

  @Override
  protected @Nullable Hint<List<Integer>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    Set<Integer> positions;
    if (hint instanceof ConcreteAppExpression appExpr && appExpr.getArguments().getFirst().isExplicit()) {
      positions = getPositions(appExpr.getFunction(), typechecker.getErrorReporter());
      if (positions != null) {
        hint = appExpr.getArguments().getFirst().getExpression();
        if (appExpr.getArguments().size() > 1) {
          hint = typechecker.getFactory().withData(hint).app(hint, appExpr.getArguments().subList(1, appExpr.getArguments().size()));
        }
      }
    } else {
      positions = null;
    }

    Hint<List<Integer>> result = super.parseHint(hint, hintType, operations, values, typechecker);
    return result == null ? null : new MyHint<>(positions, result.typed, result.left, result.right, result.leftNF, result.rightNF, result.originalExpression);
  }

  @Override
  protected void checkHint(@NotNull Hint<List<Integer>> hint, int[] position, @NotNull ErrorReporter errorReporter) {
    if (!(((MyHint<List<Integer>>) hint).positions != null && hint.originalExpression instanceof ConcreteAppExpression appExpr)) return;
    for (ConcreteExpression arg : Utils.getArgumentList(appExpr.getFunction())) {
      Integer pos = Utils.getNumber(arg, null, true);
      if (pos != null && pos > position[0]) {
        errorReporter.report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Maximal available position is " + position[0], arg));
      }
    }
  }

  @Override
  protected @Nullable HintResult<List<Integer>> applyHint(@NotNull Hint<List<Integer>> hint, @NotNull List<Integer> current, int[] position, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull ConcreteFactory factory) {
    if (hint.leftNF.equals(current)) {
      position[0]++;
      return super.applyHint(hint, current, position, solverRef, envRef, factory);
    }

    Pair<List<Integer>,List<Integer>> pair = abstractNF((MyHint<List<Integer>>) hint, current, position);
    return pair == null ? null : new HintResult<>(factory.appBuilder(factory.ref(applyAxiom))
        .app(factory.ref(solverRef.get()), false)
        .app(factory.ref(envRef.get()))
        .app(hint.left.generateReflectedTerm(factory, getVarTerm()))
        .app(hint.right.generateReflectedTerm(factory, getVarTerm()))
        .app(factory.core(hint.typed))
        .app(nfToConcrete(pair.proj1, factory))
        .build(), pair.proj2);
  }

  private @Nullable Pair<List<Integer>, List<Integer>> abstractNF(@NotNull MyHint<List<Integer>> hint, @NotNull List<Integer> nf, int[] position) {
    if (hint.leftNF.isEmpty()) return null;

    boolean found = false;
    List<Integer> abstracted = new ArrayList<>();
    List<Integer> right = new ArrayList<>();
    for (int i = 0; i < nf.size();) {
      int n = i + hint.leftNF.size();
      if (n <= nf.size() && nf.subList(i, n).equals(hint.leftNF) && (hint.positions == null || hint.positions.contains(++position[0]))) {
        abstracted.add(0);
        right.addAll(hint.rightNF);
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

  private ConcreteExpression nfToConcrete(List<Integer> values, ConcreteFactory factory) {
    ConcreteExpression result = factory.ref(nil);
    for (int i = values.size() - 1; i >= 0; i--) {
      result = factory.app(factory.ref(cons), true, factory.number(values.get(i)), result);
    }
    return result;
  }
}
