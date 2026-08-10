package org.arend.core.expr;

import org.arend.core.context.LinkList;
import org.arend.core.context.binding.Binding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.core.context.param.TypedDependentLink;
import org.arend.core.definition.*;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.pattern.ConstructorExpressionPattern;
import org.arend.core.sort.Level;
import org.arend.core.subst.*;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.naming.renamer.Renamer;
import org.arend.prelude.Prelude;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.util.SingletonList;
import org.arend.ext.util.Wrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

public class ClassCallExpression extends LeveledDefCallExpression implements CoreClassCallExpression {
  private final ClassCallBinding myThisBinding = new ClassCallBinding();
  private final Map<ClassField, Expression> myImplementations;

  public class ClassCallBinding implements Binding {
    @Override
    public String getName() {
      return "this";
    }

    @NotNull
    @Override
    public ClassCallExpression getType() {
      return ClassCallExpression.this;
    }

    @Override
    public void strip(StripVisitor stripVisitor) {
      ClassCallExpression.this.accept(stripVisitor, null);
    }

    @Override
    public void subst(InPlaceLevelSubstVisitor substVisitor) {
      ClassCallExpression.this.accept(substVisitor, null);
    }

    @Override
    public boolean isHidden() {
      return true;
    }
  }

  public ClassCallExpression(ClassDefinition definition, Levels levels) {
    super(definition, levels);
    myImplementations = Collections.emptyMap();
  }

  public ClassCallExpression(ClassDefinition definition, Levels levels, Map<ClassField, Expression> implementations) {
    super(definition, levels);
    assert implementations instanceof LinkedHashMap || implementations.size() <= 1;
    myImplementations = implementations;
  }

  @NotNull
  @Override
  public ClassCallBinding getThisBinding() {
    return myThisBinding;
  }

  @NotNull
  public Levels getLevels(ClassDefinition superClass) {
    return getDefinition().castLevels(superClass, getLevels());
  }

  @Override
  public @NotNull ClassCallExpression toSuperClass(@NotNull CoreClassDefinition superClass) {
    if (!(superClass instanceof ClassDefinition superClassDef && getDefinition().isSubClassOf(superClass))) throw new IllegalArgumentException();
    Levels levels = getLevels(superClassDef);
    int s = getDefinition().getLevelParameters().size();
    int m = getLevels().size() - s;
    if (m > 0) {
      List<ClassField> infiniteFields = getDefinition().getOverridableInfiniteFields(myImplementations.keySet());
      List<ClassField> superInfiniteFields = superClassDef.getOverridableInfiniteFields(myImplementations.keySet());
      int n = 0;
      for (; n < superInfiniteFields.size() && n < infiniteFields.size() && n < m; n++) {
        if (superInfiniteFields.get(n) != infiniteFields.get(n)) break;
      }
      if (n > 0) {
        List<Level> newLevels = new ArrayList<>();
        newLevels.addAll(levels.toList());
        newLevels.addAll(getLevels().toList().subList(s, s + n));
        levels = new ListLevels(newLevels);
      }
    }

    Map<ClassField, Expression> implementations = new LinkedHashMap<>();
    if (!myImplementations.isEmpty()) {
      for (ClassField field : superClassDef.getNotImplementedFields()) {
        if (getDefinition().isImplemented(field)) {
          break;
        }
        Expression impl = myImplementations.get(field);
        if (impl != null) {
          implementations.put(field, impl);
        }
      }
    }

    return new ClassCallExpression(superClassDef, levels, implementations);
  }

  public void fixOrderOfImplementations() {
    if (myImplementations.size() <= 1) return;
    Map<ClassField, Expression> newImpls = new LinkedHashMap<>();
    for (ClassField field : getDefinition().getNotImplementedFields()) {
      Expression impl = myImplementations.get(field);
      if (impl != null) {
        newImpls.put(field, impl);
      }
    }
    myImplementations.clear();
    myImplementations.putAll(newImpls);
  }

