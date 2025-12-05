package org.arend.core.expr.type;

import org.arend.core.expr.Expression;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.sort.Sort;
import org.arend.core.subst.InPlaceLevelSubstVisitor;

public interface Type {
  Expression getExpr();
  Sort getSortOfType();
  void subst(InPlaceLevelSubstVisitor substVisitor);
  Type strip(StripVisitor visitor);
}
