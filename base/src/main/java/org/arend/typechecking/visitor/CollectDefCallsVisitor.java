package org.arend.typechecking.visitor;

import org.arend.extImpl.DefaultMetaDefinition;
import org.arend.naming.reference.*;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.instance.ArendInstances;
import org.arend.typechecking.provider.ConcreteProvider;

import java.util.*;

public class CollectDefCallsVisitor extends VoidConcreteVisitor<Void> {
  private Collection<TCDefReferable> myDependencies;
  private final Set<TCDefReferable> myInstanceDependencies;
  private final boolean myWithBodies;
  private ArendInstances myInstances;
  private Map<TCDefReferable, List<TCDefReferable>> myInstanceMap;
  private final ConcreteProvider myConcreteProvider;
  private final Concrete.ResolvableDefinition myDefinition;
  private final Concrete.ClassDefinition myClassDefinition;
  private Set<TCDefReferable> mySuperClasses;
  private Set<TCDefReferable> myExcluded;
  private Set<TCDefReferable> myVisitedClasses;
  private final Set<MetaReferable> myVisitedMetas = new HashSet<>();

  public CollectDefCallsVisitor(Collection<TCDefReferable> dependencies, Set<TCDefReferable> instanceDependencies, boolean withBodies, ArendInstances instances, ConcreteProvider concreteProvider, Concrete.ResolvableDefinition definition) {
    myDependencies = dependencies;
    myInstanceDependencies = instanceDependencies;
    myWithBodies = withBodies;
    myInstances = instances;
    myDefinition = definition;
    myClassDefinition = definition instanceof Concrete.ClassDefinition classDef ? classDef : definition == null || definition.getEnclosingClass() == null ? null
        : concreteProvider.getConcrete(definition.getEnclosingClass()) instanceof Concrete.ClassDefinition classDef ? classDef : null;
    myConcreteProvider = concreteProvider;
  }

  public CollectDefCallsVisitor(Collection<TCDefReferable> dependencies, boolean withBodies) {
    this(dependencies, null, withBodies, null, null, null);
  }

  public void addDependency(TCDefReferable dependency) {
    if (myDependencies != null) {
      myDependencies.add(dependency);
    }
  }

  @Override
  protected void visitFunctionBody(Concrete.BaseFunctionDefinition def, Void params) {
    if (myWithBodies) {
      super.visitFunctionBody(def, params);
    }
  }

  @Override
  protected void visitDataBody(Concrete.DataDefinition def, Void params) {
    if (myWithBodies) {
      super.visitDataBody(def, params);
    }
  }

  @Override
  protected void visitClassBody(Concrete.ClassDefinition def, Void params) {
    if (myWithBodies) {
      super.visitClassBody(def, params);
    }
  }

  @Override
  protected void visitMetaBody(Concrete.MetaDefinition def, Void params) {
    if (myWithBodies) {
      myInstances = null;
      myInstanceMap = null;
      super.visitMetaBody(def, params);
    }
  }

  @Override
  protected void visitClassHeader(Concrete.ClassDefinition def, Void params) {
    for (Concrete.ReferenceExpression superClass : def.getSuperClasses()) {
      if (superClass.getReferent() instanceof TCDefReferable ref && (myExcluded == null || !myExcluded.contains(ref))) {
        addDependency(ref);
      }
    }
  }

  @Override
  public Void visitClass(Concrete.ClassDefinition def, Void params) {
    visitClassHeader(def, null);

    myExcluded = new HashSet<>();
    for (Concrete.ClassElement element : def.getElements()) {
      if (element instanceof Concrete.ClassField) {
        myExcluded.add(((Concrete.ClassField) element).getData());
      }
    }

    visitClassBody(def, null);

    myExcluded = null;
    return null;
  }

  @Override
  protected void visitClassFieldImpl(Concrete.ClassFieldImpl classFieldImpl, Void params) {
    if (classFieldImpl.implementation != null && !(classFieldImpl instanceof Concrete.CoClauseFunctionReference && classFieldImpl.isDefault())) {
      classFieldImpl.implementation.accept(this, params);
    }
    visitElements(classFieldImpl.getSubCoclauseList(), params);
  }

  @Override
  protected void visitPattern(Concrete.Pattern pattern, Void params) {
    if (myDependencies != null && pattern instanceof Concrete.ConstructorPattern) {
      Referable constructor = ((Concrete.ConstructorPattern) pattern).getConstructor();
      if (constructor instanceof TCDefReferable) {
        myDependencies.add((TCDefReferable) constructor);
      }
    }
    super.visitPattern(pattern, null);
  }