  public void removeDependencies(Set<? extends ClassField> originalSet) {
    boolean updated = myImplementations.size() < originalSet.size();
    if (!updated) {
      return;
    }

    Set<ClassField> removed = new HashSet<>(originalSet);
    removed.removeAll(myImplementations.keySet());
    while (updated) {
      updated = false;
      for (Iterator<Map.Entry<ClassField, Expression>> iterator = myImplementations.entrySet().iterator(); iterator.hasNext(); ) {
        Map.Entry<ClassField, Expression> entry = iterator.next();
        if (entry.getKey().getResultType().findBinding(removed) != null) {
          iterator.remove();
          removed.add(entry.getKey());
          updated = true;
        }
      }
    }
  }

  public Map<ClassField, Expression> getImplementedHere() {
    return myImplementations;
  }

  @NotNull
  @Override
  public Collection<? extends Map.Entry<? extends CoreClassField, ? extends CoreExpression>> getImplementations() {
    return myImplementations.entrySet();
  }

  @Override
  public @Nullable AbsExpression getAbsImplementation(@NotNull CoreClassField field) {
    if (!(field instanceof ClassField)) {
      throw new IllegalArgumentException();
    }
    Expression impl = myImplementations.get(field);
    return impl != null ? new AbsExpression(myThisBinding, impl) : getDefinition().getImplementation(field);
  }

  @Override
  public Expression getAbsImplementationHere(@NotNull CoreClassField field) {
    return field instanceof ClassField ? myImplementations.get(field) : null;
  }

  @Override
  public @Nullable Expression getImplementationHere(@NotNull CoreClassField field, @NotNull CoreExpression thisExpr) {
    if (!(field instanceof ClassField && thisExpr instanceof Expression)) {
      throw new IllegalArgumentException();
    }
    Expression expr = myImplementations.get(field);
    return expr != null ? expr.subst(myThisBinding, (Expression) thisExpr) : null;
  }

  @Override
  public @Nullable Expression getImplementation(@NotNull CoreClassField field, @NotNull CoreExpression thisExpr) {
    if (!(field instanceof ClassField && thisExpr instanceof Expression)) {
      throw new IllegalArgumentException();
    }
    Expression expr = myImplementations.get(field);
    if (expr != null) {
      return expr.subst(myThisBinding, (Expression) thisExpr);
    }
    AbsExpression impl = getDefinition().getImplementation(field);
    return impl == null ? null : impl.apply((Expression) thisExpr, getLevelSubstitution());
  }

  public @NotNull Expression getFieldType(@NotNull ClassField field, @NotNull Levels levels) {
    Expression type = getDefinition().getFieldType(field, levels, new ReferenceExpression(myThisBinding));
    Level overrideLevel = getDefinition().getFieldLevelOverride(field, levels, myImplementations.keySet());
    return overrideLevel == null ? type : type.replaceInfinityLevel(overrideLevel);
  }

  public @NotNull Expression getFieldType(@NotNull ClassField field) {
    return getFieldType(field, getLevels());
  }

  public @NotNull Expression getFieldType(@NotNull ClassField field, @NotNull Expression thisExpr) {
    Expression type = getDefinition().getFieldType(field, getLevels(), thisExpr);
    Level overrideLevel = getFieldLevelOverride(field);
    return overrideLevel == null ? type : type.replaceInfinityLevel(overrideLevel);
  }

  public @NotNull PiExpression getFieldPiType(@NotNull ClassField field) {
    PiExpression type = getDefinition().getFieldType(field, getLevels());
    Level overrideLevel = getFieldLevelOverride(field);
    return overrideLevel == null ? type : type.replaceInfinityLevel(overrideLevel);
  }

  private static void checkImplementation(CoreClassField field, Expression type) {
    type = type.normalize(NormalizationMode.WHNF);
    if (!(type instanceof CoreClassCallExpression && ((CoreClassCallExpression) type).getDefinition().isSubClassOf(field.getParentClass()))) {
      throw new IllegalArgumentException("Expected an expression of type '" + field.getParentClass().getName() + "'");
    }
  }

  @Override
  public @Nullable CoreExpression getImplementationHere(@NotNull CoreClassField field, @NotNull TypedExpression thisExpr) {
    if (!(field instanceof ClassField)) {
      throw new IllegalArgumentException();
    }

    Expression expr = myImplementations.get(field);
    if (expr == null) {
      return null;
    }

    TypecheckingResult result = TypecheckingResult.fromChecked(thisExpr);
    checkImplementation(field, result.type);
    return expr.subst(myThisBinding, result.expression);
  }

