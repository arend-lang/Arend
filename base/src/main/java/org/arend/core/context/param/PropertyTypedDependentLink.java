package org.arend.core.context.param;

import org.arend.core.expr.Expression;
import org.arend.core.expr.ReferenceExpression;
import org.arend.core.subst.SubstVisitor;

public class PropertyTypedDependentLink extends TypedDependentLink {
  public PropertyTypedDependentLink(boolean isExplicit, String name, Expression type, boolean isHidden, DependentLink next) {
    super(isExplicit, name, type, isHidden, next);
  }

  public PropertyTypedDependentLink(boolean isExplicit, String name, Expression type, DependentLink next) {
    super(isExplicit, name, type, false, next);
  }

  @Override
  public boolean isProperty() {
    return true;
  }

  @Override
  public DependentLink subst(SubstVisitor substVisitor, int size, boolean updateSubst) {
    if (size > 0) {
      TypedDependentLink result = new PropertyTypedDependentLink(isExplicit(), getName(), getTypeExpr().accept(substVisitor, null), EmptyDependentLink.getInstance());
      if (updateSubst) {
        substVisitor.getExprSubstitution().addSubst(this, new ReferenceExpression(result));
      } else {
        substVisitor.getExprSubstitution().add(this, new ReferenceExpression(result));
      }
      result.setNext(getNext().subst(substVisitor, size - 1, updateSubst));
      return result;
    } else {
      return EmptyDependentLink.getInstance();
    }
  }
}