  private void fillInstanceMap(TCDefReferable classRef, TCDefReferable instance) {
    Set<TCDefReferable> visited = new HashSet<>();
    List<TCDefReferable> toVisit = new ArrayList<>();
    toVisit.add(classRef);

    while (!toVisit.isEmpty()) {
      TCDefReferable last = toVisit.removeLast();
      if (visited.add(last)) {
        if (!(myDefinition instanceof Concrete.CoClauseFunctionDefinition function && instance.equals(function.getUseParent()))) {
          myInstanceMap.computeIfAbsent(last, k -> new ArrayList<>()).add(instance);
        }
        if (myConcreteProvider.getConcrete(last) instanceof Concrete.ClassDefinition classDef) {
          for (Concrete.ReferenceExpression refExpr : classDef.getSuperClasses()) {
            if (refExpr.getReferent() instanceof TCDefReferable superClass) {
              toVisit.add(superClass);
            }
          }
        }
      }
    }
  }

  private void initializeInstances() {
    myVisitedClasses = new HashSet<>();
    myInstanceMap = new HashMap<>();
    for (TCDefReferable instance : myInstances.getInstances()) {
      if (myConcreteProvider.getConcrete(instance) instanceof Concrete.FunctionDefinition function) {
        TCDefReferable classRef = ArendInstances.getClassRef(function.getResultType(), myConcreteProvider);
        if (classRef != null) {
          fillInstanceMap(classRef, instance);
        }
      }
    }

    if (myClassDefinition == null) {
      mySuperClasses = Collections.emptySet();
      return;
    }

    mySuperClasses = new HashSet<>();
    List<Concrete.ClassDefinition> toVisit = new ArrayList<>();
    toVisit.add(myClassDefinition);
    while (!toVisit.isEmpty()) {
      Concrete.ClassDefinition last = toVisit.removeLast();
      if (mySuperClasses.add(last.getRef())) {
        for (Concrete.ReferenceExpression refExpr : last.getSuperClasses()) {
          if (refExpr.getReferent() instanceof TCDefReferable superClassRef && myConcreteProvider.getConcrete(superClassRef) instanceof Concrete.ClassDefinition superClass) {
            toVisit.add(superClass);
          }
        }
      }
    }
  }

  private void addInstances(TCDefReferable classRef, boolean withDependencies) {
    if (!myVisitedClasses.add(classRef)) return;
    List<TCDefReferable> instances = myInstanceMap.get(classRef);
    if (instances != null) {
      if (withDependencies && myDependencies != null) {
        myDependencies.addAll(instances);
      }
      if (myInstanceDependencies != null) {
        myInstanceDependencies.addAll(instances);
        for (TCDefReferable instance : instances) {
          if (myConcreteProvider.getConcrete(instance) instanceof Concrete.FunctionDefinition function) {
            addParametersClassReferences(function.getParameters(), Collections.emptyList(), false);
          }
        }
      }
    }
  }

  private int addParametersClassReferences(List<? extends Concrete.Parameter> parameters, List<? extends Concrete.Argument> arguments, boolean withDependencies) {
    boolean[] safeParams = new boolean[parameters.size()];
    int n = 0;
    loop:
    for (int i = 0; i < parameters.size(); i++) {
      Concrete.Parameter parameter = parameters.get(i);
      boolean safeParam = true;
      for (int j = 0; j < parameter.getNumberOfParameters(); j++) {
        if (n >= arguments.size()) break loop;
        Concrete.Argument argument = arguments.get(n);
        if (parameter.isExplicit() == argument.isExplicit()) {
          n++;
          if (argument.getExpression() instanceof Concrete.HoleExpression) {
            safeParam = false;
          }
          continue;
        }
        safeParam = false;
        if (parameter.isExplicit()) {
          n = arguments.size();
          break loop;
        }
      }
      safeParams[i] = safeParam;
    }

    for (int i = 0; i < parameters.size(); i++) {
      if (safeParams[i]) continue;
      Concrete.Expression paramType = parameters.get(i).getType();
      if (paramType != null) {
        TCDefReferable classRef = ArendInstances.getClassRef(paramType, null);
        if (classRef != null && !mySuperClasses.contains(classRef)) {
          addInstances(classRef, withDependencies);
        }
      }
    }

    return n;
  }

  @Override
  public Void visitApp(Concrete.AppExpression expr, Void params) {
    if (expr.getFunction() instanceof Concrete.ReferenceExpression refExpr) {
      visitReference(refExpr, expr.getArguments());
    } else {
      expr.getFunction().accept(this, params);
    }

    for (Concrete.Argument argument : expr.getArguments()) {
      argument.getExpression().accept(this, params);
    }
    return null;
  }