  @Override
  public @Nullable CoreExpression getImplementation(@NotNull CoreClassField field, @NotNull TypedExpression thisExpr) {
    if (!(field instanceof ClassField)) {
      throw new IllegalArgumentException();
    }
    TypecheckingResult result = TypecheckingResult.fromChecked(thisExpr);

    Expression expr = myImplementations.get(field);
    if (expr != null) {
      checkImplementation(field, result.type);
      return expr.subst(myThisBinding, result.expression);
    }

    AbsExpression impl = getDefinition().getImplementation(field);
    if (impl == null) {
      return null;
    }
    checkImplementation(field, result.type);
    return impl.apply(result.expression, getLevelSubstitution());
  }

  @Override
  public @Nullable Expression getClosedImplementation(@NotNull CoreClassField field) {
    if (!(field instanceof ClassField)) {
      throw new IllegalArgumentException();
    }
    Expression expr = myImplementations.get(field);
    if (expr != null) {
      return expr.removeUnusedBinding(myThisBinding);
    }
    AbsExpression impl = getDefinition().getImplementation(field);
    Expression result = impl == null ? null : impl.getBinding() == null ? impl.getExpression() : impl.getExpression().removeUnusedBinding(impl.getBinding());
    return result == null ? null : result.subst(getLevelSubstitution());
  }

  @Override
  public boolean isImplementedHere(@NotNull CoreClassField field) {
    return field instanceof ClassField && myImplementations.containsKey(field);
  }

  @Override
  public boolean isImplemented(@NotNull CoreClassField field) {
    return field instanceof ClassField && (myImplementations.containsKey(field) || getDefinition().isImplemented(field));
  }

  @Override
  public boolean isInfinityLevel() {
    for (ClassField field : getDefinition().getNotImplementedFields()) {
      if (field.isInfiniteField() && !myImplementations.containsKey(field) && getFieldLevelOverride(field) == null) {
        return true;
      }
    }
    return false;
  }

  public @Nullable Level getFieldLevelOverride(ClassField field) {
    return getDefinition().getFieldLevelOverride(field, getLevels(), myImplementations.keySet());
  }

  public boolean isUnit() {
    return myImplementations.size() == getDefinition().getNumberOfNotImplementedFields();
  }

  public List<ClassField> getNotImplementedFields() {
    List<ClassField> result = new ArrayList<>();
    for (ClassField field : getDefinition().getNotImplementedFields()) {
      if (!myImplementations.containsKey(field)) {
        result.add(field);
      }
    }
    return result;
  }

  public int getNumberOfNotImplementedFields() {
    return getDefinition().getNumberOfNotImplementedFields() - myImplementations.size();
  }

  public void copyImplementationsFrom(ClassCallExpression classCall) {
    if (classCall.myImplementations.isEmpty()) {
      return;
    }

    ReferenceExpression thisExpr = new ReferenceExpression(myThisBinding);
    for (Map.Entry<ClassField, Expression> entry : classCall.myImplementations.entrySet()) {
      myImplementations.put(entry.getKey(), entry.getValue().subst(classCall.myThisBinding, thisExpr));
    }
  }

