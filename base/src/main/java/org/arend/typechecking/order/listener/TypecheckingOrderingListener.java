package org.arend.typechecking.order.listener;

import org.arend.core.context.binding.PersistentEvaluatingBinding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.core.context.param.TypedSingleDependentLink;
import org.arend.core.definition.*;
import org.arend.core.elimtree.ElimClause;
import org.arend.core.expr.*;
import org.arend.core.expr.visitor.VoidExpressionVisitor;
import org.arend.core.pattern.ExpressionPattern;
import org.arend.error.CountingErrorReporter;
import org.arend.ext.ArendExtension;
import org.arend.ext.core.definition.CoreFunctionDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.DefinitionListener;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.server.ArendServerResolveListener;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDefCallsVisitor;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.*;
import org.arend.typechecking.computation.BooleanComputationRunner;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.error.CycleError;
import org.arend.typechecking.error.TerminationCheckError;
import org.arend.typechecking.error.local.LocalErrorReporter;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.typechecking.instance.pool.GlobalInstancePool;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.order.Ordering;
import org.arend.typechecking.order.PartialComparator;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.arend.typechecking.patternmatching.ExtElimClause;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.termination.BaseCallGraph;
import org.arend.typechecking.termination.CollectCallVisitor;
import org.arend.typechecking.visitor.*;
import org.arend.ext.util.Pair;

import java.util.*;

public class TypecheckingOrderingListener extends BooleanComputationRunner implements OrderingListener {
  private final ArendCheckerFactory myCheckerFactory;
  private final DependencyListener myDependencyListener;
  private final Map<TCDefReferable, Suspension> mySuspensions = new HashMap<>();
  private final ErrorReporter myErrorReporter;
  private final InstanceScopeProvider myInstanceScopeProvider;
  private final Map<TCDefReferable, List<TCDefReferable>> myInstanceDependencies;
  private final ConcreteProvider myConcreteProvider;
  private final PartialComparator<TCDefReferable> myComparator;
  private final ArendExtensionProvider myExtensionProvider;
  private final Map<TCDefReferable, Concrete.ResolvableDefinition> myDesugaredDefinitions = new HashMap<>();
  private final ArendServerResolveListener myResolveListener;
  private final boolean myClearLemmas;
  private List<TCDefReferable> myCurrentDefinitions = new ArrayList<>();
  private boolean myHeadersAreOK = true;

  private record Suspension(CheckTypeVisitor typechecker, UniverseKind universeKind) {}

  public TypecheckingOrderingListener(ArendCheckerFactory factory, InstanceScopeProvider instanceScopeProvider, Map<TCDefReferable, List<TCDefReferable>> instanceDependencies, ConcreteProvider concreteProvider, ErrorReporter errorReporter, DependencyListener dependencyListener, PartialComparator<TCDefReferable> comparator, ArendExtensionProvider extensionProvider, ArendServerResolveListener resolveListener, boolean clearLemmas) {
    myCheckerFactory = factory;
    myErrorReporter = errorReporter;
    myDependencyListener = dependencyListener;
    myInstanceScopeProvider = instanceScopeProvider;
    myInstanceDependencies = instanceDependencies;
    myConcreteProvider = concreteProvider;
    myComparator = comparator;
    myExtensionProvider = extensionProvider;
    myResolveListener = resolveListener;
    myClearLemmas = clearLemmas;
  }

  public ConcreteProvider getConcreteProvider() {
    return myConcreteProvider;
  }

  public InstanceScopeProvider getInstanceScopeProvider() {
    return myInstanceScopeProvider;
  }

