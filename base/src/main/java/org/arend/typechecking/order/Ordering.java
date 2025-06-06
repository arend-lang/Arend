package org.arend.typechecking.order;

import org.arend.core.definition.Definition;
import org.arend.ext.error.ErrorReporter;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.ComputationRunner;
import org.arend.typechecking.error.DefinitionOrderingError;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.arend.typechecking.order.listener.OrderingListener;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.visitor.CollectDefCallsVisitor;

import java.util.*;
import java.util.function.Consumer;

public class Ordering extends TarjanSCC<Concrete.ResolvableDefinition> {
  private final InstanceScopeProvider myInstanceScopeProvider;
  private final Map<TCDefReferable, List<TCDefReferable>> myInstanceDependencies;
  private final ConcreteProvider myConcreteProvider;
  private OrderingListener myOrderingListener;
  private final DependencyListener myDependencyListener;
  private final PartialComparator<TCDefReferable> myComparator;
  private final Set<TCDefReferable> myAllowedDependencies;
  private final ErrorReporter myErrorReporter;
  private final Stage myStage;

  private enum Stage { EVERYTHING, WITHOUT_BODIES }

  private Ordering(InstanceScopeProvider instanceScopeProvider, ConcreteProvider concreteProvider, OrderingListener orderingListener, DependencyListener dependencyListener, PartialComparator<TCDefReferable> comparator, Set<TCDefReferable> allowedDependencies, Stage stage, ErrorReporter errorReporter) {
    myInstanceScopeProvider = instanceScopeProvider;
    myConcreteProvider = concreteProvider;
    myOrderingListener = orderingListener;
    myDependencyListener = dependencyListener;
    myComparator = comparator;
    myAllowedDependencies = allowedDependencies;
    myStage = stage;
    myInstanceDependencies = stage == Stage.EVERYTHING ? new HashMap<>() : null;
    myErrorReporter = errorReporter;
  }

  private Ordering(Ordering ordering, Set<TCDefReferable> allowedDependencies, Stage stage) {
    this(ordering.myInstanceScopeProvider, ordering.myConcreteProvider, ordering.myOrderingListener, ordering.myDependencyListener, ordering.myComparator, allowedDependencies, stage, ordering.myErrorReporter);
  }

  public Ordering(InstanceScopeProvider instanceScopeProvider, ConcreteProvider concreteProvider, OrderingListener orderingListener, DependencyListener dependencyListener, PartialComparator<TCDefReferable> comparator, ErrorReporter errorReporter) {
    this(instanceScopeProvider, concreteProvider, orderingListener, dependencyListener, comparator, null, Stage.EVERYTHING, errorReporter);
  }

  public Map<TCDefReferable, List<TCDefReferable>> getInstanceDependencies() {
    return myInstanceDependencies == null ? Collections.emptyMap() : myInstanceDependencies;
  }

  public OrderingListener getListener() {
    return myOrderingListener;
  }

  public void setListener(OrderingListener listener) {
    myOrderingListener = listener;
  }

  public void orderModules(Collection<? extends ConcreteGroup> modules) {
    for (ConcreteGroup group : modules) {
      orderModule(group);
    }
  }

  public void orderModule(ConcreteGroup group) {
    if (group.referable() instanceof TCDefReferable tcReferable && getTypechecked(tcReferable) == null) {
      var def = myConcreteProvider.getConcrete(tcReferable);
      if (def instanceof Concrete.ResolvableDefinition) {
        order((Concrete.ResolvableDefinition) def);
      }
    }

    for (ConcreteStatement statement : group.statements()) {
      ConcreteGroup subgroup = statement.group();
      if (subgroup != null) {
        orderModule(subgroup);
      }
    }
    for (ConcreteGroup subgroup : group.dynamicGroups()) {
      orderModule(subgroup);
    }
  }

