package org.arend.typechecking.visitor;

import org.arend.naming.reference.*;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.instance.ArendInstances;
import org.arend.typechecking.provider.ConcreteProvider;

import java.util.*;

public class CollectDefCallsVisitor extends VoidConcreteVisitor<Void> {
  private final Collection<TCDefReferable> myDependencies;
  private final boolean myWithBodies;
  private final ArendInstances myInstances;
  private Map<TCDefReferable, List<TCDefReferable>> myInstanceMap;
  private final ConcreteProvider myConcreteProvider;
  private Set<TCDefReferable> myExcluded;

  public CollectDefCallsVisitor(Collection<TCDefReferable> dependencies, boolean withBodies, ArendInstances instances, ConcreteProvider concreteProvider) {
    myDependencies = dependencies;
    myWithBodies = withBodies;
    myInstances = instances;
    myConcreteProvider = concreteProvider;
  }

  public CollectDefCallsVisitor(Collection<TCDefReferable> dependencies, boolean withBodies) {
    this(dependencies, withBodies, null, null);
  }

  public void addDependency(TCDefReferable dependency) {
    myDependencies.add(dependency);
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
      super.visitMetaBody(def, params);
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
    if (pattern instanceof Concrete.ConstructorPattern) {
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
        myInstanceMap.computeIfAbsent(last, k -> new ArrayList<>()).add(instance);
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
    myInstanceMap = new HashMap<>();
    for (TCDefReferable instance : myInstances.getInstances()) {
      if (myConcreteProvider.getConcrete(instance) instanceof Concrete.FunctionDefinition function) {
        TCDefReferable classRef = ArendInstances.getClassRef(function.getResultType(), myConcreteProvider);
        if (classRef != null) {
          fillInstanceMap(classRef, instance);
        }
      }
    }
  }

  private void addParameterInstances(Concrete.ClassDefinition classDef) {
    Set<TCDefReferable> visited = new HashSet<>();
    List<Concrete.ClassDefinition> toVisit = new ArrayList<>();
    toVisit.add(classDef);

    while (!toVisit.isEmpty()) {
      Concrete.ClassDefinition last = toVisit.removeLast();
      if (visited.add(last.getRef())) {
        for (Concrete.ClassElement element : last.getElements()) {
          if (element instanceof Concrete.ClassField field && field.getData().isParameterField()) {
            TCDefReferable classRef = ArendInstances.getClassRef(field.getResultType(), null);
            if (classRef != null) {
              addInstances(classRef);
            }
          }
        }
        for (Concrete.ReferenceExpression refExpr : last.getSuperClasses()) {
          if (refExpr.getReferent() instanceof TCDefReferable superClassRef && myConcreteProvider.getConcrete(superClassRef) instanceof Concrete.ClassDefinition superClass) {
            toVisit.add(superClass);
          }
        }
      }
    }
  }

  private void addInstances(TCDefReferable classRef) {
    List<TCDefReferable> instances = myInstanceMap.get(classRef);
    if (instances != null) {
      myDependencies.addAll(instances);
    }
  }

  private void addParametersClassReferences(List<? extends Concrete.Parameter> parameters) {
    for (Concrete.Parameter parameter : parameters) {
      if (parameter.getType() != null) {
        TCDefReferable classRef = ArendInstances.getClassRef(parameter.getType(), null);
        if (classRef != null) {
          addInstances(classRef);
        }
      }
    }
  }

  @Override
  public Void visitReference(Concrete.ReferenceExpression expr, Void params) {
    if (expr.getReferent() instanceof TCDefReferable ref) {
      if (myExcluded == null || !myExcluded.contains(ref)) {
        myDependencies.add(ref);
      }
      if (myInstanceMap == null && myInstances != null) {
        initializeInstances();
      }
      if (myInstanceMap != null && !myInstanceMap.isEmpty()) {
        if (ref.getKind() == GlobalReferable.Kind.FIELD) {
          addInstances(ref.getTypecheckable());
        } else {
          TCDefReferable ownerRef = ref.getTypecheckable();
          Concrete.GeneralDefinition definition = myConcreteProvider.getConcrete(ownerRef);
          if (definition != null) {
            addParametersClassReferences(definition.getParameters());
            if (definition instanceof Concrete.DataDefinition dataDef && !ownerRef.equals(ref)) {
              loop:
              for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
                for (Concrete.Constructor constructor : clause.getConstructors()) {
                  if (constructor.getData().equals(ref)) {
                    addParametersClassReferences(constructor.getParameters());
                    break loop;
                  }
                }
              }
            }
          }
          if (definition instanceof Concrete.ClassDefinition classDef) {
            addParameterInstances(classDef);
          }
        }
      }
    }
    return null;
  }
}
