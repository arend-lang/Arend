package org.arend.core.definition;

import org.arend.core.context.binding.Binding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.expr.*;
import org.arend.core.expr.visitor.FindBindingVisitor;
import org.arend.core.expr.visitor.GetTypeVisitor;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.sort.SortExpression;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.InfiniteFieldsSubstVisitor;
import org.arend.core.subst.Levels;
import org.arend.core.subst.SubstVisitor;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.level.ConstLevel;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.naming.reference.TCDefReferable;
import org.arend.ext.util.Pair;
import org.arend.typechecking.dfs.ClassDFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ClassDefinition extends TopLevelDefinition implements CoreClassDefinition {
  private final Set<ClassDefinition> mySuperClasses = new LinkedHashSet<>();
  private final LinkedHashSet<ClassField> myNotImplementedFields = new LinkedHashSet<>();
  private final List<ClassField> myPersonalFields = new ArrayList<>();
  private final Map<ClassField, AbsExpression> myImplemented = new HashMap<>();
  private final Map<ClassField, Pair<AbsExpression,Boolean>> myDefaults = new HashMap<>();
  private final Map<ClassField, Set<ClassField>> myDefaultDependencies = new HashMap<>();
  private final Map<ClassField, Set<ClassField>> myDefaultImplDependencies = new HashMap<>();
  private final Map<ClassField, Pair<PiExpression,ClassDefinition>> myOverridden = new HashMap<>();
  private final Set<ClassField> myCovariantFields = new HashSet<>();
  private ClassField myCoercingField;
  private SortExpression mySort = new SortExpression.Const(Sort.PROP);
  private boolean myRecord = false;
  private final CoerceData myCoerce = new CoerceData(this);
  private Set<ClassField> myGoodThisFields = Collections.emptySet();
  private Set<ClassField> myTypeClassParameters = Collections.emptySet();
  private final ParametersLevels<ParametersLevel> myParametersLevels = new ParametersLevels<>();
  private Map<ClassDefinition, Levels> mySuperLevels = Collections.emptyMap();

  public ClassDefinition(TCDefReferable referable) {
    super(referable, TypeCheckingStatus.NEEDS_TYPE_CHECKING);
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
    public final List<Pair<ClassDefinition,Set<ClassField>>> strictList;

    public ParametersLevel(DependentLink parameters, BigInteger level, List<ClassField> fields, List<Pair<ClassDefinition,Set<ClassField>>> strictList) {
      super(parameters, level);
      this.fields = fields;
      this.strictList = strictList;
    }

    @Override
    public boolean hasEquivalentDomain(org.arend.core.definition.ParametersLevel another) {
      return another instanceof ParametersLevel && fields.equals(((ParametersLevel) another).fields) && super.hasEquivalentDomain(another);
    }

    private boolean checkExpressionsTypesStrict(List<Expression> expressions) {
      if (strictList == null || expressions.size() != strictList.size()) {
        return false;
      }
      for (int i = 0; i < expressions.size(); i++) {
        if (strictList.get(i) == null) {
          continue;
        }
        Expression type = expressions.get(i).getType();
        if (type == null) {
          return false;
        }
        ClassCallExpression classCall = type.cast(ClassCallExpression.class);
        if (classCall == null || !classCall.getDefinition().isSubClassOf(strictList.get(i).proj1)) {
          return false;
        }
        for (ClassField field : strictList.get(i).proj2) {
          if (!classCall.isImplemented(field)) {
            return false;
          }
        }
      }
      return true;
    }
  }

  @Override
  public List<? extends ParametersLevel> getParametersLevels() {
    return myParametersLevels.getList();
  }

  @Override
  public <P, R> R accept(DefinitionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitClass(this, params);
  }

  public void addParametersLevel(ParametersLevel parametersLevel) {
    myParametersLevels.add(parametersLevel);
  }

  public Map<ClassDefinition, Levels> getSuperLevels() {
    return mySuperLevels;
  }

  public void setSuperLevels(Map<ClassDefinition, Levels> superLevels) {
    mySuperLevels = superLevels;
  }

  public @NotNull Levels getSuperLevels(ClassDefinition superClass) {
    Levels levels = mySuperLevels.get(superClass);
    return levels == null ? makeIdLevels() : levels;
  }

  public Levels castLevels(ClassDefinition superClass, Levels levels) {
    if (superClass == this) return levels;
    if (superClass.getLevelParameters().isEmpty()) return Levels.EMPTY;
    Levels result = mySuperLevels.get(superClass);
    return result == null ? levels : result.subst(levels.makeSubstitution(this));
  }

  public LevelSubstitution levelSubstitutionFor(ClassDefinition superClass, Levels levels) {
    return castLevels(superClass, levels).makeSubstitution(superClass);
  }

  public LevelSubstitution levelSubstitutionFor(ClassDefinition superClass) {
    if (superClass == this) return LevelSubstitution.EMPTY;
    if (superClass.getLevelParameters().isEmpty()) return LevelSubstitution.EMPTY;
    Levels result = mySuperLevels.get(superClass);
    return result == null ? LevelSubstitution.EMPTY : result.makeSubstitution(superClass);
  }

  public BigInteger getUseLevel(Map<ClassField,Expression> implemented, Binding thisBinding, boolean isStrict) {
    loop:
    for (ParametersLevel parametersLevel : myParametersLevels.getList()) {
      List<ClassField> fields = parametersLevel.fields == null ? Collections.emptyList() : parametersLevel.fields;
      if (isStrict && parametersLevel.strictList == null || fields.size() > implemented.size()) {
        continue;
      }
      if (fields.size() != implemented.size()) {
        for (ClassField field : implemented.keySet()) {
          if (!field.isProperty() && !fields.contains(field)) {
            continue loop;
          }
        }
      }
      List<Expression> expressions = new ArrayList<>();
      for (ClassField field : fields) {
        Expression expr = implemented.get(field);
        if (expr == null || expr.accept(new FindBindingVisitor(Collections.singleton(thisBinding), true), null)) {
          continue loop;
        }
        expressions.add(expr);
      }

      if (isStrict ? parametersLevel.checkExpressionsTypesStrict(expressions) : parametersLevel.checkExpressionsTypes(expressions)) {
        return parametersLevel.level;
      }
    }
    return null;
  }

  /**
   * Fields that are eligible for a level override via an extra level argument on a class call
   * (e.g. {@code R.{3}}), in the order in which they consume override slots. Fields in
   * {@code implemented} (e.g. implemented by a class extension co-occurring with the level
   * arguments) don't need a level and so don't consume a slot.
   */
  public List<ClassField> getOverridableInfiniteFields(Set<ClassField> implemented) {
    List<ClassField> result = new ArrayList<>();
    for (ClassField field : myNotImplementedFields) {
      if (field.isInfiniteField() && !implemented.contains(field) && field.getType().isPiInfinityLevel()) {
        result.add(field);
      }
    }
    return result;
  }

  public List<ClassField> getOverridableInfiniteFields() {
    return getOverridableInfiniteFields(Collections.emptySet());
  }

  /**
   * The level overriding {@code field}'s type in a class call with the given levels, if any
   * (see {@link #getOverridableInfiniteFields}); {@code levels} is expected to contain, after
   * the definition's own level parameters, one extra entry per overridable field, in order.
   */
  public @Nullable Level getFieldLevelOverride(ClassField field, Levels levels, Set<ClassField> implemented) {
    int index = getOverridableInfiniteFields(implemented).indexOf(field);
    if (index < 0) return null;
    List<? extends Level> levelList = levels.toList();
    int pos = getLevelParameters().size() + index;
    return pos < levelList.size() ? levelList.get(pos) : null;
  }

  public @Nullable Level getFieldLevelOverride(ClassField field, Levels levels) {
    return getFieldLevelOverride(field, levels, Collections.emptySet());
  }

  public SortExpression computeSort(Map<ClassField,Expression> implemented, Binding thisBinding, Levels levels, LevelSubstitution levelSubstitution, boolean ignoreErrors, GetTypeVisitor visitor) {
    Levels idLevels = makeIdLevels();
    ReferenceExpression thisExpr1 = new ReferenceExpression(ExpressionFactory.parameter("this", new ClassCallExpression(this, levels, implemented)));
    Expression thisExpr2 = new ReferenceExpression(ExpressionFactory.parameter("this", new ClassCallExpression(this, levels)));
    BigInteger hLevel = getUseLevel(implemented, thisBinding, true);
    if (hLevel != null && hLevel.equals(ConstLevel.PROP.value())) {
      return new SortExpression.Const(Sort.PROP);
    }

    List<SortExpression> sorts = new ArrayList<>();
    for (ClassField field : myNotImplementedFields) {
      if (implemented.containsKey(field)) continue;
      Expression fieldType = getFieldTypeSubstInfiniteFields(field, idLevels, thisExpr1).normalize(NormalizationMode.WHNF);
      Level overrideLevel = getFieldLevelOverride(field, levels, implemented.keySet());
      if (overrideLevel != null) {
        fieldType = fieldType.replaceInfinityLevel(overrideLevel);
      }
      if (!fieldType.isInstance(ErrorExpression.class)) {
        SortExpression fieldSort = fieldType.accept(visitor, null).toSortExpression();
        if (fieldSort == null || fieldSort instanceof SortExpression.Const(Sort sort) && sort.isOmega()) {
          fieldSort = getFieldType(field, idLevels, thisExpr2).normalize(NormalizationMode.WHNF).getSortExpressionOfType();
          if (!ignoreErrors && fieldSort == null) {
            return null;
          }
        }
        if (fieldSort != null) {
          sorts.add(levelSubstitution.isEmpty() ? fieldSort : fieldSort.subst(levelSubstitution));
        }
      }
    }

    SortExpression sort = SortExpression.makeMax(sorts);
    if (hLevel != null) {
      Sort infSort = sort.withInfLevel();
      ConstLevel constLevel = new ConstLevel(hLevel);
      if (constLevel.isLess(infSort.getHLevel())) {
        sort = new SortExpression.Const(new Sort(infSort.getPLevel(), constLevel));
      }
    }

    return sort;
  }

  public void updateSort() {
    mySort = computeSort(Collections.emptyMap(), null, makeIdLevels(), LevelSubstitution.EMPTY, true, GetTypeVisitor.INSTANCE);
  }

  public void setSortExpression(SortExpression sort) {
    mySort = sort;
  }

  @NotNull
  @Override
  public SortExpression getSortExpression() {
    return mySort;
  }

  public @Nullable Sort getSort() {
    return mySort instanceof SortExpression.Const(Sort sort) ? sort : null;
  }

  @Override
  public @NotNull CoerceData getCoerceData() {
    return myCoerce;
  }

  public static @Nullable CoreClassDefinition findAncestor(ArrayDeque<CoreClassDefinition> classDefs, Predicate<CoreClassDefinition> predicate) {
    Set<CoreClassDefinition> visited = new HashSet<>();
    while (!classDefs.isEmpty()) {
      CoreClassDefinition subClass = classDefs.pop();
      if (!visited.add(subClass)) {
        continue;
      }
      if (predicate.test(subClass)) {
        return subClass;
      }
      classDefs.addAll(subClass.getSuperClasses());
    }
    return null;
  }

  @Override
  public @Nullable CoreClassDefinition findAncestor(@NotNull Predicate<CoreClassDefinition> predicate) {
    ArrayDeque<CoreClassDefinition> classDefs = new ArrayDeque<>();
    classDefs.add(this);
    return findAncestor(classDefs, predicate);
  }

  @NotNull
  @Override
  public Set<? extends ClassDefinition> getSuperClasses() {
    return mySuperClasses;
  }

  public void addSuperClass(ClassDefinition superClass) {
    mySuperClasses.add(superClass);
  }

  @NotNull
  @Override
  public Set<? extends ClassField> getNotImplementedFields() {
    return myNotImplementedFields;
  }

  public Set<ClassField> getAllFields() {
    LinkedHashSet<ClassField> fields = new LinkedHashSet<>();
    fields.addAll(myNotImplementedFields);
    fields.addAll(myImplemented.keySet());
    return fields;
  }

  public void forFields(Consumer<ClassField> consumer) {
    new ClassDFS() {
      @Override
      protected Void forDependencies(ClassDefinition classDef) {
        super.forDependencies(classDef);
        for (ClassField field : classDef.getPersonalFields()) {
          consumer.accept(field);
        }
        return null;
      }
    }.visit(this);
  }

  public ClassField findField(Predicate<ClassField> pred) {
    Set<ClassDefinition> visited = new HashSet<>();
    return findField(pred, visited);
  }

  private ClassField findField(Predicate<ClassField> pred, Set<ClassDefinition> visited) {
    if (!visited.add(this)) return null;
    for (ClassField field : myPersonalFields) {
      if (pred.test(field)) {
        return field;
      }
    }
    for (ClassDefinition superClass : mySuperClasses) {
      ClassField result = superClass.findField(pred, visited);
      if (result != null) return result;
    }
    return null;
  }

  @NotNull
  @Override
  public List<? extends ClassField> getPersonalFields() {
    return myPersonalFields;
  }

  public boolean containsField(ClassField field) {
    return myNotImplementedFields.contains(field) || myImplemented.containsKey(field);
  }

  public boolean isCovariantField(ClassField field) {
    return myCovariantFields.contains(field);
  }

  public Set<? extends ClassField> getCovariantFields() {
    return myCovariantFields;
  }

  public void addCovariantField(ClassField field) {
    myCovariantFields.add(field);
  }

  public int getNumberOfNotImplementedFields() {
    return myNotImplementedFields.size();
  }

  public void addField(ClassField field) {
    myNotImplementedFields.add(field);
  }

  public void addPersonalField(ClassField field) {
    myPersonalFields.add(field);
  }

  @Override
  public boolean isImplemented(@NotNull CoreClassField field) {
    return field instanceof ClassField && myImplemented.containsKey(field);
  }

  @NotNull
  @Override
  public Set<Map.Entry<ClassField, AbsExpression>> getImplemented() {
    return myImplemented.entrySet();
  }

  @NotNull
  @Override
  public Set<? extends ClassField> getImplementedFields() {
    return myImplemented.keySet();
  }

  @Override
  public AbsExpression getImplementation(@NotNull CoreClassField field) {
    return field instanceof ClassField ? myImplemented.get(field) : null;
  }

  public AbsExpression implementField(ClassField field, AbsExpression impl) {
    myNotImplementedFields.remove(field);
    return myImplemented.putIfAbsent(field, impl);
  }

  public Set<Map.Entry<ClassField, Pair<AbsExpression, Boolean>>> getDefaults() {
    return myDefaults.entrySet();
  }

  public Pair<AbsExpression, Boolean> getDefaultPair(@NotNull ClassField field) {
    return myDefaults.get(field);
  }

  @Nullable
  @Override
  public AbsExpression getDefault(@NotNull CoreClassField field) {
    Pair<AbsExpression, Boolean> pair = field instanceof ClassField ? myDefaults.get(field) : null;
    return pair == null ? null : pair.proj1;
  }

  public AbsExpression addDefault(ClassField field, AbsExpression impl, boolean isFunc) {
    Pair<AbsExpression, Boolean> pair = myDefaults.put(field, new Pair<>(impl, isFunc));
    return pair == null ? null : pair.proj1;
  }

  public boolean addDefaultIfAbsent(ClassField field, AbsExpression impl, boolean isFunc) {
    return myDefaults.putIfAbsent(field, new Pair<>(impl, isFunc)) == null;
  }

  public Map<ClassField, Set<ClassField>> getDefaultDependencies() {
    return myDefaultDependencies;
  }

  public void addDefaultDependencies(ClassField field, Set<ClassField> dependencies) {
    myDefaultDependencies.computeIfAbsent(field, k -> new HashSet<>()).addAll(dependencies);
  }

  public void addDefaultDependency(ClassField field, ClassField dependency) {
    myDefaultDependencies.computeIfAbsent(field, k -> new HashSet<>()).add(dependency);
  }

  public Map<ClassField, Set<ClassField>> getDefaultImplDependencies() {
    return myDefaultImplDependencies;
  }

  public void addDefaultImplDependencies(ClassField field, Set<ClassField> dependencies) {
    myDefaultImplDependencies.computeIfAbsent(field, k -> new HashSet<>()).addAll(dependencies);
  }

  public void addDefaultImplDependency(ClassField field, ClassField dependency) {
    myDefaultImplDependencies.computeIfAbsent(field, k -> new HashSet<>()).add(dependency);
  }

  public void removeDefault(ClassField field) {
    myDefaults.remove(field);
    myDefaultDependencies.remove(field);
  }

  @NotNull
  @Override
  public Set<Map.Entry<ClassField, Pair<PiExpression, ClassDefinition>>> getOverriddenFields() {
    return myOverridden.entrySet();
  }

  @Nullable
  @Override
  public ClassDefinition getOverriddenOriginalClass(@NotNull CoreClassField field) {
    var pair = field instanceof ClassField ? myOverridden.get(field) : null;
    return pair == null ? null : pair.proj2;
  }

  public PiExpression getOverriddenType(ClassField field, Levels levels) {
    Pair<PiExpression, ClassDefinition> pair = myOverridden.get(field);
    return pair == null ? null : (PiExpression) new SubstVisitor(new ExprSubstitution(), castLevels(pair.proj2, levels).makeSubstitution(pair.proj2)).visitPi(pair.proj1, null);
  }

  public PiExpression getFieldType(ClassField field) {
    Pair<PiExpression, ClassDefinition> pair = myOverridden.get(field);
    return pair == null ? field.getType() : pair.proj1;
  }

  public PiExpression getFieldType(ClassField field, Levels levels) {
    Pair<PiExpression, ClassDefinition> pair = myOverridden.get(field);
    return pair == null ? field.getType(castLevels(field.getParentClass(), levels)) : (PiExpression) new SubstVisitor(new ExprSubstitution(), levels.makeSubstitution(pair.proj2)).visitPi(pair.proj1, null);
  }

  public Expression getFieldType(ClassField field, Levels levels, Expression thisExpr) {
    Pair<PiExpression, ClassDefinition> pair = myOverridden.get(field);
    PiExpression type = pair == null ? field.getType() : pair.proj1;
    return type.getCodomain().subst(new ExprSubstitution(type.getParameters(), thisExpr), levelSubstitutionFor(pair == null ? field.getParentClass() : pair.proj2, levels));
  }

  private Expression getFieldTypeSubstInfiniteFields(ClassField field, Levels levels, ReferenceExpression thisExpr) {
    Pair<PiExpression, ClassDefinition> pair = myOverridden.get(field);
    PiExpression type = pair == null ? field.getType() : pair.proj1;
    return type.getCodomain().accept(new InfiniteFieldsSubstVisitor(type.getParameters(), thisExpr, levelSubstitutionFor(pair == null ? field.getParentClass() : pair.proj2, levels)), null);
  }

  @Nullable
  @Override
  public PiExpression getOverriddenType(@NotNull CoreClassField field) {
    if (field instanceof ClassField) {
      var pair = myOverridden.get(field);
      return pair == null ? null : pair.proj1;
    } else {
      return null;
    }
  }

  @Override
  public @Nullable ClassField findField(@NotNull String fieldName) {
    for (ClassField field : myPersonalFields) {
      if (field.getName().equals(fieldName)) {
        return field;
      }
    }
    return null;
  }

  @Override
  public boolean isOverridden(@NotNull CoreClassField field) {
    return field instanceof ClassField && myOverridden.containsKey(field);
  }

  public void overrideField(ClassField field, PiExpression type, ClassDefinition originalClass) {
    myOverridden.put(field, new Pair<>(type, originalClass));
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
  public Expression getTypeWithParams(List<? super DependentLink> params, Levels levels) {
    for (ClassField field : getOverridableInfiniteFields()) {
      if (getFieldLevelOverride(field, levels) != null) {
        return new UniverseExpression(computeSort(Collections.emptyMap(), null, levels, levels.makeSubstitution(this), true, GetTypeVisitor.INSTANCE));
      }
    }
    return new UniverseExpression(mySort.subst(levels.makeSubstitution(this)));
  }

  @Override
  public ClassCallExpression getDefCall(Levels levels, List<Expression> args) {
    return new ClassCallExpression(this, levels, Collections.emptyMap());
  }

  public void clear() {
    mySuperClasses.clear();
    myNotImplementedFields.clear();
    myPersonalFields.clear();
    myImplemented.clear();
    myOverridden.clear();
    myCoercingField = null;
  }
}
