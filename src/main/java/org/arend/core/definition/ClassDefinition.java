package org.arend.core.definition;

import org.arend.core.context.binding.Binding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.expr.*;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.SubstVisitor;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.naming.reference.TCClassReferable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class ClassDefinition extends Definition implements CoreClassDefinition {
  private final Set<ClassDefinition> mySuperClasses = new LinkedHashSet<>();
  private final LinkedHashSet<ClassField> myFields = new LinkedHashSet<>();
  private final List<ClassField> myPersonalFields = new ArrayList<>();
  private final Map<ClassField, AbsExpression> myImplemented = new HashMap<>();
  private final Map<ClassField, PiExpression> myOverridden = new HashMap<>();
  private ClassField myCoercingField;
  private Sort mySort = Sort.PROP;
  private boolean myRecord = false;
  private final CoerceData myCoerce = new CoerceData(this);
  private Set<ClassField> myGoodThisFields = Collections.emptySet();
  private Set<ClassField> myTypeClassParameters = Collections.emptySet();
  private ParametersLevels<ParametersLevel> myParametersLevels = new ParametersLevels<>();
  private FunctionDefinition mySquasher;

  public ClassDefinition(TCClassReferable referable) {
    super(referable, TypeCheckingStatus.HEADER_NEEDS_TYPE_CHECKING);
  }

  @Override
  public TCClassReferable getReferable() {
    return (TCClassReferable) super.getReferable();
  }

  @Override
  public boolean isRecord() {
    return myRecord;
  }

  public void setRecord() {
    myRecord = true;
  }

  @Override
  public ClassField getClassifyingField() {
    return myCoercingField;
  }

  public void setClassifyingField(ClassField coercingField) {
    myCoercingField = coercingField;
  }

  public static class ParametersLevel extends org.arend.core.definition.ParametersLevel {
    public final List<ClassField> fields;

    public ParametersLevel(DependentLink parameters, int level, List<ClassField> fields) {
      super(parameters, level);
      this.fields = fields;
    }

    @Override
    public boolean hasEquivalentDomain(org.arend.core.definition.ParametersLevel another) {
      return another instanceof ParametersLevel && fields.equals(((ParametersLevel) another).fields) && super.hasEquivalentDomain(another);
    }
  }

  @Override
  public List<? extends ParametersLevel> getParametersLevels() {
    return myParametersLevels.getList();
  }

  public void addParametersLevel(ParametersLevel parametersLevel) {
    myParametersLevels.add(parametersLevel);
  }

  public FunctionDefinition getSquasher() {
    return mySquasher;
  }

  public void setSquasher(FunctionDefinition squasher) {
    mySquasher = squasher;
  }

  public Integer getUseLevel(Map<ClassField,Expression> implemented, Binding thisBinding) {
    loop:
    for (ParametersLevel parametersLevel : myParametersLevels.getList()) {
      if (parametersLevel.fields.size() != implemented.size()) {
        continue;
      }
      List<Expression> expressions = new ArrayList<>();
      for (ClassField field : parametersLevel.fields) {
        Expression expr = implemented.get(field);
        if (expr == null || expr.findBinding(thisBinding)) {
          continue loop;
        }
        expressions.add(expr);
      }

      if (parametersLevel.checkExpressionsTypes(expressions)) {
        return parametersLevel.level;
      }
    }
    return null;
  }

  public Sort computeSort(Sort sortArgument, Map<ClassField,Expression> implemented, Binding thisBinding) {
    Integer hLevel = getUseLevel(implemented, thisBinding);
    if (hLevel != null && hLevel == -1) {
      return Sort.PROP;
    }

    ClassCallExpression thisClass = new ClassCallExpression(this, sortArgument, Collections.emptyMap(), mySort.subst(sortArgument.toLevelSubstitution()), getUniverseKind());
    Sort sort = Sort.PROP;

    for (ClassField field : myFields) {
      if (myImplemented.containsKey(field) || implemented.containsKey(field)) {
        continue;
      }

      PiExpression fieldType = field.getType(sortArgument);
      if (fieldType.getCodomain().isInstance(ErrorExpression.class)) {
        continue;
      }

      Expression type = fieldType
        .applyExpression(new ReferenceExpression(ExpressionFactory.parameter("this", thisClass)))
        .normalize(NormalizationMode.WHNF)
        .getType();
      Sort sort1 = type == null ? null : type.toSort();
      if (sort1 != null) {
        sort = sort.max(sort1);
      }
    }

    return hLevel == null ? sort : new Sort(sort.getPLevel(), new Level(hLevel));
  }

  public void updateSort() {
    mySort = computeSort(Sort.STD, Collections.emptyMap(), null);
  }

  @Nonnull
  @Override
  public Sort getSort() {
    return mySort;
  }

  public void setSort(Sort sort) {
    mySort = sort;
  }

  @Override
  public CoerceData getCoerceData() {
    return myCoerce;
  }

  @Override
  public boolean isSubClassOf(@Nonnull CoreClassDefinition classDefinition) {
    if (this.equals(classDefinition)) return true;
    for (ClassDefinition superClass : mySuperClasses) {
      if (superClass.isSubClassOf(classDefinition)) return true;
    }
    return false;
  }

  @Nonnull
  @Override
  public Set<? extends ClassDefinition> getSuperClasses() {
    return mySuperClasses;
  }

  public void addSuperClass(ClassDefinition superClass) {
    mySuperClasses.add(superClass);
  }

  @Nonnull
  public Set<? extends ClassField> getFields() {
    return myFields;
  }

  @Nonnull
  @Override
  public List<? extends ClassField> getPersonalFields() {
    return myPersonalFields;
  }

  public int getNumberOfNotImplementedFields() {
    return myFields.size() - myImplemented.size();
  }

  public void addField(ClassField field) {
    myFields.add(field);
  }

  public void addPersonalField(ClassField field) {
    myPersonalFields.add(field);
  }

  public void addFields(Collection<? extends ClassField> fields) {
    myFields.addAll(fields);
  }

  @Override
  public boolean isImplemented(@Nonnull CoreClassField field) {
    return field instanceof ClassField && myImplemented.containsKey(field);
  }

  @Nonnull
  @Override
  public Set<Map.Entry<ClassField, AbsExpression>> getImplemented() {
    return myImplemented.entrySet();
  }

  public Set<? extends ClassField> getImplementedFields() {
    return myImplemented.keySet();
  }

  @Override
  public AbsExpression getImplementation(@Nonnull CoreClassField field) {
    return field instanceof ClassField ? myImplemented.get(field) : null;
  }

  public AbsExpression implementField(ClassField field, AbsExpression impl) {
    return myImplemented.putIfAbsent(field, impl);
  }

  @Nonnull
  @Override
  public Set<Map.Entry<ClassField, PiExpression>> getOverriddenFields() {
    return myOverridden.entrySet();
  }

  public PiExpression getOverriddenType(ClassField field, Sort sortArg) {
    PiExpression type = myOverridden.get(field);
    return type == null || sortArg.equals(Sort.STD) ? type : (PiExpression) new SubstVisitor(new ExprSubstitution(), sortArg.toLevelSubstitution()).visitPi(type, null);
  }

  @Nullable
  @Override
  public PiExpression getOverriddenType(@Nonnull CoreClassField field) {
    return field instanceof ClassField ? myOverridden.get(field) : null;
  }

  @Override
  public boolean isOverridden(@Nonnull CoreClassField field) {
    return field instanceof ClassField && myOverridden.containsKey(field);
  }

  public PiExpression overrideField(ClassField field, PiExpression type) {
    return myOverridden.putIfAbsent(field, type);
  }

  public Set<? extends ClassField> getGoodThisFields() {
    return myGoodThisFields;
  }

  public boolean isGoodField(ClassField field) {
    return myGoodThisFields.contains(field);
  }

  public void setGoodThisFields(Set<ClassField> goodThisFields) {
    myGoodThisFields = goodThisFields;
  }

  public Set<? extends ClassField> getTypeClassFields() {
    return myTypeClassParameters;
  }

  public boolean isTypeClassField(ClassField field) {
    return myTypeClassParameters.contains(field);
  }

  public void setTypeClassFields(Set<ClassField> typeClassFields) {
    myTypeClassParameters = typeClassFields;
  }

  @Override
  public Expression getTypeWithParams(List<? super DependentLink> params, Sort sortArgument) {
    return new UniverseExpression(mySort.subst(sortArgument.toLevelSubstitution()));
  }

  @Override
  public ClassCallExpression getDefCall(Sort sortArgument, List<Expression> args) {
    return new ClassCallExpression(this, sortArgument, Collections.emptyMap(), mySort.subst(sortArgument.toLevelSubstitution()), getUniverseKind());
  }

  public void clear() {
    mySuperClasses.clear();
    myFields.clear();
    myPersonalFields.clear();
    myImplemented.clear();
    myOverridden.clear();
    myCoercingField = null;
  }

  @Override
  public void fill() {
    for (ClassField field : myPersonalFields) {
      field.fill();
    }
  }
}
