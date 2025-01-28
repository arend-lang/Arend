package org.arend.core.definition;

import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.SingleDependentLink;
import org.arend.core.expr.*;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.Levels;
import org.arend.core.subst.SubstVisitor;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.naming.reference.FieldReferableImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ClassField extends CallableDefinition implements CoreClassField {
  private final ClassDefinition myParentClass;
  private boolean myProperty;
  private PiExpression myType;
  private Expression myTypeLevel;
  private int myResultTypeLevel = -2;
  private int myNumberOfParameters;
  private boolean myHideable;
  private UniverseKind myUniverseKind = UniverseKind.NO_UNIVERSES;

  public ClassField(FieldReferableImpl referable, ClassDefinition parentClass) {
    super(referable, TypeCheckingStatus.NEEDS_TYPE_CHECKING);
    myParentClass = parentClass;
  }

  public ClassField(FieldReferableImpl referable, ClassDefinition parentClass, PiExpression type, Expression typeLevel) {
    super(referable, TypeCheckingStatus.NO_ERRORS);
    myParentClass = parentClass;
    myType = type;
    myTypeLevel = typeLevel;
  }

  @Override
  public FieldReferableImpl getReferable() {
    return (FieldReferableImpl) super.getReferable();
  }

  @Override
  public TopLevelDefinition getTopLevelDefinition() {
    return myParentClass;
  }

  @NotNull
  @Override
  public ClassDefinition getParentClass() {
    return myParentClass;
  }

  public void setType(PiExpression type) {
    myType = type;
  }

  public PiExpression getType(Levels levels) {
    return (PiExpression) new SubstVisitor(new ExprSubstitution(), levels.makeSubstitution(getParentClass())).visitPi(myType, null);
  }

  public PiExpression getType() {
    return myType;
  }

  public void setNumberOfParameters(int numberOfParameters) {
    myNumberOfParameters = numberOfParameters;
  }

  public int getNumberOfParameters() {
    return myNumberOfParameters;
  }

  @NotNull
  @Override
  public SingleDependentLink getThisParameter() {
    return myType.getParameters();
  }

  @NotNull
  @Override
  public Expression getResultType() {
    return myType.getCodomain();
  }

  public Expression getResultTypeFor(ClassDefinition classDef) {
    Levels levels = classDef.getSuperLevels().get(getParentClass());
    return levels == null ? getResultType() : getResultType().subst(levels.makeSubstitution(getParentClass()));
  }

  @Override
  public Expression getTypeLevel() {
    return myTypeLevel;
  }

  public int getResultTypeLevel() {
    return myResultTypeLevel;
  }

  public void setTypeLevel(Expression typeLevel, int level) {
    myTypeLevel = typeLevel;
    myResultTypeLevel = level;
  }

  @Override
  public boolean isProperty() {
    return myProperty;
  }

  public void setIsProperty() {
    myProperty = true;
  }

  public boolean isTypeClass() {
    return myParentClass.isTypeClassField(this);
  }

  @Override
  public int getVisibleParameter() {
    return myHideable ? 0 : -1;
  }

  @Override
  public boolean isHideable() {
    return myHideable;
  }

  public void setHideable(boolean isHideable) {
    myHideable = isHideable;
  }

  @Override
  public List<Boolean> getGoodThisParameters() {
    return Collections.singletonList(true);
  }

  @Override
  public boolean isGoodParameter(int index) {
    return index == 0;
  }

  @Override
  public List<TypeClassParameterKind> getTypeClassParameters() {
    return myParentClass.isRecord() ? Collections.emptyList() : Collections.singletonList(TypeClassParameterKind.YES);
  }

  @Override
  public TypeClassParameterKind getTypeClassParameterKind(int index) {
    return index == 0 && !myParentClass.isRecord() ? TypeClassParameterKind.YES : TypeClassParameterKind.NO;
  }

  @Override
  public UniverseKind getUniverseKind() {
    return myUniverseKind;
  }

  public void setUniverseKind(UniverseKind kind) {
    myUniverseKind = kind;
  }

  @Override
  public <P, R> R accept(DefinitionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitField(this, params);
  }

  @NotNull
  @Override
  public DependentLink getParameters() {
    return myType.getParameters();
  }

  @Override
  public Expression getTypeWithParams(List<? super DependentLink> params, Levels levels) {
    PiExpression type = getType(levels);
    params.add(type.getParameters());
    return type.getCodomain();
  }

  @Override
  public Expression getDefCall(Levels levels, List<Expression> args) {
    return FieldCallExpression.make(this, args.get(0));
  }
}