  public void orderExpression(Concrete.Expression expr) {
    Set<TCDefReferable> dependencies = new LinkedHashSet<>();
    expr.accept(new CollectDefCallsVisitor(dependencies, true), null);
    for (TCDefReferable dependency : dependencies) {
      TCDefReferable typecheckable = dependency.getTypecheckable();
      if (!typecheckable.isTypechecked()) {
        var def = myConcreteProvider.getConcrete(typecheckable);
        if (def instanceof Concrete.ResolvableDefinition) {
          order((Concrete.ResolvableDefinition) def);
        }
      }
    }
  }

  private Concrete.ResolvableDefinition getCanonicalRepresentative(Concrete.ResolvableDefinition definition) {
    while (definition instanceof Concrete.Definition def && def.getUseParent() != null && !(definition instanceof Concrete.CoClauseFunctionDefinition coClauseDef && coClauseDef.getKind() == FunctionKind.FUNC_COCLAUSE)) {
      var useParent = myConcreteProvider.getConcrete(def.getUseParent());
      if (useParent instanceof Concrete.ResolvableDefinition) {
        definition = (Concrete.ResolvableDefinition) useParent;
      } else {
        break;
      }
    }
    return definition;
  }

  @Override
  public void order(Concrete.ResolvableDefinition definition) {
    definition = getCanonicalRepresentative(definition);
    if (getTypechecked(definition.getData()) == null) {
      ComputationRunner.checkCanceled();
      super.order(definition);
    }
  }

  public Definition getTypechecked(TCDefReferable definition) {
    Definition typechecked = definition.getTypechecked();
    return typechecked == null || typechecked.status().needsTypeChecking() ? null : typechecked;
  }

  private void collectInUsedDefinitions(TCDefReferable referable, CollectDefCallsVisitor visitor) {
    var def = myConcreteProvider.getConcrete(referable);
    if (def instanceof Concrete.ResolvableDefinition resolvableDef) {
      resolvableDef.accept(visitor, null);
      for (TCDefReferable usedDefinition : resolvableDef.getUsedDefinitions()) {
        collectInUsedDefinitions(usedDefinition, visitor);
      }
    }
  }

  @Override
  protected boolean forDependencies(Concrete.ResolvableDefinition definition, Consumer<Concrete.ResolvableDefinition> consumer) {
    Set<TCDefReferable> dependencies = new LinkedHashSet<>();
    Set<TCDefReferable> instanceDependencies = myInstanceDependencies == null ? null : new HashSet<>();
    CollectDefCallsVisitor visitor = new CollectDefCallsVisitor(dependencies, instanceDependencies, myStage.ordinal() < Stage.WITHOUT_BODIES.ordinal(), myStage == Stage.EVERYTHING ? myInstanceScopeProvider.getInstancesFor(definition.getData()) : null, myConcreteProvider, definition);

    if (definition.getEnclosingClass() != null) {
      visitor.addDependency(definition.getEnclosingClass());
    }
    if (definition instanceof Concrete.CoClauseFunctionDefinition) {
      Referable ref = ((Concrete.CoClauseFunctionDefinition) definition).getImplementedField();
      if (ref instanceof TCDefReferable) {
        visitor.addDependency((TCDefReferable) ref);
      }
    }
    if (definition instanceof Concrete.FunctionDefinition funDef && funDef.getKind().isUse() && (!(definition instanceof Concrete.CoClauseFunctionDefinition) || ((Concrete.CoClauseFunctionDefinition) definition).getKind() == FunctionKind.CLASS_COCLAUSE)) {
      visitor.addDependency(funDef.getUseParent());
    }
    definition.accept(visitor, null);
    if (myStage.ordinal() < Stage.WITHOUT_BODIES.ordinal()) {
      for (TCDefReferable usedDefinition : definition.getUsedDefinitions()) {
        collectInUsedDefinitions(usedDefinition, visitor);
      }
    }

    if (myInstanceDependencies != null && !instanceDependencies.isEmpty()) {
      List<TCDefReferable> instances = new ArrayList<>();
      for (TCDefReferable instance : myInstanceScopeProvider.getInstancesFor(definition.getData()).getInstances()) {
        if (instanceDependencies.contains(instance)) {
          instances.add(instance);
        }
      }
      if (!instances.isEmpty()) {
        myInstanceDependencies.put(definition.getData(), instances);
        for (TCDefReferable usedDefinition : definition.getUsedDefinitions()) {
          myInstanceDependencies.put(usedDefinition, instances);
        }
      }
    }

    boolean withLoops = false;
    for (TCDefReferable referable : dependencies) {
      TCDefReferable tcReferable = referable.getTypecheckable();
      var dependency = myConcreteProvider.getConcrete(tcReferable);
      if (dependency instanceof Concrete.ResolvableDefinition) {
        dependency = getCanonicalRepresentative((Concrete.ResolvableDefinition) dependency);
      }
      if (dependency != null) {
        tcReferable = dependency.getData();
      }
      if (myAllowedDependencies != null && !myAllowedDependencies.contains(tcReferable)) {
        continue;
      }

      if (tcReferable.equals(definition.getData())) {
        if (referable.equals(tcReferable)) {
          withLoops = true;
        }
      } else {
        myDependencyListener.dependsOn(definition.getData(), tcReferable);
        if (!tcReferable.isTypechecked()) {
          if (dependency instanceof Concrete.ResolvableDefinition && !dependency.getData().isTypechecked()) {
            consumer.accept((Concrete.ResolvableDefinition) dependency);
          }
        }
      }
    }
    return withLoops;
  }

