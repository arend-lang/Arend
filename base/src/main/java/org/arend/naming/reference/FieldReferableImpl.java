package org.arend.naming.reference;

import org.arend.ext.reference.Precedence;
import org.arend.term.group.AccessModifier;

public class FieldReferableImpl extends InternalLocatedReferable implements TCFieldReferable {
  private final boolean myExplicit;
  private final boolean myRealParameterField;

  public FieldReferableImpl(Object data, AccessModifier accessModifier, Precedence precedence, String name, Precedence aliasPrecedence, String aliasName, boolean isExplicit, boolean isParameter, boolean isRealParameterField, TCDefReferable parent) {
    super(data, accessModifier, precedence, name, aliasPrecedence, aliasName, !isParameter, parent, Kind.FIELD);
    myRealParameterField = isRealParameterField;
    myExplicit = isExplicit;
  }

  @Override
  public boolean isExplicitField() {
    return myExplicit;
  }

  @Override
  public boolean isParameterField() {
    return !isVisible();
  }

  @Override
  public boolean isRealParameterField() {
    return myRealParameterField;
  }
}
