package org.arend.core.sort;

import org.arend.core.context.binding.Binding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.ClassField;
import org.arend.core.expr.*;
import org.arend.ext.core.ops.NormalizationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

// TODO[sorts]: Optimize representation
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
    return getSortExpression(expression, getVariables(parameters), Collections.emptyMap(), null);
  }

  static SortExpression getSortExpression(Expression expression, Map<Binding, Integer> variables, Map<ClassField, Integer> fields, Binding thisBinding) {
    return switch (expression) {
      case PiExpression piExpr -> {
        SortExpression expr1 = getSortExpression(piExpr.getParameters().getTypeExpr(), variables, fields, thisBinding);
        SortExpression expr2 = getSortExpression(piExpr.getCodomain(), variables, fields, thisBinding);
        yield expr1 == null || expr2 == null ? null : new Pi(expr1, expr2);
      }
      case SigmaExpression sigmaExpr -> {
        SortExpression result = new Const(Sort.PROP);
        for (DependentLink param = sigmaExpr.getParameters(); param.hasNext(); param = param.getNext()) {
          param = param.getNextTyped(null);
          SortExpression sortExpr = getSortExpression(param.getTypeExpr(), variables, fields, thisBinding);
          if (sortExpr == null) yield null;
          result = new Max(result, sortExpr);
        }
        yield result;
      }
      case DefCallExpression defCall when !(expression instanceof FieldCallExpression) -> {
        SortExpression result = defCall.getDefinition().getSortExpression();
        if (result == null) {
          Sort sort = defCall.getSortOfType();
          yield sort == null || sort.getPLevel().isInfinity() ? null : new Const(sort);
        }
        List<Expression> arguments;
        if (defCall instanceof ClassCallExpression classCall) {
          arguments = new ArrayList<>();
          for (ClassField field : classCall.getDefinition().getSortFields()) {
            AbsExpression impl = classCall.getAbsImplementation(field);
            arguments.add(impl == null ? null : impl.getExpression());
          }
        } else {
          arguments = defCall.getDefCallArguments();
        }
        yield result.subst(index -> {
          if (index >= arguments.size()) return null;
          Expression arg = arguments.get(index);
          while (arg instanceof LamExpression lamExpr) {
            arg = lamExpr.getBody();
          }
          return getSortExpression(arg, variables, fields, thisBinding);
        });
      }
      default -> {
        Expression fun = expression instanceof AppExpression appExpr ? appExpr.getFunction() : expression;
        Integer index;
        if (fun instanceof ReferenceExpression refExpr) {
          index = variables.get(refExpr.getBinding());
        } else if (fun instanceof FieldCallExpression fieldCall && fieldCall.getArgument() instanceof ReferenceExpression refExpr && refExpr.getBinding().equals(thisBinding)) {
          index = fields.get(fieldCall.getDefinition());
        } else {
          index = null;
        }
        if (index != null) {
          yield new Var(index);
        }

        Sort sort = expression.getSortOfType();
        yield sort == null || sort.getPLevel().isInfinity() ? null : new Const(sort);
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
      if (hLevel.isInfinity() || hLevel.isCat()) return result;
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
      if (index >= arguments.size()) return null;
      Expression arg = arguments.get(index);
      if (arg == null) return null;
      Expression type = arg.getType();
      if (type == null) return null;
      type = type.normalize(NormalizationMode.WHNF);
      while (type instanceof PiExpression piExpression) {
        type = piExpression.getCodomain().normalize(NormalizationMode.WHNF);
      }
      return type instanceof UniverseExpression universe ? universe.getSort() : null;
    }

    @Override
    public @Nullable SortExpression subst(@NotNull Function<Integer, SortExpression> substitution) {
      return substitution.apply(index);
    }
  }
}

