package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.error.MergingErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCReferable;
import org.arend.naming.resolving.CollectingResolverListener;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.resolving.typing.TypingInfoVisitor;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.server.ArendChecker;
import org.arend.term.NamespaceCommand;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.group.Group;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.order.Ordering;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.arend.typechecking.order.listener.CollectingOrderingListener;
import org.arend.typechecking.order.listener.TypecheckingOrderingListener;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.arend.util.ComputationInterruptedException;
import org.arend.util.FullName;
import org.arend.util.list.PersistentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ArendCheckerImpl implements ArendChecker {
  private final ArendServerImpl myServer;
  private final Logger myLogger = Logger.getLogger(ArendCheckerImpl.class.getName());
  private final List<? extends ModuleLocation> myModules;
  private boolean myInterrupted;
  private Map<ModuleLocation, GroupData> myDependencies;
  private ConcreteProvider myConcreteProvider;
  private final DependencyCollector myDependencyCollector;
  private final CollectingOrderingListener myCollector = new CollectingOrderingListener();

  public ArendCheckerImpl(ArendServerImpl server, List<? extends ModuleLocation> modules) {
    myServer = server;
    server.copyLogger(myLogger);
    myModules = modules;
    myDependencyCollector = new DependencyCollector(myServer);
  }

  private Map<ModuleLocation, GroupData> getDependenciesInternal(@NotNull CancellationIndicator indicator) {
    if (myDependencies == null) {
      if (myInterrupted) return null;
      myDependencies = getDependencies(myModules, indicator);
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

  private Map<ModuleLocation, GroupData> getDependencies(List<? extends ModuleLocation> modules, CancellationIndicator indicator) {
    myLogger.info(() -> "Begin calculating dependencies for " + modules);

    try {
      Map<ModuleLocation, GroupData> groups = new HashMap<>();
      Deque<ModuleLocation> toVisit = new ArrayDeque<>();

      myServer.getRequester().runUnderReadLock(() -> {
        for (ModuleLocation module : modules) {
          indicator.checkCanceled();
          myServer.getRequester().requestModuleUpdate(myServer, module);
          toVisit.add(module);
        }

        synchronized (myServer) {
          while (!toVisit.isEmpty()) {
            indicator.checkCanceled();
            ModuleLocation module = toVisit.pop();
            GroupData groupData = myServer.getGroupData(module);
            if (groupData != null) {
              if (groups.putIfAbsent(module, groupData) != null) continue;
              for (ConcreteStatement statement : groupData.getRawGroup().statements()) {
                indicator.checkCanceled();
                if (statement.command() != null && statement.command().getKind() == NamespaceCommand.Kind.IMPORT) {
                  ModuleLocation dependency = myServer.findDependency(new ModulePath(statement.command().getPath()), module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
                  if (dependency != null) toVisit.add(dependency);
                }
              }
            }
          }

          for (Map.Entry<ModuleLocation, GroupData> entry : groups.entrySet()) {
            for (ConcreteStatement statement : entry.getValue().getRawGroup().statements()) {
              if (statement.command() != null && statement.command().getKind() == NamespaceCommand.Kind.IMPORT) {
                myServer.addReverseDependencies(new ModulePath(statement.command().getPath()), entry.getKey());
              }
            }
          }

          for (Map.Entry<ModuleLocation, GroupData> entry : groups.entrySet()) {
            if (entry.getValue().getTypingInfo() == null) {
              GlobalTypingInfo typingInfo = new GlobalTypingInfo(null);
              new TypingInfoVisitor(typingInfo).processGroup(entry.getValue().getRawGroup(), myServer.getParentGroupScope(entry.getKey(), entry.getValue().getRawGroup()));
              entry.getValue().setTypingInfo(typingInfo);
              myLogger.info(() -> "Header of module '" + entry.getKey() + "' is resolved");
            }
          }
        }
      });

      groups.computeIfAbsent(Prelude.MODULE_LOCATION, k -> myServer.getGroupData(Prelude.MODULE_LOCATION));

      myLogger.info(() -> "End calculating dependencies for " + modules);
      return groups;
    } catch (ComputationInterruptedException e) {
      myLogger.info(() -> "Calculating dependencies of " + modules + " is interrupted");
      return null;
    }
  }

  private ConcreteProvider resolveModules(@NotNull List<? extends @NotNull ModuleLocation> modules, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ArendChecker.ProgressReporter<ModuleLocation> progressReporter, @Nullable Map<ModuleLocation, GroupData> dependencies, boolean resolveDependencies) {
    if (dependencies == null) return null;
    if (modules.isEmpty()) return ConcreteProvider.EMPTY;
    try {
      myLogger.info(() -> "Begin resolving modules " + modules);

      Map<GlobalReferable, Concrete.GeneralDefinition> defMap = new HashMap<>();
      ConcreteProvider concreteProvider = new SimpleConcreteProvider(defMap);
      Collection<? extends ModuleLocation> currentModules = resolveDependencies ? dependencies.keySet() : modules;
      for (ModuleLocation module : currentModules) {
        GroupData groupData = dependencies.get(module);
        if (groupData != null) {
          Collection<GroupData.DefinitionData> definitionData = groupData.getResolvedDefinitions();
          if (definitionData == null) {
            groupData.getRawGroup().traverseGroup(group -> {
              if (group instanceof ConcreteGroup cGroup) {
                Concrete.ResolvableDefinition definition = cGroup.definition();
                if (definition != null) {
                  defMap.put(cGroup.referable(), definition.accept(new ReplaceDataVisitor(true), null));
                }
              }
            });
          } else {
            for (GroupData.DefinitionData data : definitionData) {
              defMap.put(data.definition().getData(), data.definition());
            }
          }
        }
      }

      CollectingResolverListener resolverListener = new CollectingResolverListener(myServer.doCacheReferences());
      Map<ModuleLocation, ListErrorReporter> errorReporterMap = new HashMap<>();
      Map<ModuleLocation, Map<LongName, GroupData.DefinitionData>> resolverResult = new HashMap<>();
      for (ModuleLocation module : currentModules) {
        indicator.checkCanceled();
        GroupData groupData = dependencies.get(module);
        if (groupData != null && !groupData.isResolved()) {
          resolverListener.moduleLocation = module;
          ErrorReporter currentErrorReporter;
          if (groupData.isReadOnly()) {
            currentErrorReporter = DummyErrorReporter.INSTANCE;
          } else {
            ListErrorReporter listErrorReporter = new ListErrorReporter();
            errorReporterMap.put(module, listErrorReporter);
            currentErrorReporter = new MergingErrorReporter(errorReporter, listErrorReporter);
          }

          Map<LongName, GroupData.DefinitionData> definitionData = new LinkedHashMap<>();
          new DefinitionResolveNameVisitor(concreteProvider, myServer.getTypingInfo(), currentErrorReporter, resolverListener).resolveGroup(groupData.getRawGroup(), myServer.getParentGroupScope(module, groupData.getRawGroup()), PersistentList.empty(), definitionData);
          resolverResult.put(module, definitionData);

          myLogger.info(() -> "Module '" + module + "' is resolved");
        }
        progressReporter.itemProcessed(module);
      }

      synchronized (myServer) {
        ModuleLocation changedModule = findChanged(dependencies);
        if (changedModule != null) {
          myLogger.info(() -> "Version of " + changedModule + " changed; didn't resolve modules " + modules);
          return null;
        }

        for (ModuleLocation module : currentModules) {
          indicator.checkCanceled();
          GroupData groupData = dependencies.get(module);
          if (groupData != null) {
            CollectingResolverListener.ModuleCacheStructure cache = resolverListener.getCacheStructure(module);
            if (cache != null) {
              for (CollectingResolverListener.ResolvedReference resolvedReference : cache.cache()) {
                myServer.getRequester().addReference(module, resolvedReference.reference(), resolvedReference.referable());
              }
              for (CollectingResolverListener.ReferablePair pair : cache.referables()) {
                myServer.getRequester().addReference(module, pair.referable(), pair.tcReferable());
              }
              for (ModulePath modulePath : cache.importedModules()) {
                ModuleLocation dependency = myServer.findDependency(modulePath, module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, false);
                if (dependency != null) {
                  myServer.getRequester().addModuleDependency(module, dependency);
                }
              }
            }
            ListErrorReporter reporter = errorReporterMap.get(module);
            if (reporter != null) {
              myServer.getErrorService().setResolverErrors(module, reporter.getErrorList());
            }

            Map<LongName, GroupData.DefinitionData> definitionData = resolverResult.get(module);
            if (definitionData != null) {
              Map<LongName, GroupData.DefinitionData> prevData = groupData.getPreviousDefinitions();
              if (prevData != null) {
                for (Map.Entry<LongName, GroupData.DefinitionData> entry : prevData.entrySet()) {
                  GroupData.DefinitionData newData = definitionData.get(entry.getKey());
                  boolean update;
                  if (newData != null) {
                    Map<Object, Consumer<Concrete.SourceNode>> updater = new HashMap<>();
                    for (GeneralError error : myServer.getErrorService().getTypecheckingErrors(entry.getValue().definition().getData())) {
                      Object cause = error.getCause();
                      if (cause != null) updater.put(cause, error::setCauseSourceNode);
                    }
                    update = !entry.getValue().compare(newData, updater);
                  } else {
                    update = true;
                  }
                  if (update) {
                    Set<? extends TCReferable> updatedSet = myDependencyCollector.update(entry.getValue().definition().getData());
                    myLogger.info(() -> "Updated definitions " + updatedSet);
                    for (TCReferable updated : updatedSet) {
                      myServer.getErrorService().resetDefinition(updated);
                    }
                  }
                }
              }
              groupData.updateResolvedDefinitions(definitionData);
            }
          }
        }
      }

      myLogger.info(() -> "End resolving modules " + modules);
      return concreteProvider;
    } catch (ComputationInterruptedException e) {
      myLogger.info(() -> "Resolving of modules " + modules + " is interrupted");
      return null;
    }
  }

  @Override
  public void resolveModules(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter) {
    resolveModules(myModules, DummyErrorReporter.INSTANCE, indicator, progressReporter, getDependenciesInternal(indicator), false);
  }

  private ConcreteProvider getConcreteProvider(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter) {
    if (myConcreteProvider == null) {
      myConcreteProvider = resolveModules(myModules, errorReporter, indicator, progressReporter, getDependenciesInternal(indicator), true);
    }
    return myConcreteProvider;
  }

  @Override
  public void resolveAll(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter) {
    getConcreteProvider(DummyErrorReporter.INSTANCE, indicator, progressReporter);
  }

  @Override
  public int prepareTypechecking() {
    myLogger.info(() -> "Begin ordering definitions in " + myModules);

    Ordering ordering = prepareOrdering(DummyErrorReporter.INSTANCE, true);
    if (ordering == null) return 0;

    List<Group> groups = new ArrayList<>(myModules.size());
    for (ModuleLocation module : myModules) {
      GroupData groupData = myDependencies.get(module);
      if (groupData != null) groups.add(groupData.getRawGroup());
    }
    ordering.orderModules(groups);

    myLogger.info(() -> "End ordering definitions in " + myModules);
    return myCollector.getElements().size();
  }

  private int prepareTypechecking(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter, boolean withInstances) {
    myLogger.info(() -> "Begin ordering definitions " + definitions);

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

    myLogger.info(() -> "End ordering definitions " + definitions);
    return myCollector.getElements().size();
  }

  @Override
  public int prepareTypechecking(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter) {
    return prepareTypechecking(definitions, errorReporter, true);
  }

  private Ordering prepareOrdering(ErrorReporter errorReporter, boolean withInstances) {
    ConcreteProvider concreteProvider = getConcreteProvider(errorReporter, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    if (concreteProvider == null) return null;

    if (!Prelude.isInitialized()) {
      new Prelude.PreludeTypechecking(concreteProvider).typecheckDefinitions(myDependencies.get(Prelude.MODULE_LOCATION).getResolvedDefinitions().stream().map(GroupData.DefinitionData::definition).toList(), UnstoppableCancellationIndicator.INSTANCE);
    }

    myCollector.clear();
    myDependencyCollector.clear();
    return new Ordering(myServer.getInstanceScopeProvider(), concreteProvider, myCollector, myDependencyCollector, new GroupComparator(myDependencies), withInstances);
  }

  private ModuleLocation findChanged(Map<ModuleLocation, GroupData> modules) {
    for (Map.Entry<ModuleLocation, GroupData> entry : modules.entrySet()) {
      GroupData groupData = myServer.getGroupData(entry.getKey());
      if (groupData == null || !groupData.isReadOnly() && groupData.getTimestamp() != entry.getValue().getTimestamp()) {
        return entry.getKey();
      }
    }
    return null;
  }

  @Override
  public void typecheckPrepared(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter) {
    if (myCollector.isEmpty()) return;
    ConcreteProvider concreteProvider = getConcreteProvider(DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    if (concreteProvider == null) return;

    myLogger.info(() -> "Begin typechecking definitions (" + myCollector.getElements().size() + ") in " + myModules);

    ListErrorReporter listErrorReporter = new ListErrorReporter();
    TypecheckingOrderingListener typechecker = new TypecheckingOrderingListener(myServer.getInstanceScopeProvider(), concreteProvider, listErrorReporter, new GroupComparator(myDependencies), myServer.getExtensionProvider());
    typechecker.run(indicator, () -> {
      for (CollectingOrderingListener.Element element : myCollector.getElements()) {
        indicator.checkCanceled();
        element.feedTo(typechecker);
        progressReporter.itemProcessed(element.getAllDefinitions());
      }

      synchronized (myServer) {
        if (findChanged(myDependencies) == null) {
          listErrorReporter.reportTo(myServer.getErrorService());
          myDependencyCollector.copyTo(myServer.getDependencyCollector());
          return true;
        } else {
          return typechecker.computationInterrupted();
        }
      }
    });

    myLogger.info(() -> "End typechecking definitions (" + myCollector.getElements().size() + ") in " + myModules);
  }

  @Override
  public void typecheckExtensionDefinition(@NotNull FullName definition) {
    resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    prepareTypechecking(Collections.singletonList(definition), DummyErrorReporter.INSTANCE, false);
    typecheckPrepared(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }
}
