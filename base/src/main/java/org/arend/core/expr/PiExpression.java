package org.arend.core.expr;

import org.arend.core.context.param.SingleDependentLink;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.expr.visitor.NormalizeVisitor;
import org.arend.core.sort.Sort;
import org.arend.core.subst.ExprSubstitution;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.extImpl.AbstractedExpressionImpl;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PiExpression extends Expression implements CorePiExpression, CoreAbsExpression {
  private final SingleDependentLink myLink;
  private final Expression myCodomain;

  public PiExpression(SingleDependentLink link, Expression codomain) {
    assert link.hasNext();
    myLink = link;
    myCodomain = codomain;
  }

  public static Sort piSort(Sort domSort, Sort codSort) {
    return Sort.make(domSort.getPLevel().max(codSort.getPLevel()), codSort.getHLevel(), domSort.isCat() || codSort.isCat());
  }

  @NotNull
  @Override
  public SingleDependentLink getParameters() {
    return myLink;
  }

  @NotNull
  @Override
  public Expression getCodomain() {
    return myCodomain;
  }

  @Override
  public @NotNull AbstractedExpression getAbstractedCodomain() {
    return AbstractedExpressionImpl.make(myLink, myCodomain);
  }

  @Override
  public @NotNull CorePiExpression dropParameters(int n) {
    SingleDependentLink link = myLink;
    for (int i = 0; i < n; i++) {
      link = link.getNext();
      if (!link.hasNext()) {
        throw new IllegalArgumentException();
      }
    }
    return new PiExpression(link, myCodomain);
  }

  @Override
  public Expression applyExpression(Expression expression) {
    SingleDependentLink link = myLink;
    ExprSubstitution subst = new ExprSubstitution(link, expression);
    link = link.getNext();
    Expression result = myCodomain;
    if (link.hasNext()) {
      result = new PiExpression(link, result);
    }
    return result.subst(subst);
  }

  @Override
  public Expression applyExpression(Expression expression, boolean normalizing) {
    return applyExpression(expression);
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitPi(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitPi(this, param1, param2);
  }

  @Override
  public <P, R> R accept(@NotNull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitPi(this, params);
  }

  @Override
  public Sort getSortOfType() {
    Sort codSort = myCodomain.getSortOfType();
    return codSort == null ? null : piSort(myLink.getType().getSortOfType(), codSort);
  }

  @Override
  public @NotNull CoreExpression getPiParameters(@Nullable List<? super CoreParameter> parameters) {
    return getPiParameters(parameters, false);
  }

  @Override
  public Expression getPiParameters(List<? super SingleDependentLink> params, boolean implicitOnly) {
    Expression cod = this;
    while (cod instanceof PiExpression piCod) {
      if (implicitOnly) {
        if (piCod.getParameters().isExplicit()) {
          break;
        }
        for (SingleDependentLink link = piCod.getParameters(); link.hasNext(); link = link.getNext()) {
          if (params != null) {
            params.add(link);
          }
        }
      } else {
        if (params != null) {
          for (SingleDependentLink link = piCod.getParameters(); link.hasNext(); link = link.getNext()) {
            params.add(link);
          }
        }
      }
      cod = piCod.getCodomain().normalize(NormalizationMode.WHNF);
    }
    return cod;
  }

  @NotNull
  @Override
  public PiExpression normalize(@NotNull NormalizationMode mode) {
    return NormalizeVisitor.INSTANCE.visitPi(this, mode);
  }

  @Override
  public Decision isWHNF() {
    return Decision.YES;
  }

  @Override
  public Expression getStuckExpression() {
    return null;
  }

  @NotNull
  @Override
  public SingleDependentLink getBinding() {
    return myLink;
  }

  @NotNull
  @Override
  public Expression getExpression() {
    return myCodomain;
  }
}
