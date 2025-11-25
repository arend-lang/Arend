package org.arend.core.sort;

import org.arend.core.context.binding.Binding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.expr.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public sealed interface SortExpression permits SortExpression.Const, SortExpression.Equality, SortExpression.Max, SortExpression.Pi, SortExpression.Var {
  @Nullable Sort computeSort(@NotNull List<? extends Expression> arguments);
  @Nullable SortExpression subst(@NotNull Function<Integer, SortExpression> substitution);

  static Map<Binding, Integer> getVariables(DependentLink parameters) {
    Map<Binding, Integer> variables = new HashMap<>();
    for (int index = 0; parameters.hasNext(); parameters = parameters.getNext(), index++) {
      if (parameters.getTypeExpr().isInfinityLevel()) {
        variables.put(parameters, index);
      }
    }
    return variables;
  }

  static SortExpression getSortExpression(Expression expression, DependentLink parameters) {
    return getSortExpression(expression, getVariables(parameters));
  }

  static SortExpression getSortExpression(Expression expression, Map<Binding, Integer> variables) {
    return switch (expression) {
      case PiExpression piExpr -> {
        SortExpression expr1 = getSortExpression(piExpr.getParameters().getTypeExpr(), variables);
        SortExpression expr2 = getSortExpression(piExpr.getCodomain(), variables);
        yield expr1 == null || expr2 == null ? null : new SortExpression.Pi(expr1, expr2);
      }
      case SigmaExpression sigmaExpr -> {
        SortExpression result = new SortExpression.Const(Sort.PROP);
        for (DependentLink param = sigmaExpr.getParameters(); param.hasNext(); param = param.getNext()) {
          param = param.getNextTyped(null);
          SortExpression sortExpr = getSortExpression(param.getTypeExpr(), variables);
          if (sortExpr == null) yield null;
          result = new SortExpression.Max(result, sortExpr);
        }
        yield result;
      }
      case DefCallExpression defCall -> {
        SortExpression result = defCall.getDefinition().getSortExpression();
        if (result == null) {
          Sort sort = defCall.getSortOfType();
          yield sort == null || sort.getPLevel().isInfinity() ? null : new Const(sort);
        }
        yield result.subst(index -> {
          if (index >= defCall.getDefCallArguments().size()) return null;
          Expression arg = defCall.getDefCallArguments().get(index);
          while (arg instanceof LamExpression lamExpr) {
            arg = lamExpr.getBody();
          }
          return getSortExpression(arg, variables);
        });
      }
      default -> {
        Expression fun = expression instanceof AppExpression appExpr ? appExpr.getFunction() : expression;
        if (fun instanceof ReferenceExpression refExpr) {
          Integer index = variables.get(refExpr.getBinding());
          if (index != null) {
            yield new SortExpression.Var(index);
          }
        }
        Sort sort = expression.getSortOfType();
        yield sort == null || sort.getPLevel().isInfinity() ? null : new SortExpression.Const(sort);
      }
    };
  }

  record Const(Sort sort) implements SortExpression {
    @Override
    public @NotNull Sort computeSort(@NotNull List<? extends Expression> arguments) {
      return sort;
    }

    @Override
    public @NotNull SortExpression subst(@NotNull Function<Integer, SortExpression> substitution) {
      return this;
    }
  }

  record Equality(SortExpression sort) implements SortExpression {
    @Override
    public @Nullable Sort computeSort(@NotNull List<? extends Expression> arguments) {
      Sort result = sort.computeSort(arguments);
      if (result == null) return null;
      Level hLevel = result.getHLevel();
      if (hLevel.isInfinity()) return result;
      if (hLevel.isClosed() && hLevel.getConstant() == 0) return Sort.PROP;
      return new Sort(result.getPLevel(), new Level(hLevel.getVar(), hLevel.getConstant() > 0 ? hLevel.getConstant() - 1 : hLevel.getConstant(), hLevel.getMaxConstant() > 0 ? hLevel.getMaxConstant() - 1 : hLevel.getMaxConstant()));
    }

    @Override
    public @Nullable SortExpression subst(@NotNull Function<Integer, SortExpression> substitution) {
      SortExpression result = sort.subst(substitution);
      return result == null ? null : new Equality(result);
    }
  }

  record Max(SortExpression sort1, SortExpression sort2) implements SortExpression {
    @Override
    public @Nullable Sort computeSort(@NotNull List<? extends Expression> arguments) {
      Sort result1 = sort1.computeSort(arguments);
      Sort result2 = sort2.computeSort(arguments);
      return result1 == null || result2 == null ? null : result1.max(result2);
    }

    @Override
    public @Nullable SortExpression subst(@NotNull Function<Integer, SortExpression> substitution) {
      SortExpression result1 = sort1.subst(substitution);
      SortExpression result2 = sort2.subst(substitution);
      return result1 == null || result2 == null ? null : new Max(result1, result2);
    }
  }

  record Pi(SortExpression sort1, SortExpression sort2) implements SortExpression {
    @Override
    public @Nullable Sort computeSort(@NotNull List<? extends Expression> arguments) {
      Sort result1 = sort1.computeSort(arguments);
      Sort result2 = sort2.computeSort(arguments);
      return result1 == null || result2 == null ? null : result2.isProp() ? result2 : new Sort(result1.getPLevel().max(result2.getPLevel()), result2.getHLevel());
    }

    @Override
    public @Nullable SortExpression subst(@NotNull Function<Integer, SortExpression> substitution) {
      SortExpression result1 = sort1.subst(substitution);
      SortExpression result2 = sort2.subst(substitution);
      return result1 == null || result2 == null ? null : new Pi(result1, result2);
    }
  }

  record Var(int index) implements SortExpression {
    @Override
    public @Nullable Sort computeSort(@NotNull List<? extends Expression> arguments) {
      return index >= arguments.size() ? null : arguments.get(index).getSortOfType();
    }

    @Override
    public @Nullable SortExpression subst(@NotNull Function<Integer, SortExpression> substitution) {
      return substitution.apply(index);
    }
  }
}

