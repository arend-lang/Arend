package org.arend.core.expr;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.DataDefinition;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.pattern.ExpressionPattern;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.Levels;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.core.expr.CorePathTypeExpression;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.prelude.Prelude;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PathTypeExpression extends Expression implements CorePathTypeExpression, BaseDataCallExpression {
  private final Expression myArgumentType;
  private final Expression myLeftArgument;
  private final Expression myRightArgument;
  private final boolean myDirected;

  public PathTypeExpression(Expression argumentType, Expression leftArgument, Expression rightArgument, boolean directed) {
    myArgumentType = argumentType;
    myLeftArgument = leftArgument;
    myRightArgument = rightArgument;
    myDirected = directed;
  }

  @Override
  public @NotNull Expression getArgumentType() {
    return myArgumentType;
  }

  @Override
  public @NotNull Expression getLeftArgument() {
    return myLeftArgument;
  }

  @Override
  public @NotNull Expression getRightArgument() {
    return myRightArgument;
  }

  @Override
  public boolean isDirected() {
    return myDirected;
  }

  @Override
  public @NotNull DataDefinition getDefinition() {
    return myDirected ? Prelude.DPATH : Prelude.PATH;
  }

  public @NotNull Constructor getConstructor() {
    return myDirected ? Prelude.DPATH_CON : Prelude.PATH_CON;
  }

  @Override
  public @NotNull Levels getLevels() {
    return Levels.EMPTY;
  }

  @Override
  public @NotNull LevelSubstitution getLevelSubstitution() {
    return LevelSubstitution.EMPTY;
  }

  @Override
  public <P, R> R accept(@NotNull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitPathType(this, params);
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitPathType(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitPathType(this, param1, param2);
  }

  @Override
  public Decision isWHNF() {
    return Decision.YES;
  }

  @Override
  public Expression getStuckExpression() {
    return null;
  }

  @Override
  public boolean getMatchedConCall(Constructor constructor, List<ConCallExpression> conCalls) {
    if (constructor.getDataType() != getDefinition()) {
      return false;
    }
    if (!constructor.status().headerIsOK()) {
      return true;
    }

    List<Expression> arguments = Arrays.asList(myArgumentType, myLeftArgument, myRightArgument);
    List<Expression> matchedParameters;
    if (constructor.getPatterns() != null) {
      matchedParameters = new ArrayList<>();
      Decision matchResult = ExpressionPattern.match(constructor.getPatterns(), arguments, matchedParameters);
      if (matchResult == Decision.MAYBE) {
        return false;
      }
      if (matchResult == Decision.NO) {
        return true;
      }
    } else {
      matchedParameters = arguments;
    }

    conCalls.add(new ConCallExpression(constructor, Levels.EMPTY, matchedParameters, new ArrayList<>()));
    return true;
  }

  public boolean getMatchedConstructors(List<ConCallExpression> result) {
    if (!myDirected && myArgumentType.removeConstLam() != null && myLeftArgument.areDisjointConstructors(myRightArgument)) {
      return true;
    }
    return getMatchedConCall(myDirected ? Prelude.DPATH_CON : Prelude.PATH_CON, result);
  }

  @Override
  public @Nullable List<ConCallExpression> getMatchedConstructors() {
    if (!myDirected && myArgumentType.removeConstLam() != null && myLeftArgument.areDisjointConstructors(myRightArgument)) {
      return Collections.emptyList();
    }

    List<ConCallExpression> result = new ArrayList<>();
    return getMatchedConCall(myDirected ? Prelude.DPATH_CON : Prelude.PATH_CON, result) ? result : null;
  }

  @Override
  public boolean computeMatchedConstructorsWithDataArguments(List<? super ConstructorWithDataArguments> result) {
    List<ConCallExpression> conCalls = new ArrayList<>();
    boolean ok = getMatchedConstructors(conCalls);
    for (ConCallExpression conCall : conCalls) {
      result.add(new ConstructorWithDataArgumentsImpl(conCall));
    }
    return ok;
  }

  @Override
  public @Nullable List<ConstructorWithDataArguments> computeMatchedConstructorsWithDataArguments() {
    List<ConCallExpression> conCalls = getMatchedConstructors();
    if (conCalls == null) {
      return null;
    }

    List<ConstructorWithDataArguments> constructors = new ArrayList<>();
    for (ConCallExpression conCall : conCalls) {
      constructors.add(new ConstructorWithDataArgumentsImpl(conCall));
    }
    return constructors;
  }

  private static class ConstructorWithDataArgumentsImpl implements ConstructorWithDataArguments {
    private final ConCallExpression myConCall;
    private DependentLink myParameters;

    private ConstructorWithDataArgumentsImpl(ConCallExpression conCall) {
      myConCall = conCall;
    }

    @Override
    public @NotNull Constructor getConstructor() {
      return myConCall.getDefinition();
    }

    @Override
    public @NotNull List<? extends Expression> getDataTypeArguments() {
      return myConCall.getDataTypeArguments();
    }

    @Override
    public @NotNull CoreParameter getParameters() {
      if (myParameters == null) {
        myParameters = DependentLink.Helper.subst(myConCall.getDefinition().getParameters(), new ExprSubstitution().add(myConCall.getDefinition().getDataType().getParameters(), myConCall.getDataTypeArguments()), myConCall.getLevelSubstitution());
      }
      return myParameters;
    }
  }

  @Override
  public @NotNull List<Expression> getDefCallArguments() {
    return Arrays.asList(myArgumentType, myLeftArgument, myRightArgument);
  }
}