  @Override
  public @NotNull DependentLink getClassFieldParameters() {
    Map<ClassField, Expression> implementations = new LinkedHashMap<>();
    NewExpression newExpr = new NewExpression(null, new ClassCallExpression(getDefinition(), getLevels(), implementations));
    newExpr.getClassCall().copyImplementationsFrom(this);

    Set<? extends ClassField> fields = getDefinition().getNotImplementedFields();
    if (fields.isEmpty()) {
      return EmptyDependentLink.getInstance();
    }

    LinkList list = new LinkList();
    for (ClassField field : fields) {
      if (myImplementations.containsKey(field)) {
        continue;
      }

      PiExpression piExpr = getFieldPiType(field);
      Binding thisBinding = piExpr.getBinding();
      Expression type = piExpr.getCodomain().accept(new SubstVisitor(new ExprSubstitution(thisBinding, newExpr), LevelSubstitution.EMPTY) {
        private Expression makeNewExpression(Expression arg, Expression type) {
          arg = arg.getUnderlyingExpression();
          boolean ok = arg instanceof ReferenceExpression && ((ReferenceExpression) arg).getBinding() == thisBinding;
          if (!ok && arg instanceof NewExpression newExpr && newExpr.getRenewExpression() != null && newExpr.getClassCall().getImplementedHere().isEmpty()) {
            ReferenceExpression refExpr = newExpr.getRenewExpression().cast(ReferenceExpression.class);
            if (refExpr != null && refExpr.getBinding() == thisBinding) {
              ok = true;
            }
          }
          if (ok) {
            type = type.normalize(NormalizationMode.WHNF);
            if (type instanceof ClassCallExpression classCall && getDefinition().isSubClassOf(classCall.getDefinition())) {
              Map<ClassField, Expression> subImplementations = new LinkedHashMap<>(classCall.getImplementedHere());
              ClassCallExpression resultClassCall = new ClassCallExpression(classCall.getDefinition(), getLevels(classCall.getDefinition()), subImplementations);
              Expression resultRef = new ReferenceExpression(resultClassCall.myThisBinding);
              for (ClassField field : classCall.getDefinition().getNotImplementedFields()) {
                Expression impl = getImplementation(field, resultRef);
                if (impl != null) {
                  subImplementations.put(field, impl);
                } else {
                  impl = implementations.get(field);
                  if (impl != null) {
                    subImplementations.put(field, impl);
                  }
                }
              }
              return new NewExpression(null, resultClassCall);
            }
          }
          return null;
        }

        private List<Expression> visitArgs(List<? extends Expression> args, DependentLink params) {
          List<Expression> newArgs = new ArrayList<>(args.size());
          for (Expression arg : args) {
            Expression newArg = makeNewExpression(arg, params.getType());
            newArgs.add(newArg != null ? newArg : arg.accept(this, null));
            params = params.getNext();
          }
          return newArgs;
        }

        @Override
        public Expression visitDefCall(DefCallExpression expr, Void params) {
          assert expr instanceof LeveledDefCallExpression;
          List<Expression> newArgs = visitArgs(expr.getDefCallArguments(), expr.getDefinition().getParameters());
          return expr.getDefinition().getDefCall(((LeveledDefCallExpression) expr).getLevels().subst(getLevelSubstitution()), newArgs);
        }

        private Constructor constructor;
        private Map<Wrapper<Expression>, Expression> constructorArgMap = Collections.emptyMap();

        @Override
        public Expression visitFieldCall(FieldCallExpression expr, Void params) {
          if (expr.getDefinition().isProperty()) {
            Expression arg = expr.getArgument().getUnderlyingExpression();
            if (arg instanceof ReferenceExpression && ((ReferenceExpression) arg).getBinding() == thisBinding) {
              Expression impl = implementations.get(expr.getDefinition());
              if (impl != null) {
                return impl;
              }
            }
          }

          Expression arg = expr.getArgument().getUnderlyingExpression();
          if (arg instanceof ReferenceExpression && ((ReferenceExpression) arg).getBinding() == thisBinding) {
            Expression fieldImpl = newExpr.getClassCall().myImplementations.get(field);
            if (fieldImpl != null) {
              return expr.subst(myThisBinding, newExpr).accept(this, null);
            }
          }
          return super.visitFieldCall(expr, null);
        }

        @Override
        protected Expression preVisitConCall(ConCallExpression expr, Void params) {
          constructor = expr.getDefinition();
          constructorArgMap = Collections.emptyMap();
          DependentLink param = expr.getDefinition().getParameters();
          for (Expression arg : expr.getDefCallArguments()) {
            Expression newExpr = makeNewExpression(arg, param.getType());
            if (newExpr != null) {
              if (constructorArgMap.isEmpty()) constructorArgMap = new HashMap<>();
              constructorArgMap.put(new Wrapper<>(arg), newExpr);
            }
            param = param.getNext();
          }
          return null;
        }

        @Override
        protected List<Expression> visitDataTypeArguments(List<? extends Expression> args, Void params) {
          return visitArgs(args, constructor.getDataTypeParameters());
        }

        @Override
        protected Expression visit(Expression expr, Void params) {
          Expression result = constructorArgMap.get(new Wrapper<>(expr));
          return result != null ? result : super.visit(expr, params);
        }

        @Override
        public Expression visitConCall(ConCallExpression expr, Void params) {
          if (expr.getDefCallArguments().isEmpty()) {
            return ConCallExpression.make(expr.getDefinition(), expr.getLevels().subst(getLevelSubstitution()), visitArgs(expr.getDataTypeArguments(), expr.getDefinition().getDataTypeParameters()), Collections.emptyList());
          } else {
            return super.visitConCall(expr, params);
          }
        }

        @Override
        public Expression visitClassCall(ClassCallExpression expr, Void params) {
          Map<ClassField, Expression> fieldSet = new LinkedHashMap<>();
          ClassCallExpression result = new ClassCallExpression(expr.getDefinition(), expr.getLevels().subst(getLevelSubstitution()), fieldSet);
          getExprSubstitution().add(expr.getThisBinding(), new ReferenceExpression(result.getThisBinding()));
          for (Map.Entry<ClassField, Expression> entry : expr.getImplementedHere().entrySet()) {
            Expression newArg = makeNewExpression(entry.getValue(), entry.getKey().getType().getCodomain());
            fieldSet.put(entry.getKey(), newArg != null ? newArg : entry.getValue().accept(this, null));
          }
          getExprSubstitution().remove(expr.getThisBinding());
          return result;
        }
      }, null);

      DependentLink link = new TypedDependentLink(true, Renamer.getNameFromType(type, field.getName()), type, EmptyDependentLink.getInstance());
      implementations.put(field, new ReferenceExpression(link));
      list.append(link);
    }

    return list.getFirst();
  }

