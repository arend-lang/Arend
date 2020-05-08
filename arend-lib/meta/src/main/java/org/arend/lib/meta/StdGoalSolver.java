package org.arend.lib.meta;

import org.arend.ext.concrete.ConcreteClassElement;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.expr.ConcreteAppExpression;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteGoalExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.definition.CoreConstructor;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.typechecking.*;
import org.arend.ext.ui.ArendQuery;
import org.arend.ext.ui.ArendSession;
import org.arend.ext.ui.ArendUI;
import org.arend.ext.variable.VariableRenamer;
import org.arend.lib.StdExtension;
import org.arend.lib.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StdGoalSolver implements GoalSolver {
  private final StdExtension ext;

  public StdGoalSolver(StdExtension ext) {
    this.ext = ext;
  }

  @Override
  public @NotNull CheckGoalResult checkGoal(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteGoalExpression goalExpression, @Nullable CoreExpression expectedType) {
    ConcreteExpression expr = goalExpression.getExpression();
    if (expr == null) {
      return new CheckGoalResult(null, null);
    }

    if (!(expectedType != null && (expr instanceof ConcreteReferenceExpression || expr instanceof ConcreteAppExpression))) {
      return new CheckGoalResult(expr, typechecker.typecheck(expr, expectedType));
    }

    int expectedParams = Utils.numberOfExplicitPiParameters(expectedType);

    Object exprData = expr.getData();
    ErrorReporter errorReporter = typechecker.getErrorReporter();
    ConcreteExpression extExpr = Utils.addArguments(expr, ext, expectedParams, true);
    TypedExpression result = typechecker.withErrorReporter(error -> {
      if (!(error.level == GeneralError.Level.GOAL && error.getCause() == exprData)) {
        errorReporter.report(error);
      }
    }, tc -> tc.typecheck(extExpr, null));

    return new CheckGoalResult(result == null ? extExpr : Utils.addArguments(extExpr, ext.factory.withData(exprData), Utils.numberOfExplicitPiParameters(result.getType()) - expectedParams, true), result);
  }

  @Override
  public boolean willTrySolve(@NotNull ConcreteGoalExpression goalExpression, @Nullable CoreExpression expectedType) {
    return expectedType != null && goalExpression.getExpression() == null;
  }

  @Override
  public void trySolve(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteGoalExpression goalExpression, @Nullable CoreExpression expectedType, @NotNull ArendUI ui, @NotNull Consumer<@Nullable ConcreteExpression> callback) {
    CoreExpression type = expectedType == null ? null : expectedType.getUnderlyingExpression().normalize(NormalizationMode.WHNF);
    ConcreteFactory factory = ext.factory.withData(goalExpression.getData());

    ConcreteExpression result;
    if (type instanceof CoreSigmaExpression) {
      List<ConcreteExpression> goals = new ArrayList<>();
      for (CoreParameter param = ((CoreSigmaExpression) type).getParameters(); param.hasNext(); param = param.getNext()) {
        goals.add(factory.goal());
      }
      result = factory.tuple(goals);
    } else if (type instanceof CorePiExpression) {
      List<CoreParameter> params = new ArrayList<>();
      type.getPiParameters(params);
      List<ConcreteParameter> cParams = new ArrayList<>(params.size());
      VariableRenamer renamer = ext.renamerFactory.variableRenamer();
      List<CoreBinding> freeVars = typechecker.getFreeBindingsList();
      for (CoreParameter param : params) {
        cParams.add(factory.param(factory.local(renamer.generateFreshName(param.getBinding(), freeVars))));
        freeVars.add(param.getBinding());
      }
      result = factory.lam(cParams, factory.goal());
    } else if (type instanceof CoreClassCallExpression) {
      CoreClassCallExpression classCall = (CoreClassCallExpression) type;
      List<ConcreteClassElement> classElements = new ArrayList<>();
      for (CoreClassField field : classCall.getDefinition().getFields()) {
        if (!classCall.isImplemented(field)) {
          classElements.add(factory.implementation(field.getRef(), factory.goal()));
        }
      }
      result = factory.newExpr(factory.classExt(factory.ref(classCall.getDefinition().getRef()), classElements));
    } else if (type instanceof CoreDataCallExpression) {
      CoreDataCallExpression dataCall = (CoreDataCallExpression) type;
      if (dataCall.getDefinition() == ext.prelude.getPath()) {
        CoreFunCallExpression eq = dataCall.toEquality();
        if (eq != null && eq.getDefCallArguments().get(1).compare(eq.getDefCallArguments().get(2), CMP.EQ)) {
          callback.accept(factory.ref(ext.prelude.getIdp().getRef()));
          return;
        }
      }

      List<CoreConstructor> constructors = dataCall.computeMatchedConstructors();
      if (constructors == null || constructors.isEmpty()) {
        result = null;
      } else if (constructors.size() == 1) {
        result = factory.ref(constructors.get(0).getRef());
      } else {
        ArendSession session = ext.ui.newSession();
        session.setDescription("Goal");
        ArendQuery<CoreConstructor> query = session.listQuery("Choose constructor", constructors, null);
        session.setCallback(ok -> {
          if (ok) {
            CoreConstructor constructor = query.getResult();
            if (constructor != null) {
              callback.accept(Utils.addArguments(factory.ref(constructor.getRef()), factory, Utils.numberOfExplicitParameters(constructor.getParameters()), true));
            }
          }
        });
        session.startSession();
        return;
      }
    } else {
      result = null;
    }

    callback.accept(result);
  }
}
