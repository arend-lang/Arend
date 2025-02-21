package org.arend.term.concrete;

import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;

import java.util.Set;

public class FreeReferablesVisitor extends SearchConcreteVisitor<Void, TCDefReferable> {
  private final Set<? extends TCDefReferable> myReferables;

  public FreeReferablesVisitor(Set<? extends TCDefReferable> referables) {
    myReferables = referables;
  }

  @Override
  public TCDefReferable visitPattern(Concrete.Pattern pattern, Void params) {
    if (pattern instanceof Concrete.NamePattern) {
      Referable ref = ((Concrete.NamePattern) pattern).getReferable();
      if (ref instanceof TCDefReferable && myReferables.contains(ref)) {
        return (TCDefReferable) ref;
      }
    } else if (pattern instanceof Concrete.ConstructorPattern) {
      Referable ref = ((Concrete.ConstructorPattern) pattern).getConstructor();
      if (ref instanceof TCDefReferable && myReferables.contains(ref)) {
        return (TCDefReferable) ref;
      }
    }
    return super.visitPattern(pattern, params);
  }

  @Override
  public TCDefReferable visitParameter(Concrete.Parameter parameter, Void params) {
    for (Referable ref : parameter.getReferableList()) {
      if (ref instanceof TCDefReferable && myReferables.contains(ref)) {
        return (TCDefReferable) ref;
      }
    }
    return null;
  }

  @Override
  public TCDefReferable visitReference(Concrete.ReferenceExpression expr, Void params) {
    Referable ref = expr.getReferent();
    return ref instanceof TCDefReferable && myReferables.contains(ref) ? (TCDefReferable) ref : null;
  }

  @Override
  public TCDefReferable visitClassFieldImpl(Concrete.ClassFieldImpl fieldImpl, Void params) {
    Referable ref = fieldImpl.getImplementedField();
    return ref instanceof TCDefReferable && myReferables.contains(ref) ? (TCDefReferable) ref : super.visitClassFieldImpl(fieldImpl, params);
  }
}
