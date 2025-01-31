package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.server.ArendChecker;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.Group;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.instance.provider.InstanceProviderSet;
import org.arend.typechecking.order.Ordering;
import org.arend.typechecking.order.dependency.DummyDependencyListener;
import org.arend.typechecking.order.listener.CollectingOrderingListener;
import org.arend.typechecking.order.listener.TypecheckingOrderingListener;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.util.FullName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ArendCheckerImpl implements ArendChecker {
  private final ArendServerImpl myServer;
  private final List<? extends ModuleLocation> myModules;
  private boolean myInterrupted;
  private Map<ModuleLocation, GroupData> myDependencies;
  private ConcreteProvider myConcreteProvider;
  private final CollectingOrderingListener myCollector = new CollectingOrderingListener();

  public ArendCheckerImpl(ArendServerImpl server, List<? extends ModuleLocation> modules) {
    myServer = server;
    myModules = modules;
  }

  private Map<ModuleLocation, GroupData> getDependenciesInternal(@NotNull CancellationIndicator indicator) {
    if (myDependencies == null) {
      if (myInterrupted) return null;
      myDependencies = myServer.getDependencies(myModules, indicator);
      if (myDependencies == null) {
        myInterrupted = true;
      }
    }
    return myDependencies;
  }

  @Override
  public @Nullable Set<ModuleLocation> getDependencies(@NotNull CancellationIndicator indicator) {
    var dependencies = getDependenciesInternal(indicator);
    return dependencies == null ? null : Collections.unmodifiableSet(dependencies.keySet());
  }

  @Override
  public void resolveModules(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {
    myServer.resolveModules(myModules, errorReporter, indicator, listener, getDependenciesInternal(indicator), false, true);
  }

  private ConcreteProvider getConcreteProvider(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {
    if (myConcreteProvider == null) {
      myConcreteProvider = myServer.resolveModules(myModules, errorReporter, indicator, listener, getDependenciesInternal(indicator), true, false);
    }
    return myConcreteProvider;
  }

  @Override
  public void resolveAll(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {
    getConcreteProvider(errorReporter, indicator, listener);
  }

  @Override
  public int prepareTypechecking() {
    Ordering ordering = prepareOrdering(DummyErrorReporter.INSTANCE);
    if (ordering == null) return 0;

    List<Group> groups = new ArrayList<>(myModules.size());
    for (ModuleLocation module : myModules) {
      GroupData groupData = myDependencies.get(module);
      if (groupData != null) groups.add(groupData.getRawGroup());
    }
    ordering.orderModules(groups);

    return myCollector.getElements().size();
  }

  @Override
  public int prepareTypechecking(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter) {
    Ordering ordering = prepareOrdering(errorReporter);
    if (ordering == null) return 0;

    for (FullName definition : definitions) {
      GroupData groupData = myServer.getGroupData(definition.module);
      Referable ref = groupData == null ? null : Scope.resolveName(groupData.getFileScope(), definition.longName.toList());
      Concrete.GeneralDefinition def = ref instanceof GlobalReferable ? myConcreteProvider.getConcrete((GlobalReferable) ref) : null;
      if (def instanceof Concrete.ResolvableDefinition) {
        ordering.order((Concrete.ResolvableDefinition) def);
      } else {
        errorReporter.report(new DefinitionNotFoundError(definition));
      }
    }

    return myCollector.getElements().size();
  }

  private Ordering prepareOrdering(ErrorReporter errorReporter) {
    ConcreteProvider concreteProvider = getConcreteProvider(errorReporter, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY);
    if (concreteProvider == null) return null;

    if (!Prelude.isInitialized()) {
      new Prelude.PreludeTypechecking(concreteProvider).typecheckDefinitions(myDependencies.get(Prelude.MODULE_LOCATION).getResolvedDefinitions().stream().map(GroupData.DefinitionData::definition).toList(), UnstoppableCancellationIndicator.INSTANCE);
    }

    myCollector.clear();
    return new Ordering(new InstanceProviderSet(), concreteProvider, myCollector, DummyDependencyListener.INSTANCE, new GroupComparator(myDependencies));
  }

  @Override
  public void typecheckPrepared(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull TypecheckingListener listener) {
    if (myCollector.isEmpty()) return;
    ConcreteProvider concreteProvider = getConcreteProvider(errorReporter, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY);
    if (concreteProvider == null) return;

    TypecheckingOrderingListener typechecker = new TypecheckingOrderingListener(new InstanceProviderSet(), concreteProvider, errorReporter, new GroupComparator(myDependencies), myServer.getExtensionProvider());
    typechecker.run(indicator, () -> {
      for (CollectingOrderingListener.Element element : myCollector.getElements()) {
        indicator.checkCanceled();
        element.feedTo(typechecker);
        listener.definitionsTypechecked(element.getAllDefinitions());
      }
      if (myServer.findChanged(myDependencies) != null) {
        return typechecker.computationInterrupted();
      }
      return true;
    });
  }
}
