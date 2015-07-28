package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.definition.Binding;
import com.jetbrains.jetpad.vclang.term.definition.Constructor;
import com.jetbrains.jetpad.vclang.term.definition.Definition;
import com.jetbrains.jetpad.vclang.term.expr.arg.Utils;
import com.jetbrains.jetpad.vclang.term.expr.visitor.AbstractExpressionVisitor;
import com.jetbrains.jetpad.vclang.term.expr.visitor.ExpressionVisitor;
import com.jetbrains.jetpad.vclang.term.expr.visitor.ReplaceDefCallVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefCallExpression extends Expression implements Abstract.DefCallExpression {
  private final Expression myExpression;
  private final Definition myDefinition;
  private List<Expression> myParameters;

  public DefCallExpression(Expression expression, Definition definition, List<Expression> parameters) {
    myDefinition = definition;
    myExpression = expression;
    myParameters = parameters;
  }

  public List<Expression> getParameters() {
    return myParameters;
  }

  public void setParameters(List<Expression> parameters) {
    myParameters = parameters;
  }

  @Override
  public Expression getExpression() {
    return myExpression;
  }

  @Override
  public Definition getDefinition() {
    return myDefinition;
  }

  @Override
  public Utils.Name getName() {
    return myDefinition.getName();
  }

  @Override
  public <T> T accept(ExpressionVisitor<? extends T> visitor) {
    return visitor.visitDefCall(this);
  }

  @Override
  public Expression getType(List<Binding> context) {
    Expression resultType;
    resultType = myDefinition.getType();
    if (myDefinition instanceof Constructor && !((Constructor) myDefinition).getDataType().getParameters().isEmpty()) {
      List<Expression> parameters = new ArrayList<>(myParameters);
      Collections.reverse(parameters);
      resultType = resultType.subst(parameters, 0);
    }
    return myExpression == null ? resultType : resultType.accept(new ReplaceDefCallVisitor(myDefinition.getParent(), myExpression));
  }

  @Override
  public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitDefCall(this, params);
  }
}
