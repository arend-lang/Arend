package org.arend.core.context.binding;

import org.arend.core.expr.Expression;
import org.arend.core.expr.ReferenceExpression;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.subst.InPlaceLevelSubstVisitor;
import org.arend.core.subst.SubstVisitor;
import org.arend.ext.core.context.CoreBinding;

public interface Binding extends CoreBinding {
  @Override Expression getTypeExpr();
  void strip(StripVisitor stripVisitor);
  void subst(InPlaceLevelSubstVisitor substVisitor);

  default boolean isHidden() {
    return false;
  }

  default Binding subst(SubstVisitor visitor) {
    return visitor.isEmpty() ? this : new TypedBinding(getName(), getTypeExpr().accept(visitor, null));
  }

  @Override
  default ReferenceExpression makeReference() {
    return new ReferenceExpression(this);
  }
}