  @Override
  public BigInteger getUseLevel() {
    return getDefinition().getUseLevel(myImplementations, myThisBinding, false);
  }

  @NotNull
  @Override
  public ClassDefinition getDefinition() {
    return (ClassDefinition) super.getDefinition();
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitClassCall(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitClassCall(this, param1, param2);
  }

  @Override
  public <P, R> R accept(@NotNull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitClassCall(this, params);
  }

  private static class ConstructorWithDataArgumentsImpl implements ConstructorWithDataArguments {
    private final DConstructor myConstructor;
    private final Expression myLength;
    private final Binding myThisBinding;
    private final Expression myElementsType;
    private DependentLink myParameters;

    private ConstructorWithDataArgumentsImpl(DConstructor constructor, Expression length, Binding thisBinding, Expression elementsType) {
      myConstructor = constructor;
      myLength = length;
      myThisBinding = thisBinding;
      myElementsType = elementsType;
    }

    @Override
    public @NotNull Definition getConstructor() {
      return myConstructor;
    }

    @Override
    public @NotNull List<? extends Expression> getDataTypeArguments() {
      return myElementsType == null ? (myLength == null ? Collections.emptyList() : new SingletonList<>(myLength)) : myConstructor == Prelude.EMPTY_ARRAY ? new SingletonList<>(myElementsType) : Arrays.asList(myLength, myElementsType);
    }

    @Override
    public @NotNull DependentLink getParameters() {
      if (myParameters == null) {
        myParameters = myConstructor.getArrayParameters(myLength, myThisBinding, myElementsType);
      }
      return myParameters;
    }
  }

  @Override
  public boolean computeMatchedConstructorsWithDataArguments(List<? super ConstructorWithDataArguments> result) {
    List<ConstructorWithDataArguments> cons = computeMatchedConstructorsWithDataArguments();
    if (cons == null) return false;
    result.addAll(cons);
    return true;
  }

  @Override
  public @Nullable List<ConstructorWithDataArguments> computeMatchedConstructorsWithDataArguments() {
    if (getDefinition() != Prelude.DEP_ARRAY) return null;
    List<ConstructorWithDataArguments> result = new ArrayList<>(2);
    Boolean isEmpty = ConstructorExpressionPattern.isArrayEmpty(this);
    Expression length = getAbsImplementationHere(Prelude.ARRAY_LENGTH);
    Expression elementsType = getAbsImplementationHere(Prelude.ARRAY_ELEMENTS_TYPE);
    if (isEmpty == null || isEmpty.equals(true)) {
      result.add(new ConstructorWithDataArgumentsImpl(Prelude.EMPTY_ARRAY, length, myThisBinding, elementsType));
    }
    if (isEmpty == null || isEmpty.equals(false)) {
      result.add(new ConstructorWithDataArgumentsImpl(Prelude.ARRAY_CONS, length, myThisBinding, elementsType));
    }
    return result;
  }
}
