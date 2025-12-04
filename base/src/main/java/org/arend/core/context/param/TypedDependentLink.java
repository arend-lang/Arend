package org.arend.core.context.param;

import org.arend.core.expr.Expression;
import org.arend.core.expr.ReferenceExpression;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.subst.InPlaceLevelSubstVisitor;
import org.arend.core.subst.SubstVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TypedDependentLink implements DependentLink {
  private boolean myExplicit;
  private String myName;
  private Expression myType;
  private DependentLink myNext;
  private final boolean myHidden;

  public TypedDependentLink(boolean isExplicit, String name, Expression type, boolean isHidden, DependentLink next) {
    assert next != null;
    myExplicit = isExplicit;
    myName = name;
    myType = type;
    myNext = next;
    myHidden = isHidden;
  }

  public TypedDependentLink(boolean isExplicit, String name, Expression type, DependentLink next) {
    this(isExplicit, name, type, false, next);
  }

  @Override
  public boolean isProperty() {
    return false;
  }

  @Override
  public boolean isExplicit() {
    return myExplicit;
  }

  @Override
  public void setExplicit(boolean isExplicit) {
    myExplicit = isExplicit;
  }

  @Override
  public void setType(Expression type) {
    myType = type;
  }

  @NotNull
  @Override
  public DependentLink getNext() {
    return myNext;
  }

  @Override
  public void setNext(DependentLink next) {
    myNext = next;
  }

  @Override
  public void setName(String name) {
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public @NotNull Expression getTypeExpr() {
    return myType;
  }

  @Override
  public DependentLink subst(SubstVisitor substVisitor, int size, boolean updateSubst) {
    if (size > 0) {
      TypedDependentLink result = new TypedDependentLink(myExplicit, myName, myType.accept(substVisitor, null), myHidden, EmptyDependentLink.getInstance());
      if (updateSubst) {
        substVisitor.getExprSubstitution().addSubst(this, new ReferenceExpression(result));
      } else {
        substVisitor.getExprSubstitution().add(this, new ReferenceExpression(result));
      }
      result.myNext = myNext.subst(substVisitor, size - 1, updateSubst);
      return result;
    } else {
      return EmptyDependentLink.getInstance();
    }
  }

  @Override
  public TypedDependentLink getNextTyped(List<String> names) {
    if (names != null) {
      names.add(myName);
    }
    return this;
  }

  @Override
  public boolean hasNext() {
    return true;
  }

  @Override
  public void strip(StripVisitor stripVisitor) {
    myType = myType.accept(stripVisitor, null);
  }

  @Override
  public void subst(InPlaceLevelSubstVisitor substVisitor) {
    myType.accept(substVisitor, null);
  }

  @Override
  public boolean isHidden() {
    return myHidden;
  }

  @Override
  public String toString() {
    return DependentLink.toString(this);
  }
}
