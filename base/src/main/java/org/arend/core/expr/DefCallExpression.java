package org.arend.core.expr;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.CallableDefinition;
import org.arend.core.definition.ParametersLevel;
import org.arend.core.subst.ExprSubstitution;
import org.arend.ext.core.expr.CoreDefCallExpression;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public abstract class DefCallExpression extends Expression implements CoreDefCallExpression {
  private final CallableDefinition myDefinition;

  public DefCallExpression(CallableDefinition definition) {
    myDefinition = definition;
  }

  public LevelSubstitution getLevelSubstitution() {
    return LevelSubstitution.EMPTY;
  }

  @Override
  public @NotNull List<Expression> getDefCallArguments() {
    return Collections.emptyList();
  }

  public List<? extends Expression> getConCallArguments() {
    return getDefCallArguments();
  }

  @Override
  public @NotNull CallableDefinition getDefinition() {
    return myDefinition;
  }

  public BigInteger getUseLevel() {
    for (ParametersLevel parametersLevel : myDefinition.getParametersLevels()) {
      if (parametersLevel.checkExpressionsTypes(getDefCallArguments())) {
        return parametersLevel.level;
      }
    }
    return null;
  }

  public void fixBoxes() {
    List<Expression> args0 = getDefCallArguments();
    boolean needsBoxing = false;
    DependentLink param0 = myDefinition.getParameters();
    for (int i = 0; i < args0.size() && param0.hasNext(); i++, param0 = param0.getNext()) {
      if (param0.isProperty() && !(args0.get(i) instanceof BoxExpression)) {
        needsBoxing = true;
        break;
      }
    }
    if (!needsBoxing) {
      return;
    }

    LevelSubstitution levelSubst = getLevelSubstitution();
    ExprSubstitution substitution = new ExprSubstitution();
    if (this instanceof ConCallExpression conCall) {
      DependentLink param = conCall.getDefinition().getDataTypeParameters();
      for (Expression arg : conCall.getDataTypeArguments()) {
        if (!param.hasNext()) break;
        substitution.add(param, arg);
        param = param.getNext();
      }
    }
    List<Expression> args = getDefCallArguments();
    DependentLink param = myDefinition.getParameters();
    for (int i = 0; i < args.size(); i++) {
      if (!param.hasNext()) break;
      Expression arg = args.get(i);
      if (param.isProperty() && !(arg instanceof BoxExpression)) {
        args.set(i, BoxExpression.make(arg, param.getType().subst(substitution, levelSubst)));
      }
      substitution.add(param, arg);
      param = param.getNext();
    }
  }

  @Override
  public Decision isWHNF() {
    return Decision.YES;
  }

  @Override
  public Expression getStuckExpression() {
    return null;
  }
}
