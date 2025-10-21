package org.arend.lib.meta.equation;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.core.definition.*;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.StdExtension;
import org.arend.lib.util.Maybe;
import org.arend.lib.error.TypeError;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EquationMeta extends BaseEquationMeta {
  public final StdExtension ext;

  @Dependency                                   CoreClassDefinition Equiv;
  @Dependency(name = "Map.A")                   CoreClassField equivLeft;
  @Dependency(name = "Map.B")                   CoreClassField equivRight;
  @Dependency                                   ArendRef idEquiv;
  @Dependency                                   ArendRef transEquiv;

  public record TransitivityInstanceCache(CoreFunctionDefinition instance, CoreClassField relationField) {}

  public final Map<CoreDefinition, TransitivityInstanceCache> transitivityInstanceCache = new HashMap<>();

  public EquationMeta(StdExtension ext) {
    this.ext = ext;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ErrorReporter errorReporter = typechecker.getErrorReporter();
    ConcreteReferenceExpression refExpr = contextData.getReferenceExpression();
    ConcreteFactory factory = contextData.getFactory();

    // values will contain ConcreteExpression (which correspond to implicit arguments) and TypedExpression (which correspond to explicit arguments).
    List<Object> values = new ArrayList<>();

    EquationSolver solver = null;
    int argIndex = 0;
    List<? extends ConcreteArgument> arguments = contextData.getArguments();
    if (!arguments.isEmpty() && !arguments.getFirst().isExplicit()) {
      ConcreteExpression arg = arguments.getFirst().getExpression();
      if (arg instanceof ConcreteUniverseExpression) {
        argIndex = 1;
        solver = new EqualitySolver(this, typechecker, factory, refExpr, false);
      } else if (arg instanceof ConcreteReferenceExpression) {
        CoreDefinition def = typechecker.getCoreDefinition(((ConcreteReferenceExpression) arg).getReferent());
        if (def instanceof CoreClassDefinition classDef) {
          if ((classDef.isSubClassOf(Monoid) || classDef.isSubClassOf(AddMonoid) || classDef.isSubClassOf(TopMeetSemilattice))) {
            argIndex = 1;
            solver = new EqualitySolver(this, typechecker, factory, refExpr, classDef);
          }
        }
      }
    }

    if (contextData.getExpectedType() != null) {
      CoreExpression type = contextData.getExpectedType().normalize(NormalizationMode.WHNF);
      final List<EquationSolver> solvers = solver != null ? Collections.singletonList(solver) : Arrays.asList(new EqualitySolver(this, typechecker, factory, refExpr), new EquivSolver(this, typechecker, factory), new TransitivitySolver(this, typechecker, factory, refExpr));
      solver = null;
      for (EquationSolver eqSolver : solvers) {
        if (eqSolver.isApplicable(type)) {
          solver = eqSolver;
          break;
        }
      }
      if (solver == null) {
        errorReporter.report(new TypeError(typechecker.getExpressionPrettifier(), "Unrecognized type", type, refExpr));
        return null;
      }
    } else if (solver == null) {
      solver = new EqualitySolver(this, typechecker, factory, refExpr);
    }

    for (; argIndex < arguments.size(); argIndex++) {
      ConcreteArgument argument = arguments.get(argIndex);
      if (argument.isExplicit()) {
        TypedExpression value = typechecker.typecheck(argument.getExpression(), null);
        if (value == null) {
          return null;
        }
        values.add(value);
      } else {
        values.add(argument.getExpression());
      }
    }

    CoreExpression leftExpr = solver.getLeftValue();
    if (leftExpr != null && (values.isEmpty() || !(values.getFirst() instanceof TypedExpression) || !Utils.safeCompare(typechecker, leftExpr, ((TypedExpression) values.getFirst()).getExpression(), CMP.EQ, refExpr, false, true, true))) {
      values.addFirst(leftExpr.computeTyped());
    }
    CoreExpression rightExpr = solver.getRightValue();
    if (rightExpr != null && (values.isEmpty() || !(values.getLast() instanceof TypedExpression) || !Utils.safeCompare(typechecker, rightExpr, ((TypedExpression) values.getLast()).getExpression(), CMP.EQ, refExpr, false, true, true))) {
      values.add(rightExpr.computeTyped());
    }

    // If values.size() <= 1, then we don't have expected type
    if (values.isEmpty()) {
      errorReporter.report(new TypecheckingError("Cannot infer type of the expression", refExpr));
      return null;
    }

    // If values.size() == 1, we either return the implicit argument or the trivial answer on the explicit argument
    if (values.size() == 1) {
      return values.getFirst() instanceof ConcreteExpression
        ? typechecker.typecheck((ConcreteExpression) values.getFirst(), null)
        : solver.getTrivialResult((TypedExpression) values.getFirst());
    }

    boolean hasMissingProofs = false;
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i) instanceof TypedExpression && i + 1 < values.size() && values.get(i + 1) instanceof TypedExpression) {
        hasMissingProofs = true;
      } else if (values.get(i) instanceof ConcreteExpression expr && solver.isHint(expr instanceof ConcreteGoalExpression ? ((ConcreteGoalExpression) expr).getExpression() : expr)) {
        if (i > 0 && values.get(i - 1) instanceof TypedExpression && i + 1 < values.size() && values.get(i + 1) instanceof TypedExpression) {
          hasMissingProofs = true;
        } else {
          errorReporter.report(new TypecheckingError("Hints must be between explicit arguments", (ConcreteExpression) values.get(i)));
          values.remove(i--);
        }
      }
    }

    if (hasMissingProofs) {
      if (solver instanceof EqualitySolver && contextData.getExpectedType() == null) {
        for (Object value : values) {
          if (value instanceof TypedExpression) {
            ((EqualitySolver) solver).setValuesType(((TypedExpression) value).getType());
            break;
          }
        }
      }

      if (!solver.initializeSolver()) {
        errorReporter.report(new TypecheckingError("Cannot infer missing proofs", refExpr));
        return null;
      }
    }

    boolean ok = true;
    List<ConcreteExpression> equalities = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      Object value = values.get(i);
      if (value instanceof ConcreteExpression && solver.isHint((ConcreteExpression) value) || value instanceof TypedExpression && i + 1 < values.size() && values.get(i + 1) instanceof TypedExpression) {
        ConcreteGoalExpression goalExpr = value instanceof ConcreteGoalExpression ? (ConcreteGoalExpression) value : null;
        List<GeneralError> errors = goalExpr != null ? new ArrayList<>() : null;
        ConcreteExpression result = solver.solve(
            goalExpr != null ? goalExpr.getExpression() : value instanceof ConcreteExpression ? (ConcreteExpression) value : null,
            (TypedExpression) values.get(value instanceof ConcreteExpression ? i - 1 : i),
            (TypedExpression) values.get(i + 1),
            errors != null ? new ListErrorReporter(errors) : errorReporter);
        if (result == null && goalExpr == null) {
          ok = false;
          continue;
        }
        equalities.add(goalExpr == null ? result : factory.withData(goalExpr.getData()).goal(goalExpr.getName(), result, null, Objects.requireNonNull(errors)));
      } else if (value instanceof ConcreteExpression) {
        Maybe<CoreExpression> eqType = solver.getEqType(
            i > 0 && values.get(i - 1) instanceof TypedExpression ? (TypedExpression) values.get(i - 1) : null,
            i < values.size() - 1 && values.get(i + 1) instanceof TypedExpression ? (TypedExpression) values.get(i + 1) : null);

        TypedExpression result = eqType == null ? null : typechecker.typecheck((ConcreteExpression) value, eqType.just);
        if (result == null) {
          ok = false;
        } else {
          equalities.add(factory.core(null, result));
        }
      }
    }
    if (!ok) {
      return null;
    }

    ConcreteExpression result = equalities.getLast();
    for (int i = equalities.size() - 2; i >= 0; i--) {
      result = solver.combineResults(equalities.get(i), result);
    }
    return hasMissingProofs ? solver.finalize(result) : typechecker.typecheck(result, null);
  }
}
