package org.arend.typechecking.result;

import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.SingleDependentLink;
import org.arend.core.context.param.TypedDependentLink;
import org.arend.core.context.param.UnusedDirectedIntervalDependentLink;
import org.arend.core.context.param.UnusedIntervalDependentLink;
import org.arend.core.definition.CallableDefinition;
import org.arend.core.definition.Definition;
import org.arend.core.expr.*;
import org.arend.core.expr.visitor.CompareVisitor;
import org.arend.core.expr.visitor.GetTypeVisitor;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.sort.SortExpression;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.Levels;
import org.arend.ext.core.level.ConstLevel;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.util.StringUtils;
import org.arend.prelude.Prelude;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.error.local.PathEndpointMismatchError;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefCallResult implements TResult {
  private final Concrete.ReferenceExpression myDefCall;
  private final CallableDefinition myDefinition;
  private final Levels myLevels;
  private final List<Expression> myArguments;
  private List<DependentLink> myParameters;
  private Expression myResultType;
  private Boolean myForcedInfinite;

  private DefCallResult(Concrete.ReferenceExpression defCall, CallableDefinition definition, Levels levels, List<Expression> arguments, List<DependentLink> parameters, Expression resultType) {
    myDefCall = defCall;
    myDefinition = definition;
    myLevels = levels;
    myArguments = arguments;
    myParameters = parameters;
    myResultType = resultType;
  }

  public static TResult makeTResult(Concrete.ReferenceExpression defCall, CallableDefinition definition, Levels levels, CheckTypeVisitor typechecker) {
    List<DependentLink> parameters = new ArrayList<>();
    Expression resultType = definition.getTypeWithParams(parameters, levels);

    if (parameters.isEmpty()) {
      return new TypecheckingResult(definition.getDefCall(levels, Collections.emptyList()), resultType);
    } else {
      DefCallResult result = new DefCallResult(defCall, definition, levels, new ArrayList<>(), parameters, resultType);
      if (!typechecker.hasCategoricalContext()) {
        result.myForcedInfinite = false;
        result.updateResultPathType();
      }
      return result;
    }
  }

  private static boolean isPathDefinition(CallableDefinition definition) {
    return definition == Prelude.PATH || definition == Prelude.DPATH || definition == Prelude.PATH_INFIX || definition == Prelude.DPATH_INFIX || definition == Prelude.PATH_CON || definition == Prelude.DPATH_CON;
  }

  private void updateResultPathType() {
    if (!myForcedInfinite && myResultType instanceof PathTypeExpression pathType && pathType.isForcedInfinite()) {
      myResultType = new PathTypeExpression(pathType.getArgumentType(), pathType.getLeftArgument(), pathType.getRightArgument(), pathType.isDirected(), false);
    }
  }

  private boolean isForcedInfinite(CheckTypeVisitor typechecker) {
    if (myForcedInfinite == null) {
      myForcedInfinite = false;
      if (typechecker != null && isPathDefinition(myDefinition)) {
        for (Expression argument : myArguments) {
          if (typechecker.dependsOnCategoricalContext(argument)) {
            myForcedInfinite = true;
            break;
          }
        }
      }
      updateResultPathType();
    }
    return myForcedInfinite;
  }

  private Expression getCoreDefCall(CheckTypeVisitor typechecker) {
    return myDefinition == Prelude.PATH_CON || myDefinition == Prelude.DPATH_CON
      ? new PathExpression(myArguments.get(0), myArguments.get(1), myDefinition == Prelude.DPATH_CON, isForcedInfinite(typechecker))
      : myDefinition == Prelude.PATH || myDefinition == Prelude.DPATH
        ? new PathTypeExpression(myArguments.get(0), myArguments.get(1), myArguments.get(2), myDefinition == Prelude.DPATH, isForcedInfinite(typechecker))
        : myDefinition == Prelude.PATH_INFIX || myDefinition == Prelude.DPATH_INFIX
          ? new PathTypeExpression(new LamExpression(myDefinition == Prelude.DPATH_INFIX ? UnusedDirectedIntervalDependentLink.INSTANCE : UnusedIntervalDependentLink.INSTANCE, myArguments.get(0)), myArguments.get(1), myArguments.get(2), myDefinition == Prelude.DPATH_INFIX, isForcedInfinite(typechecker))
          : myDefinition == Prelude.AT || myDefinition == Prelude.DAT
            ? AtExpression.make(myArguments.get(3), myArguments.get(4), true, myDefinition == Prelude.DAT)
            : myDefinition.getDefCall(myLevels, myArguments);
  }

  @Override
  public TypecheckingResult toResult(CheckTypeVisitor typechecker) {
    if (myParameters.isEmpty()) {
      return new TypecheckingResult(getCoreDefCall(typechecker), getType(typechecker));
    }

    {
      int i = myArguments.size();
      for (DependentLink parameter : myParameters) {
        if (parameter.getType().isInfinityLevel()) {
          typechecker.getErrorReporter().report(new TypecheckingError((parameter.getName() == null ? StringUtils.ordinal(i) + " parameter" : "Parameter '" + parameter.getName() + "'") + " must be specified explicitly", myDefCall));
          return null;
        }
        i++;
      }
    }

    List<SingleDependentLink> parameters = new ArrayList<>();
    ExprSubstitution substitution = new ExprSubstitution();
    List<String> names = new ArrayList<>();
    DependentLink link0 = null;
    for (DependentLink link : myParameters) {
      if (link0 == null) {
        link0 = link;
      }

      names.add(link.getName());
      if (link instanceof TypedDependentLink) {
        Expression parameterType = link.getType().subst(substitution);
        typechecker.checkCatDomain(parameterType, myDefCall);
        SingleDependentLink parameter = ExpressionFactory.singleParams(link.isExplicit(), names, parameterType, link.getVariance());
        parameters.add(parameter);
        names.clear();

        for (; parameter.hasNext(); parameter = parameter.getNext(), link0 = link0.getNext()) {
          substitution.add(link0, new ReferenceExpression(parameter));
          myArguments.add(new ReferenceExpression(parameter));
        }

        link0 = null;
      }
    }

    Expression expression = getCoreDefCall(typechecker);
    Expression resultType = myResultType instanceof UniverseExpression ? getType(typechecker) : myResultType.subst(substitution, LevelSubstitution.EMPTY);
    if (parameters.isEmpty()) {
      return new TypecheckingResult(expression, resultType);
    }

    for (int i = parameters.size() - 1; i >= 0; i--) {
      expression = new LamExpression(parameters.get(i), expression);
      resultType = new PiExpression(parameters.get(i), resultType);
    }
    return new TypecheckingResult(expression, resultType);
  }

  @Override
  public DependentLink getParameter() {
    return myParameters.getFirst();
  }

  @Override
  public TResult applyExpression(Expression expression, boolean isExplicit, CheckTypeVisitor typechecker, Concrete.SourceNode sourceNode) {
    int size = myParameters.size();
    myArguments.add(expression);
    ExprSubstitution subst = new ExprSubstitution();
    subst.add(myParameters.getFirst(), expression);
    myParameters = DependentLink.Helper.subst(myParameters.subList(1, size), subst, LevelSubstitution.EMPTY);
    myResultType = myResultType.subst(subst, LevelSubstitution.EMPTY);
    return size > 1 ? this : new TypecheckingResult(getCoreDefCall(typechecker), getType(typechecker));
  }

  public TResult applyExpressions(List<? extends Expression> expressions, @NotNull CheckTypeVisitor typechecker) {
    int size = myParameters.size();
    List<? extends Expression> args = expressions.size() <= size ? expressions : expressions.subList(0, size);
    myArguments.addAll(args);
    ExprSubstitution subst = new ExprSubstitution();
    for (int i = 0; i < args.size(); i++) {
      subst.add(myParameters.get(i), args.get(i));
    }
    myParameters = DependentLink.Helper.subst(myParameters.subList(args.size(), size), subst, LevelSubstitution.EMPTY);
    myResultType = myResultType.subst(subst, LevelSubstitution.EMPTY);

    assert expressions.size() <= size;
    return expressions.size() < size ? this : new TypecheckingResult(getCoreDefCall(typechecker), getType(typechecker));
  }

  public TResult applyPathArgument(boolean isDirected, Expression argument, CheckTypeVisitor visitor, Concrete.SourceNode sourceNode) {
    assert myDefinition == (isDirected ? Prelude.DPATH_CON : Prelude.PATH_CON) && !myArguments.isEmpty();
    Expression leftExpr = AppExpression.make(argument, ExpressionFactory.Left(isDirected), true);
    Expression rightExpr = AppExpression.make(argument, ExpressionFactory.Right(isDirected), true);
    ExprSubstitution subst = new ExprSubstitution();
    if (myArguments.size() >= 2) {
      if (!CompareVisitor.compare(visitor.getEquations(), CMP.EQ, leftExpr, myArguments.get(1), AppExpression.make(myArguments.get(0), ExpressionFactory.Left(isDirected), true), sourceNode)) {
        visitor.getErrorReporter().report(new PathEndpointMismatchError(visitor.getExpressionPrettifier(), true, myArguments.get(1), leftExpr, sourceNode));
      }
    } else {
      subst.add(myParameters.getFirst(), leftExpr);
    }
    if (myArguments.size() >= 3) {
      if (!CompareVisitor.compare(visitor.getEquations(), CMP.EQ, rightExpr, myArguments.get(2), AppExpression.make(myArguments.get(0), ExpressionFactory.Right(isDirected), true), sourceNode)) {
        visitor.getErrorReporter().report(new PathEndpointMismatchError(visitor.getExpressionPrettifier(), false, myArguments.get(2), rightExpr, sourceNode));
      }
    } else {
      subst.add(myParameters.get(myParameters.size() - 2), rightExpr);
    }
    if (myArguments.size() > 1) {
      myArguments.subList(1, myArguments.size()).clear();
    }
    myArguments.add(argument);

    myParameters = Collections.emptyList();
    myResultType = myResultType.subst(subst, LevelSubstitution.EMPTY);
    return new TypecheckingResult(getCoreDefCall(visitor), getType(visitor));
  }

  @Override
  public List<DependentLink> getImplicitParameters() {
    List<DependentLink> params = new ArrayList<>(myParameters.size());
    for (DependentLink param : myParameters) {
      if (param.isExplicit()) {
        return params;
      }
      params.add(param);
    }
    myResultType.getPiParameters(params, true);
    return params;
  }

  @Override
  public Expression getType(@NotNull CheckTypeVisitor typechecker) {
    if (myResultType instanceof UniverseExpression && isForcedInfinite(typechecker)) {
      return new UniverseExpression(new Sort(Level.INFINITY, ConstLevel.INFINITY));
    }
    if (myResultType instanceof UniverseExpression universe && !(universe.getSortExpression() instanceof SortExpression.Const) && !myArguments.isEmpty()) {
      DependentLink param = myDefinition.getParameters();
      for (Expression argument : myArguments) {
        if (argument instanceof InferenceReferenceExpression infRefExpr && infRefExpr.getInferenceVariable() != null && param.getType().isInfinityLevel()) {
          typechecker.getEquations().solveLowerBounds(infRefExpr.getInferenceVariable());
        }
        param = param.getNext();
      }

      return new UniverseExpression(universe.getSortExpression().subst(myArguments, LevelSubstitution.EMPTY, GetTypeVisitor.INSTANCE));
    }
    return myResultType;
  }

  public Concrete.ReferenceExpression getDefCall() {
    return myDefCall;
  }

  public Definition getDefinition() {
    return myDefinition;
  }

  public List<? extends Expression> getArguments() {
    return myArguments;
  }

  public Levels getLevels() {
    return myLevels;
  }
}
