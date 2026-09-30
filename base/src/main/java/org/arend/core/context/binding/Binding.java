package org.arend.core.context.binding;

import org.arend.core.expr.Expression;
import org.arend.core.expr.ReferenceExpression;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.subst.InPlaceLevelSubstVisitor;
import org.arend.core.subst.SubstVisitor;
import org.arend.ext.core.context.BindingVariance;
import org.arend.ext.core.context.CoreBinding;
import org.jetbrains.annotations.NotNull;

public interface Binding extends CoreBinding {
  @Override Expression getType();
  void strip(StripVisitor stripVisitor);
  void subst(InPlaceLevelSubstVisitor substVisitor);

  default boolean isHidden() {
    return false;
  }

  default boolean isUnused() {
    return false;
  }

  default @NotNull BindingVariance getVariance() {
    return BindingVariance.INVARIANT;
  }

  default Binding subst(SubstVisitor visitor) {
    return visitor.isEmpty() ? this : new TypedBinding(getName(), getType().accept(visitor, null));
  }

  @Override
  default ReferenceExpression makeReference() {
    return new ReferenceExpression(this);
  }
}
