package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.ListErrorReporter;
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
import org.arend.typechecking.order.dependency.DependencyCollector;
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
  private final DependencyCollector myDependencyCollector;
  private final CollectingOrderingListener myCollector = new CollectingOrderingListener();

  public ArendCheckerImpl(ArendServerImpl server, List<? extends ModuleLocation> modules) {
    myServer = server;
    myModules = modules;
    myDependencyCollector = new DependencyCollector(myServer);
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
    Ordering ordering = prepareOrdering(DummyErrorReporter.INSTANCE, true);
    if (ordering == null) return 0;

    List<Group> groups = new ArrayList<>(myModules.size());
    for (ModuleLocation module : myModules) {
      GroupData groupData = myDependencies.get(module);
      if (groupData != null) groups.add(groupData.getRawGroup());
    }
    ordering.orderModules(groups);

    return myCollector.getElements().size();
  }

  private int prepareTypechecking(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter, boolean withInstances) {
    Ordering ordering = prepareOrdering(errorReporter, withInstances);
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

  @Override
  public int prepareTypechecking(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter) {
    return prepareTypechecking(definitions, errorReporter, true);
  }

  private Ordering prepareOrdering(ErrorReporter errorReporter, boolean withInstances) {
    ConcreteProvider concreteProvider = getConcreteProvider(errorReporter, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY);
    if (concreteProvider == null) return null;

    if (!Prelude.isInitialized()) {
      new Prelude.PreludeTypechecking(concreteProvider).typecheckDefinitions(myDependencies.get(Prelude.MODULE_LOCATION).getResolvedDefinitions().stream().map(GroupData.DefinitionData::definition).toList(), UnstoppableCancellationIndicator.INSTANCE);
    }

    myCollector.clear();
    myDependencyCollector.clear();
    return new Ordering(new InstanceProviderSet(), concreteProvider, myCollector, myDependencyCollector, new GroupComparator(myDependencies), withInstances);
  }

  @Override
  public void typecheckPrepared(@NotNull CancellationIndicator indicator, @NotNull TypecheckingListener listener) {
    if (myCollector.isEmpty()) return;
    ConcreteProvider concreteProvider = getConcreteProvider(DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY);
    if (concreteProvider == null) return;

    ListErrorReporter listErrorReporter = new ListErrorReporter();
    TypecheckingOrderingListener typechecker = new TypecheckingOrderingListener(new InstanceProviderSet(), concreteProvider, listErrorReporter, new GroupComparator(myDependencies), myServer.getExtensionProvider());
    typechecker.run(indicator, () -> {
      for (CollectingOrderingListener.Element element : myCollector.getElements()) {
        indicator.checkCanceled();
        element.feedTo(typechecker);
        listener.definitionsTypechecked(element.getAllDefinitions());
      }

      synchronized (myServer) {
        if (myServer.findChanged(myDependencies) == null) {
          listErrorReporter.reportTo(myServer.getErrorService());
          myDependencyCollector.copyTo(myServer.getDependencyCollector());
          return true;
        } else {
          return typechecker.computationInterrupted();
        }
      }
    });
  }

  @Override
  public void typecheckExtensionDefinition(@NotNull FullName definition) {
    resolveAll(DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY);
    prepareTypechecking(Collections.singletonList(definition), DummyErrorReporter.INSTANCE, false);
    typecheckPrepared(UnstoppableCancellationIndicator.INSTANCE, TypecheckingListener.EMPTY);
  }
}