  @Override
  public Boolean computationInterrupted() {
    for (TCDefReferable currentDefinition : myCurrentDefinitions) {
      currentDefinition.setTypechecked(null);
      Concrete.GeneralDefinition def = myConcreteProvider.getConcrete(currentDefinition);
      if (def instanceof Concrete.DataDefinition dataDef) {
        for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
          for (Concrete.Constructor constructor : clause.getConstructors()) {
            constructor.getData().setTypechecked(null);
          }
        }
      } else if (def instanceof Concrete.ClassDefinition classDef) {
        for (Concrete.ClassElement element : classDef.getElements()) {
          if (element instanceof Concrete.ClassField classField) {
            classField.getData().setTypechecked(null);
          }
        }
      }
    }
    myCurrentDefinitions = new ArrayList<>();
    return false;
  }

  public boolean typecheckDefinitions(final Collection<? extends Concrete.ResolvableDefinition> definitions, CancellationIndicator cancellationIndicator) {
    return run(cancellationIndicator, () -> {
      Ordering ordering = new Ordering(myInstanceScopeProvider, myConcreteProvider, this, myDependencyListener, myComparator);
      for (Concrete.ResolvableDefinition definition : definitions) {
        ordering.order(definition);
      }
      return true;
    });
  }

  public boolean typecheckModules(final Collection<? extends ConcreteGroup> modules, CancellationIndicator cancellationIndicator) {
    return run(cancellationIndicator, () -> {
      new Ordering(myInstanceScopeProvider, myConcreteProvider, this, myDependencyListener, myComparator).orderModules(modules);
      return true;
    });
  }

  public boolean typecheckCollected(CollectingOrderingListener collector, CancellationIndicator cancellationIndicator) {
    if (collector.isEmpty()) return true;
    return run(cancellationIndicator, () -> {
      collector.feed(this);
      return true;
    });
  }

  public void typecheckingUnitFinished(TCDefReferable referable, Definition definition) {

  }

  private Definition newDefinition(Concrete.ResolvableDefinition definition) {
    Definition typechecked;
    switch (definition) {
      case Concrete.DataDefinition def -> {
        typechecked = new DataDefinition(def.getData());
        for (Concrete.ConstructorClause constructorClause : def.getConstructorClauses()) {
          for (Concrete.Constructor constructor : constructorClause.getConstructors()) {
            Constructor tcConstructor = new Constructor(constructor.getData(), (DataDefinition) typechecked);
            tcConstructor.setParameters(EmptyDependentLink.getInstance());
            tcConstructor.setStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
            ((DataDefinition) typechecked).addConstructor(tcConstructor);
            constructor.getData().setTypecheckedIfAbsent(tcConstructor);
          }
        }
      }
      case Concrete.BaseFunctionDefinition def -> {
        typechecked = def.getKind() == FunctionKind.CONS ? new DConstructor(def.getData()) : new FunctionDefinition(def.getData());
        ((FunctionDefinition) typechecked).setResultType(new ErrorExpression());
      }
      case Concrete.ClassDefinition def -> {
        typechecked = new ClassDefinition(def.getData());
        for (Concrete.ReferenceExpression superClass : def.getSuperClasses()) {
          if (superClass.getReferent() instanceof TCDefReferable defRef && defRef.getTypechecked() instanceof ClassDefinition superClassDef) {
            ((ClassDefinition) typechecked).addSuperClass(superClassDef);
            for (ClassField field : superClassDef.getNotImplementedFields()) {
              ((ClassDefinition) typechecked).addField(field);
            }
          }
        }
        for (Concrete.ClassElement element : def.getElements()) {
          if (element instanceof Concrete.ClassField) {
            ClassField classField = new ClassField(((Concrete.ClassField) element).getData(), (ClassDefinition) typechecked, new PiExpression(new TypedSingleDependentLink(false, "this", new ClassCallExpression((ClassDefinition) typechecked, typechecked.makeIdLevels()), true), new ErrorExpression()), null);
            classField.setStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
            ((ClassDefinition) typechecked).addPersonalField(classField);
            ((ClassDefinition) typechecked).addField(classField);
            classField.getReferable().setTypecheckedIfAbsent(classField);
          }
        }
      }
      case Concrete.MetaDefinition metaDefinition -> typechecked = new MetaTopDefinition(metaDefinition.getData());
      case null, default -> throw new IllegalStateException();
    }
    typechecked.setStatus(Definition.TypeCheckingStatus.NEEDS_TYPE_CHECKING);
    definition.getData().setTypecheckedIfAbsent(typechecked);
    return typechecked;
  }

  private void collectUsedDefinitions(TCDefReferable referable, Set<TCDefReferable> result) {
    result.add(referable);
    Concrete.GeneralDefinition generalDef = myConcreteProvider.getConcrete(referable);
    if (generalDef instanceof Concrete.ResolvableDefinition def) {
      for (TCDefReferable usedDefinition : def.getUsedDefinitions()) {
        collectUsedDefinitions(usedDefinition, result);
      }
    }
  }

  private List<FunctionDefinition> getInstances(TCDefReferable definition) {
    List<TCDefReferable> instances = myInstanceDependencies.get(definition);
    if (instances == null) {
      return Collections.emptyList();
    }

    List<FunctionDefinition> result = new ArrayList<>(instances.size());
    for (TCDefReferable instance : instances) {
      if (instance.getTypechecked() instanceof FunctionDefinition function) {
        result.add(function);
      }
    }
    return result;
  }

  private void clearLemma(Definition definition) {
    if (myClearLemmas && definition instanceof FunctionDefinition function && function.getKind() == CoreFunctionDefinition.Kind.LEMMA) {
      function.setBody(null);
    }
  }

  private void typecheckWithUse(Concrete.ResolvableDefinition definition, Set<TCDefReferable> usedDefinitions, boolean recursive, ArendExtension extension, List<Definition> typecheckedList) {
    ErrorReporter errorReporter = new LocalErrorReporter(definition.getData(), myErrorReporter);
    if (usedDefinitions != null && definition instanceof Concrete.Definition def) {
      ReplaceDefCallsVisitor visitor = new ReplaceDefCallsVisitor(usedDefinitions, definition.getData(), errorReporter);
      def.accept(visitor, null);
      recursive = visitor.isRecursive();
    }

    boolean ok = true;
    if (recursive) {
      Set<TCDefReferable> dependencies = new HashSet<>();
      definition.accept(new CollectDefCallsVisitor(dependencies, false), null);
      if (dependencies.contains(definition.getData())) {
        errorReporter.report(new CycleError(Collections.singletonList(definition.getData()), Collections.emptyMap()));
        typecheckingUnitFinished(definition.getData(), newDefinition(definition));
        ok = false;
      }
      if (definition instanceof Concrete.Definition def) {
        def.setRecursiveDefinitions(Collections.singleton(def.getData()));
      }
    }

    if (ok) {
      CheckTypeVisitor checkTypeVisitor = myCheckerFactory.create(errorReporter, null, extension, myResolveListener);
      checkTypeVisitor.setInstancePool(new GlobalInstancePool(getInstances(definition.getData()), checkTypeVisitor));
      definition = definition.accept(new ReplaceDataVisitor(), null);
      if (definition instanceof Concrete.FunctionDefinition funDef && funDef.getKind().isUse()) {
        myDesugaredDefinitions.put(funDef.getData(), funDef);
      }
      if (definition instanceof Concrete.Definition) {
        WhereVarsFixVisitor.fixDefinition(Collections.singletonList((Concrete.Definition) definition), errorReporter);
      }
      DesugarVisitor.desugar(definition, myConcreteProvider, checkTypeVisitor.getErrorReporter());
      DefinitionTypechecker typechecker = new DefinitionTypechecker(checkTypeVisitor, recursive ? Collections.singleton(definition.getData()) : Collections.emptySet());
      List<ExtElimClause> clauses = definition.accept(typechecker, null);
      Definition typechecked = definition.getData().getTypechecked();
      if (typechecked == null) {
        typechecked = newDefinition(definition);
      }
      clearLemma(typechecked);
      if (!(typechecked instanceof TopLevelDefinition || typechecked instanceof MetaTopDefinition)) {
        throw new IllegalStateException();
      }

      setParametersOriginalDefinitionsDependency(typechecked);
      if (!(definition instanceof Concrete.FunctionDefinition && ((Concrete.FunctionDefinition) definition).getKind().isCoclause()) && typechecked instanceof TopLevelDefinition) {
        FixLevelParameters.fix(Collections.singleton((TopLevelDefinition) typechecked), Collections.singleton(typechecked));
      }
      if (recursive && typechecked instanceof FunctionDefinition) {
        ((FunctionDefinition) typechecked).setRecursiveDefinitions(Collections.singleton((FunctionDefinition) typechecked));
      }
      if (recursive && typechecked instanceof DataDefinition) {
        ((DataDefinition) typechecked).setRecursiveDefinitions(Collections.singleton((DataDefinition) typechecked));
      }
      findAxiomsAndGoals(Collections.singletonList(definition), Collections.singleton(typechecked));
      if (definition instanceof Concrete.Definition def && def.isRecursive() && typechecked instanceof FunctionDefinition) {
        checkRecursiveFunctions(Collections.singletonMap((FunctionDefinition) typechecked, def), clauses == null ? Collections.emptyMap() : Collections.singletonMap((FunctionDefinition) typechecked, clauses));
      }

      typecheckingUnitFinished(definition.getData(), typechecked);
      typecheckedList.add(typechecked);
    }

    if (usedDefinitions != null) {
      List<Concrete.FunctionDefinition> funcDefinitions = new ArrayList<>();
      for (TCDefReferable usedDefinition : definition.getUsedDefinitions()) {
        Concrete.GeneralDefinition generalDef = myConcreteProvider.getConcrete(usedDefinition);
        usedDefinitions.remove(usedDefinition);
        if (generalDef instanceof Concrete.ResolvableDefinition resolvableDef) {
          typecheckWithUse(resolvableDef, usedDefinitions, false, extension, typecheckedList);
        }
        if (generalDef instanceof Concrete.FunctionDefinition funcDef) {
          funcDefinitions.add(funcDef);
        }
      }
      UseTypechecking.typecheck(funcDefinitions, errorReporter);
    }
  }

  private void addUseDependencies(Concrete.ResolvableDefinition definition) {
    for (TCDefReferable usedDefinition : definition.getUsedDefinitions()) {
      myDependencyListener.dependsOn(definition.getData(), usedDefinition);
      myDependencyListener.dependsOn(usedDefinition, definition.getData());
    }
  }

  @Override
  public void unitFound(Concrete.ResolvableDefinition definition, boolean recursive) {
    myHeadersAreOK = true;

    ArendExtension extension = myExtensionProvider.getArendExtension(definition.getData());
    Set<TCDefReferable> usedDefinitions;
    if (definition.getUsedDefinitions().isEmpty()) {
      usedDefinitions = null;
    } else {
      usedDefinitions = new HashSet<>();
      for (TCDefReferable usedDefinition : definition.getUsedDefinitions()) {
        collectUsedDefinitions(usedDefinition, usedDefinitions);
      }
    }

    myCurrentDefinitions.add(definition.getData());
    if (usedDefinitions != null) {
      myCurrentDefinitions.addAll(usedDefinitions);
    }
    List<Definition> typechecked = new ArrayList<>();
    typecheckWithUse(definition, usedDefinitions, recursive, extension, typechecked);
    addUseDependencies(definition);
    if (definition instanceof Concrete.ClassDefinition classDef) {
      DefinitionTypechecker.setDefaultDependencies(classDef);
    }
    if (extension != null) {
      DefinitionListener listener = extension.getDefinitionListener();
      if (listener != null) {
        for (Definition def : typechecked) {
          listener.typechecked(def);
        }
      }
    }
  }

  private void setParametersOriginalDefinitionsDependency(Definition definition) {
    for (Pair<TCDefReferable, Integer> pair : definition.getParametersOriginalDefinitions()) {
      myDependencyListener.dependsOn(definition.getRef(), pair.proj1);
    }
  }

  @Override
  public void cycleFound(List<Concrete.ResolvableDefinition> definitions) {
    List<TCDefReferable> cycle = new ArrayList<>();

    for (Concrete.ResolvableDefinition definition : definitions) {
      if (cycle.isEmpty() || cycle.getLast() != definition.getData()) {
        cycle.add(definition.getData());
      }

      if (definition instanceof Concrete.Definition def) {
        Definition typechecked = def.getData().getTypechecked();
        if (typechecked == null) {
          typechecked = newDefinition(def);
        }
        typechecked.addStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
        mySuspensions.remove(def.getData());
        typecheckingUnitFinished(def.getData(), typechecked);
      }
    }

    Set<TCDefReferable> cycleRefs = new HashSet<>(cycle);
    Map<GlobalReferable, List<TCDefReferable>> instances = new LinkedHashMap<>();
    for (Concrete.ResolvableDefinition definition : definitions) {
      Set<TCDefReferable> dependencies = new LinkedHashSet<>();
      definition.accept(new CollectDefCallsVisitor(null, dependencies, true, myInstanceScopeProvider.getInstancesFor(definition.getData()), myConcreteProvider, definition), null);
      dependencies.retainAll(cycleRefs);
      if (!dependencies.isEmpty()) {
        instances.put(definition.getData(), new ArrayList<>(dependencies));
      }
    }
    myErrorReporter.report(new CycleError(cycle, instances));
  }

  @Override
  public void preBodiesFound(List<Concrete.ResolvableDefinition> definitions) {
    List<Concrete.Definition> newDefs = new ArrayList<>(definitions.size());
    for (Concrete.ResolvableDefinition definition : definitions) {
      Concrete.ResolvableDefinition def = definition.accept(new ReplaceDataVisitor(), null);
      myDesugaredDefinitions.put(definition.getData(), def);
      if (def instanceof Concrete.Definition) {
        newDefs.add((Concrete.Definition) def);
      }
    }
    WhereVarsFixVisitor.fixDefinition(newDefs, myErrorReporter);
  }

  @Override
  public void headerFound(Concrete.ResolvableDefinition definition) {
    Concrete.ResolvableDefinition newDef = myDesugaredDefinitions.get(definition.getData());
    if (newDef != null) definition = newDef;
    myCurrentDefinitions.add(definition.getData());

    CountingErrorReporter countingErrorReporter = new CountingErrorReporter(myErrorReporter);
    CheckTypeVisitor visitor = myCheckerFactory.create(new LocalErrorReporter(definition.getData(), countingErrorReporter), null, myExtensionProvider.getArendExtension(definition.getData()), myResolveListener);
    visitor.setStatus(definition.getStatus().getTypecheckingStatus());
    DesugarVisitor.desugar(definition, myConcreteProvider, visitor.getErrorReporter());
    DefinitionTypechecker typechecker = new DefinitionTypechecker(visitor, definition instanceof Concrete.Definition ? ((Concrete.Definition) definition).getRecursiveDefinitions() : Collections.emptySet());
    Definition typechecked = typechecker.typecheckHeader(new GlobalInstancePool(getInstances(definition.getData()), visitor), definition);
    if (typechecked == null) return;

    UniverseKind universeKind = typechecked.getUniverseKind();
    if (typechecked instanceof TopLevelDefinition) {
      ((TopLevelDefinition) typechecked).setUniverseKind(UniverseKind.WITH_UNIVERSES);
    }
    if (typechecked.status() == Definition.TypeCheckingStatus.TYPE_CHECKING) {
      mySuspensions.put(definition.getData(), new Suspension(visitor, universeKind));
    }

    if (!typechecked.status().headerIsOK()) {
      myHeadersAreOK = false;
    }
  }

  @Override
  public void bodiesFound(List<Concrete.ResolvableDefinition> definitions) {
    Map<FunctionDefinition,Concrete.Definition> functionDefinitions = new HashMap<>();
    Map<FunctionDefinition, List<? extends ElimClause<ExpressionPattern>>> clausesMap = new HashMap<>();
    Set<DataDefinition> dataDefinitions = new HashSet<>();
    List<Concrete.ResolvableDefinition> orderedDefinitions = new ArrayList<>(definitions.size());
    List<Concrete.ResolvableDefinition> otherDefs = new ArrayList<>();
    Set<TCDefReferable> refs = new HashSet<>();
    for (Concrete.ResolvableDefinition definition : definitions) {
      Concrete.ResolvableDefinition newDef = myDesugaredDefinitions.get(definition.getData());
      if (newDef == null) newDef = definition;
      Definition typechecked = newDef.getData().getTypechecked();
      if (typechecked instanceof DataDefinition) {
        dataDefinitions.add((DataDefinition) typechecked);
        orderedDefinitions.add(newDef);
      } else {
        otherDefs.add(newDef);
      }
      refs.add(definition.getData());
      addUseDependencies(definition);
    }
    orderedDefinitions.addAll(otherDefs);

    DefinitionTypechecker typechecking = new DefinitionTypechecker(null, refs);
    for (Concrete.ResolvableDefinition definition : orderedDefinitions) {
      myCurrentDefinitions.add(definition.getData());
    }

    Set<Definition> newDefs = new HashSet<>();
    List<Pair<Definition, DefinitionListener>> listeners = new ArrayList<>();
    for (Concrete.ResolvableDefinition definition : orderedDefinitions) {
      Definition def = definition.getData().getTypechecked();
      if (def instanceof TopLevelDefinition) {
        Suspension suspension = mySuspensions.get(definition.getData());
        if (suspension != null) {
          ((TopLevelDefinition) def).setUniverseKind(suspension.universeKind);
        }
      }
    }

    for (Concrete.ResolvableDefinition definition : orderedDefinitions) {
      Definition def = definition.getData().getTypechecked();
      Suspension suspension = mySuspensions.remove(definition.getData());
      if (suspension != null) {
        newDefs.add(def);
      }
      if (myHeadersAreOK && suspension != null) {
        typechecking.setTypechecker(suspension.typechecker);
        List<? extends ElimClause<ExpressionPattern>> clauses = typechecking.typecheckBody(def, definition, dataDefinitions);
        if (def instanceof FunctionDefinition && definition instanceof Concrete.Definition) {
          functionDefinitions.put((FunctionDefinition) def, (Concrete.Definition) definition);
          if (clauses != null) {
            clausesMap.put((FunctionDefinition) def, clauses);
          }
        }
        clearLemma(def);

        ArendExtension extension = suspension.typechecker.getExtension();
        if (extension != null) {
          DefinitionListener listener = extension.getDefinitionListener();
          if (listener != null) {
            listeners.add(new Pair<>(def, listener));
          }
        }
      }
    }
    myHeadersAreOK = true;

    boolean fixLevels = true;
    Set<TopLevelDefinition> allDefinitions = new LinkedHashSet<>();
    for (Concrete.ResolvableDefinition definition : orderedDefinitions) {
      Definition typechecked = definition.getData().getTypechecked();
      if (!newDefs.contains(typechecked)) continue;
      if (typechecked instanceof FunctionDefinition) {
        ((FunctionDefinition) typechecked).setRecursiveDefinitions(allDefinitions);
        allDefinitions.add((FunctionDefinition) typechecked);
      } else if (typechecked instanceof DataDefinition) {
        ((DataDefinition) typechecked).setRecursiveDefinitions(allDefinitions);
        allDefinitions.add((DataDefinition) typechecked);
      }
      if (definition instanceof Concrete.FunctionDefinition && ((Concrete.FunctionDefinition) definition).getKind().isCoclause()) {
        fixLevels = false;
      }
    }

    if (fixLevels) {
      FixLevelParameters.fix(allDefinitions, newDefs);
    }

    if (!functionDefinitions.isEmpty()) {
      FindDefCallVisitor<DataDefinition> visitor = new FindDefCallVisitor<>(dataDefinitions, false);
      Iterator<Map.Entry<FunctionDefinition, Concrete.Definition>> it = functionDefinitions.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<FunctionDefinition, Concrete.Definition> entry = it.next();
        visitor.visitBody(entry.getKey().getActualBody(), null);
        Definition found = visitor.getFoundDefinition();
        if (found != null) {
          entry.getKey().setBody(null);
          entry.getKey().addStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
          myErrorReporter.report(new TypecheckingError("Mutually recursive function refers to data type '" + found.getName() + "'", entry.getValue()).withDefinition(entry.getKey().getReferable()));
          it.remove();
          visitor.clear();
        }
      }

      if (!functionDefinitions.isEmpty()) {
        checkRecursiveFunctions(functionDefinitions, clausesMap);
      }
    }

    for (Concrete.ResolvableDefinition definition : orderedDefinitions) {
      if (definition.getData().getTypechecked() != null && definition.getData().getTypechecked().accept(new SearchVisitor<Void>() {
        @Override
        protected CoreExpression.FindAction processDefCall(DefCallExpression expr, Void param) {
          return expr instanceof LeveledDefCallExpression && expr.getDefinition() instanceof TopLevelDefinition && allDefinitions.contains((TopLevelDefinition) expr.getDefinition()) && !((LeveledDefCallExpression) expr).getLevels().compare(expr.getDefinition().makeIdLevels(), CMP.EQ, DummyEquations.getInstance(), null) ? CoreExpression.FindAction.STOP : CoreExpression.FindAction.CONTINUE;
        }
      }, null)) {
        myErrorReporter.report(new TypecheckingError("Recursive call must have the same levels as the definition", definition).withDefinition(definition.getData()));
      }
    }

    for (TopLevelDefinition definition : allDefinitions) {
      setParametersOriginalDefinitionsDependency(definition);
    }

    findAxiomsAndGoals(orderedDefinitions, newDefs);

    for (Pair<Definition, DefinitionListener> pair : listeners) {
      pair.proj2.typechecked(pair.proj1);
    }
  }

  private void findAxiomsAndGoals(List<? extends Concrete.ResolvableDefinition> definitions, Set<Definition> newDefs) {
    Set<FunctionDefinition> axioms = new HashSet<>();
    Set<Definition> goals = new HashSet<>();
    VoidExpressionVisitor<Void> visitor = new VoidExpressionVisitor<>() {
      @Override
      public Void visitReference(ReferenceExpression expr, Void params) {
        if (expr.getBinding() instanceof PersistentEvaluatingBinding) {
          ((PersistentEvaluatingBinding) expr.getBinding()).getExpression().accept(this, params);
        }
        return null;
      }

      @Override
      public Void visitDefCall(DefCallExpression expr, Void params) {
        axioms.addAll(expr.getDefinition().getAxioms());
        goals.addAll(expr.getDefinition().getGoals());
        return super.visitDefCall(expr, params);
      }

      @Override
      public Void visitError(ErrorExpression expr, Void params) {
        if (expr.isGoal()) {
          goals.addAll(newDefs);
        }
        return null;
      }
    };

    for (Concrete.ResolvableDefinition definition : definitions) {
      Definition def = definition.getData().getTypechecked();
      if (def == null) continue;
      def.accept(visitor, null);
      if (def instanceof TopLevelDefinition topDef && newDefs.contains(def)) {
        if (definition instanceof Concrete.BaseFunctionDefinition && ((Concrete.BaseFunctionDefinition) definition).getKind() == FunctionKind.AXIOM) {
          axioms.add((FunctionDefinition) def);
        }
        topDef.setAxioms(axioms);
        goals.addAll(topDef.getGoals());
        topDef.setGoals(goals);
      }
    }
  }

  private void checkRecursiveFunctions(Map<FunctionDefinition,Concrete.Definition> definitions, Map<FunctionDefinition, ? extends List<? extends ElimClause<ExpressionPattern>>> clauses) {
    boolean ok = true;
    BaseCallGraph<Definition> definitionCallGraph = new BaseCallGraph<>();
    for (Map.Entry<FunctionDefinition, Concrete.Definition> entry : definitions.entrySet()) {
      List<? extends ElimClause<ExpressionPattern>> functionClauses = clauses.get(entry.getKey());
      definitionCallGraph.add(CollectCallVisitor.collectCalls(entry.getKey(), functionClauses == null ? Collections.emptyList() : functionClauses, definitions.keySet()));

      for (DependentLink link = entry.getKey().getParameters(); link.hasNext(); link = link.getNext()) {
        link = link.getNextTyped(null);
        if (FindDefCallVisitor.findDefinition(link.getType(), definitions.keySet()) != null) {
          myErrorReporter.report(new TypecheckingError("Mutually recursive functions are not allowed in parameters", entry.getValue()).withDefinition(entry.getKey().getReferable()));
          ok = false;
        }
      }
      if (entry.getValue() instanceof Concrete.FunctionDefinition && ((Concrete.FunctionDefinition) entry.getValue()).getBody() instanceof Concrete.CoelimFunctionBody) {
        myErrorReporter.report(new TypecheckingError("Recursive functions cannot be defined by copattern matching", entry.getValue()).withDefinition(entry.getKey().getReferable()));
        ok = false;
      }
    }

    if (!(definitionCallGraph.checkTermination().proj1)) {
      for (Definition entry : definitionCallGraph.myErrorInfo) {
        myErrorReporter.report(new TerminationCheckError(entry, definitionCallGraph.myErrorInfo, null));
      }
      ok = false;
    }

    if (!ok) {
      for (FunctionDefinition definition : definitions.keySet()) {
        definition.addStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
        definition.setBody(null);
      }
    }
  }
}
