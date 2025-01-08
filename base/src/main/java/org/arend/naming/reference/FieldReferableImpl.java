package org.arend.naming.reference;

import org.arend.ext.reference.Precedence;
import org.arend.term.group.AccessModifier;

public class FieldReferableImpl extends ConcreteClassFieldReferable implements TCFieldReferable {
  private final boolean myRealParameterField;

  public FieldReferableImpl(AccessModifier accessModifier, Precedence precedence, String name, boolean isExplicit, boolean isParameter, boolean isRealParameterField, TCDefReferable parent) {
    super(null, accessModifier, name, precedence, null, Precedence.DEFAULT, !isParameter, isExplicit, isParameter, parent);
    myRealParameterField = isRealParameterField;
  }

  public FieldReferableImpl(AccessModifier accessModifier, Precedence precedence, String name, boolean isExplicit, boolean isParameter, TCDefReferable parent) {
    this(accessModifier, precedence, name, isExplicit, isParameter, false, parent);
  }

  @Override
  public boolean isRealParameterField() {
    return myRealParameterField;
  }
}