  private List<Concrete.Parameter> getClassParameters(Concrete.ClassDefinition classDef) {
    List<Concrete.Parameter> result = new ArrayList<>();
    Set<TCDefReferable> visited = new HashSet<>();
    List<Concrete.ClassDefinition> toVisit = new ArrayList<>();
    toVisit.add(classDef);

    while (!toVisit.isEmpty()) {
      Concrete.ClassDefinition last = toVisit.removeLast();
      if (visited.add(last.getRef())) {
        for (Concrete.ClassElement element : last.getElements()) {
          if (element instanceof Concrete.ClassField field && field.getData().isParameterField()) {
            result.add(new Concrete.TypeParameter(field.getData().isExplicitField(), field.getResultType(), false));
          }
        }
        for (Concrete.ReferenceExpression refExpr : last.getSuperClasses()) {
          if (refExpr.getReferent() instanceof TCDefReferable superClassRef && myConcreteProvider.getConcrete(superClassRef) instanceof Concrete.ClassDefinition superClass) {
            toVisit.add(superClass);
          }
        }
      }
    }

    return result;
  }

  private void visitReference(Concrete.ReferenceExpression expr, List<? extends Concrete.Argument> arguments) {
    if (!(expr.getReferent() instanceof TCDefReferable ref)) return;

    if (myExcluded == null || !myExcluded.contains(ref)) {
      addDependency(ref);
    }

    if (ref instanceof MetaReferable metaRef && myInstances != null) {
      if (!myVisitedMetas.add(metaRef)) return;

      Concrete.Expression body = null;
      if (metaRef.getDefinition() instanceof DefaultMetaDefinition metaDef) {
        body = metaDef.getConcrete().body;
      } else if (myConcreteProvider != null && myConcreteProvider.getConcrete(metaRef) instanceof Concrete.MetaDefinition metaDef) {
        body = metaDef.body;
      }

      if (body != null) {
        Collection<TCDefReferable> prevDependencies = myDependencies;
        myDependencies = null;
        body.accept(this, null);
        myDependencies = prevDependencies;
      }

      return;
    }


    if (ref.getKind() == GlobalReferable.Kind.FIELD && !arguments.isEmpty() && !arguments.getFirst().isExplicit() && !(arguments.getFirst().getExpression() instanceof Concrete.HoleExpression)) {
      return;
    }

    if (myInstanceMap == null && myInstances != null) {
      initializeInstances();
    }

    if (myInstanceMap == null || myInstanceMap.isEmpty()) return;

    if (ref.getKind() == GlobalReferable.Kind.FIELD && mySuperClasses.contains(ref.getTypecheckable())) {
      return;
    }

    if (ref.getKind() == GlobalReferable.Kind.FIELD) {
      addInstances(ref.getTypecheckable(), true);
      return;
    }

    TCDefReferable ownerRef = ref.getTypecheckable();
    Concrete.GeneralDefinition definition = myConcreteProvider.getConcrete(ownerRef);
    if (definition != null) {
      List<? extends Concrete.Parameter> parameters;
      if (definition instanceof Concrete.ClassDefinition classDef) {
        parameters = getClassParameters(classDef);
      } else {
        if (definition instanceof Concrete.Definition def && def.enclosingClass != null) {
          List<Concrete.Parameter> newParams = new ArrayList<>();
          newParams.add(new Concrete.TypeParameter(false, new Concrete.ReferenceExpression(null, def.enclosingClass), false));
          newParams.addAll(definition.getParameters());
          parameters = newParams;
        } else {
          parameters = definition.getParameters();
        }
      }
      boolean isConstructor = definition instanceof Concrete.DataDefinition && !ownerRef.equals(ref);
      int n = addParametersClassReferences(isConstructor ? parameters.stream().map(param -> {
        Concrete.Parameter newParam = param.copy(param.getData());
        newParam.setExplicit(false);
        return newParam;
      }).toList() : parameters, arguments, true);
      if (isConstructor) {
        loop:
        for (Concrete.ConstructorClause clause : ((Concrete.DataDefinition) definition).getConstructorClauses()) {
          for (Concrete.Constructor constructor : clause.getConstructors()) {
            if (constructor.getData().equals(ref)) {
              addParametersClassReferences(constructor.getParameters(), arguments.subList(n, arguments.size()), true);
              break loop;
            }
          }
        }
      }
    }
  }

  @Override
  public Void visitReference(Concrete.ReferenceExpression expr, Void params) {
    visitReference(expr, Collections.emptyList());
    return null;
  }
}
