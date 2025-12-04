package org.arend.core.expr;

import org.arend.core.context.param.SingleDependentLink;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.sort.Sort;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.expr.AbstractedExpression;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.core.expr.CoreLamExpression;
import org.arend.extImpl.AbstractedExpressionImpl;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;

public class LamExpression extends Expression implements CoreLamExpression {
  private final SingleDependentLink myLink;
  private final Expression myBody;
  private Sort myCodomainSort;

  public LamExpression(SingleDependentLink link, Expression body, Sort codomainSort) {
    myLink = link;
    myBody = body;
    myCodomainSort = codomainSort;
  }

  public Sort getCodomainSort() {
    return myCodomainSort;
  }

  public void setCodomainSort(Sort sort) {
    myCodomainSort = sort;
  }

  public void substSort(LevelSubstitution substitution) {
    myCodomainSort = myCodomainSort.subst(substitution);
  }

  @NotNull
  @Override
  public SingleDependentLink getParameters() {
    return myLink;
  }

  @NotNull
  @Override
  public Expression getBody() {
    return myBody;
  }

  @Override
  public @NotNull AbstractedExpression getAbstractedBody() {
    return AbstractedExpressionImpl.make(myLink, myBody);
  }

  @Override
  public @NotNull CoreLamExpression dropParameters(int n) {
    SingleDependentLink link = myLink;
    for (int i = 0; i < n; i++) {
      link = link.getNext();
      if (!link.hasNext()) {
        throw new IllegalArgumentException();
      }
    }
    return new LamExpression(link, myBody, myCodomainSort);
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitLam(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitLam(this, param1, param2);
  }

  @Override
  public <P, R> R accept(@NotNull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitLam(this, params);
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