  @Override
  protected void unitFound(Concrete.ResolvableDefinition unit, boolean withLoops) {
    if (myStage.ordinal() < Stage.WITHOUT_BODIES.ordinal()) {
      myOrderingListener.unitFound(unit, withLoops);
    } else {
      if (withLoops) {
        myOrderingListener.cycleFound(Collections.singletonList(unit), false);
      } else {
        myOrderingListener.headerFound(unit);
      }
    }
  }

  @Override
  protected void sccFound(List<Concrete.ResolvableDefinition> scc) {
    if (myStage.ordinal() >= Stage.WITHOUT_BODIES.ordinal()) {
      myOrderingListener.cycleFound(scc, false);
      return;
    }
    if (scc.isEmpty()) {
      return;
    }
    if (scc.size() == 1) {
      myOrderingListener.unitFound(scc.getFirst(), true);
      return;
    }

    Set<TCDefReferable> dependencies = new HashSet<>();
    for (Concrete.ResolvableDefinition definition : scc) {
      dependencies.add(definition.getData());
    }

    boolean ok = false;
    boolean allMeta = true;
    for (Concrete.ResolvableDefinition definition : scc) {
      if (definition instanceof Concrete.ClassDefinition || !definition.getUsedDefinitions().isEmpty() || definition instanceof Concrete.FunctionDefinition function && function.getKind() == FunctionKind.INSTANCE && !(function.getBody() instanceof Concrete.ElimFunctionBody)) {
        myOrderingListener.cycleFound(scc, false);
        return;
      }
      if (definition instanceof Concrete.DataDefinition || definition instanceof Concrete.FunctionDefinition function && function.getBody() instanceof Concrete.ElimFunctionBody) {
        ok = true;
      }
      if (!(definition instanceof Concrete.MetaDefinition)) {
        allMeta = false;
      }
    }
    if (!ok && !allMeta) {
      myOrderingListener.cycleFound(scc, false);
      return;
    }

    Set<TCDefReferable> defSet = new HashSet<>();
    List<Concrete.ResolvableDefinition> defs = new ArrayList<>(scc.size());
    for (Concrete.ResolvableDefinition def : scc) {
      defs.add(def);
      defSet.add(def.getData());
      if (def instanceof Concrete.Definition) {
        ((Concrete.Definition) def).setRecursiveDefinitions(defSet);
      }
    }

    myOrderingListener.preBodiesFound(defs);
    Ordering ordering = new Ordering(this, dependencies, Stage.WITHOUT_BODIES);
    for (Concrete.ResolvableDefinition definition : scc) {
      ordering.order(definition);
    }

    if (!new DefinitionComparator(myComparator).sort(defs)) {
      myErrorReporter.report(new DefinitionOrderingError(defs));
    }

    myOrderingListener.bodiesFound(defs);
  }
}
