package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.module.ModuleLocation;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.CollectingResolverListener;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.resolving.typing.TypingInfoVisitor;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.server.ArendChecker;
import org.arend.server.ProgressReporter;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.concrete.ReplaceTCRefVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.*;
import org.arend.typechecking.order.Ordering;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.arend.typechecking.order.listener.CollectingOrderingListener;
import org.arend.typechecking.order.listener.TypecheckingOrderingListener;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.arend.typechecking.visitor.ArendCheckerFactory;
import org.arend.util.ComputationInterruptedException;
import org.arend.module.FullName;
import org.arend.util.list.PersistentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ArendCheckerImpl implements ArendChecker {
  private final ArendServerImpl myServer;
  private static final Logger myLogger = Logger.getLogger(ArendCheckerImpl.class.getName());
  private final List<? extends ModuleLocation> myModules;
  private boolean myInterrupted;
  private Map<ModuleLocation, GroupData> myDependencies;
  private ConcreteProvider myConcreteProvider;
  private final ReentrantLock myTCDefLock = new ReentrantLock();

  public ArendCheckerImpl(ArendServerImpl server, List<? extends ModuleLocation> modules) {
    myServer = server;
    myModules = modules;
  }

  static Logger getLogger() {
    return myLogger;
  }

  private void withTCDefLock(Runnable runnable) {
    myTCDefLock.lock();
    try {
      runnable.run();
    } finally {
      myTCDefLock.unlock();
    }
  }

  private Map<ModuleLocation, GroupData> getDependencies(@NotNull CancellationIndicator indicator) {
    if (myDependencies == null) {
      if (myInterrupted) return null;
      myDependencies = getDependencies(myModules, indicator);
      if (myDependencies == null) {
        myInterrupted = true;
      }
    }
    return myDependencies;
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
                if (statement.command() != null && statement.command().isImport()) {
                  ModuleLocation dependency = myServer.findModule(new ModulePath(statement.command().module().getPath()), module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
                  if (dependency != null) toVisit.add(dependency);
                }
              }
            }
          }

          for (Map.Entry<ModuleLocation, GroupData> entry : groups.entrySet()) {
            for (ConcreteStatement statement : entry.getValue().getRawGroup().statements()) {
              if (statement.command() != null && statement.command().isImport()) {
                myServer.addReverseDependencies(new ModulePath(statement.command().module().getPath()), entry.getKey());
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

  private ConcreteProvider resolveModules(@NotNull List<? extends @NotNull ModuleLocation> modules, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter, @Nullable Map<ModuleLocation, GroupData> dependencies, boolean resolveDependencies) {
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
          Collection<DefinitionData> definitionData = groupData.getResolvedDefinitions();
          if (definitionData == null) {
            groupData.getRawGroup().traverseGroup(group -> {
              Concrete.ResolvableDefinition definition = group.definition();
              if (definition != null) {
                defMap.put(group.referable(), definition.accept(new ReplaceDataVisitor(true), null));
              }
            });
          } else {
            for (DefinitionData data : definitionData) {
              defMap.put(data.definition().getData(), data.definition());
            }
          }
        }
      }

      CollectingResolverListener resolverListener = new CollectingResolverListener(myServer.doCacheReferences());
      Map<ModuleLocation, ListErrorReporter> errorReporterMap = new HashMap<>();
      Map<ModuleLocation, Map<LongName, DefinitionData>> resolverResult = new HashMap<>();
      progressReporter.beginProcessing(currentModules.size());
      for (ModuleLocation module : currentModules) {
        indicator.checkCanceled();
        progressReporter.beginItem(module);
        GroupData groupData = dependencies.get(module);
        if (groupData != null && !groupData.isResolved()) {
          resolverListener.moduleLocation = module;
          ListErrorReporter listErrorReporter = new ListErrorReporter();
          errorReporterMap.put(module, listErrorReporter);
          Map<LongName, DefinitionData> definitionData = new LinkedHashMap<>();
          new DefinitionResolveNameVisitor(concreteProvider, myServer.getTypingInfo(), listErrorReporter, resolverListener).resolveGroup(groupData.getRawGroup(), myServer.getParentGroupScope(module, groupData.getRawGroup()), PersistentList.empty(), definitionData);
          resolverResult.put(module, definitionData);

          myLogger.info(() -> "Module '" + module + "' is resolved");
        }
        progressReporter.endItem(module);
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
              for (ModulePath modulePath : cache.importedModules()) {
                ModuleLocation dependency = myServer.findModule(modulePath, module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, false);
                if (dependency != null) {
                  myServer.getRequester().addModuleDependency(module, dependency);
                }
              }
            }
            ListErrorReporter reporter = errorReporterMap.get(module);
            if (reporter != null) {
              myServer.getErrorService().setResolverErrors(module, reporter.getErrorList());
            }

            Map<LongName, DefinitionData> definitionData = resolverResult.get(module);
            if (definitionData != null) {
              Map<LongName, DefinitionData> prevData = groupData.getPreviousDefinitions();
              if (prevData != null) {
                for (Map.Entry<LongName, DefinitionData> entry : prevData.entrySet()) {
                  DefinitionData newData = definitionData.get(entry.getKey());
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
                    withTCDefLock(() -> {
                      Set<? extends TCDefReferable> updatedSet = myServer.getDependencyCollector().update(entry.getValue().definition().getData());
                      myLogger.info(() -> "Updated definitions " + updatedSet);
                      ComputationRunner.getCancellationIndicator().cancel(updatedSet);
                      for (TCDefReferable updated : updatedSet) {
                        myServer.getErrorService().resetDefinition(updated);
                      }
                    });
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
    resolveModules(myModules, indicator, progressReporter, getDependencies(indicator), false);
  }

  private ConcreteProvider getConcreteProvider(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter) {
    if (myConcreteProvider == null) {
      myConcreteProvider = resolveModules(myModules, indicator, progressReporter, getDependencies(indicator), true);
    }
    return myConcreteProvider;
  }

  @Override
  public void resolveAll(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter) {
    getConcreteProvider(indicator, progressReporter);
  }

  @Override
  public int typecheck(@Nullable List<FullName> definitions, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter) {
    return typecheck(definitions, null, null, errorReporter, indicator, progressReporter, true);
  }

  @Override
  public int typecheck(@Nullable FullName definition, @NotNull ArendCheckerFactory checkerFactory, @Nullable Map<TCDefReferable, TCDefReferable> renamed, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter) {
    return typecheck(Collections.singletonList(definition), checkerFactory, renamed, errorReporter, indicator, progressReporter, true);
  }

  private static Concrete.ResolvableDefinition copyDefinition(Concrete.ResolvableDefinition definition, Map<TCDefReferable, TCDefReferable> renamed) {
    return definition.accept(new ReplaceTCRefVisitor(renamed), null);
  }

  private int typecheck(@Nullable List<FullName> definitions, @Nullable ArendCheckerFactory checkerFactory, @Nullable Map<TCDefReferable, TCDefReferable> renamed, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter, boolean withInstances) {
    myLogger.info(() -> definitions == null ? "Begin typechecking definitions in " + myModules : "Begin typechecking definitions " + definitions);

    if (checkerFactory != null && definitions == null) {
      throw new IllegalArgumentException();
    }

    ConcreteProvider concreteProvider = getConcreteProvider(indicator, ProgressReporter.empty());
    if (concreteProvider == null) return 0;

    List<Concrete.ResolvableDefinition> concreteDefinitions = definitions == null ? null : new ArrayList<>();
    Set<TCDefReferable> concreteReferences = definitions == null || checkerFactory == null ? null : new HashSet<>();
    if (definitions != null) {
      for (FullName definition : definitions) {
        if (definition.module == null) {
          errorReporter.report(new DefinitionNotFoundError(definition));
          continue;
        }

        GroupData groupData = myDependencies.get(definition.module);
        if (groupData != null) {
          Referable ref = Scope.resolveName(groupData.getFileScope(), definition.longName.toList());
          Concrete.GeneralDefinition def = ref instanceof GlobalReferable ? myConcreteProvider.getConcrete((GlobalReferable) ref) : null;
          if (def instanceof Concrete.ResolvableDefinition cDef) {
            if (checkerFactory != null) {
              cDef = copyDefinition(cDef, renamed);
              concreteReferences.add(cDef.getData());
            }
            concreteDefinitions.add(cDef);
          } else {
            errorReporter.report(new DefinitionNotFoundError(definition));
          }
        } else {
          errorReporter.report(new ModuleNotFoundError(definition.module.getModulePath()));
        }
      }
    }

    if (!Prelude.isInitialized()) {
      new Prelude.PreludeTypechecking(concreteProvider).typecheckDefinitions(myDependencies.get(Prelude.MODULE_LOCATION).getResolvedDefinitions().stream().map(DefinitionData::definition).toList(), UnstoppableCancellationIndicator.INSTANCE);
      Prelude.initialize();
    }

    DependencyCollector dependencyCollector = new DependencyCollector(myServer);
    CollectingOrderingListener collector = new CollectingOrderingListener();
    Ordering ordering = new Ordering(myServer.getInstanceScopeProvider(), concreteProvider, collector, dependencyCollector, new GroupComparator(myDependencies), withInstances, errorReporter);

    TypecheckingCancellationIndicator typecheckingIndicator = new TypecheckingCancellationIndicator(indicator);
    new BooleanComputationRunner().run(typecheckingIndicator, () -> {
      myLogger.info(() -> "<Lock> Typechecking of definitions " + (definitions == null ? "in " + myModules : definitions));

      withTCDefLock(() -> {
        if (concreteDefinitions == null) {
          List<ConcreteGroup> groups = new ArrayList<>(myModules.size());
          for (ModuleLocation module : myModules) {
            GroupData groupData = myDependencies.get(module);
            if (groupData != null) groups.add(groupData.getRawGroup());
          }
          ordering.orderModules(groups);
        } else {
          for (Concrete.ResolvableDefinition definition : concreteDefinitions) {
            ordering.order(definition);
          }
        }
        typecheckingIndicator.setElements(collector.getElements());
      });

      if (!collector.isEmpty()) {
        myLogger.info(() -> "Collected definitions (" + collector.getElements().size() + ") for " + (definitions == null ? myModules : definitions));

        ListErrorReporter listErrorReporter = new ListErrorReporter();
        TypecheckingOrderingListener dependencyTypechecker = new TypecheckingOrderingListener(ArendCheckerFactory.DEFAULT, myServer.getInstanceScopeProvider(), concreteProvider, listErrorReporter, dependencyCollector, new GroupComparator(myDependencies), myServer.getExtensionProvider());
        TypecheckingOrderingListener typechecker = checkerFactory == null ? dependencyTypechecker : new TypecheckingOrderingListener(checkerFactory, myServer.getInstanceScopeProvider(), concreteProvider, listErrorReporter, dependencyCollector, new GroupComparator(myDependencies), myServer.getExtensionProvider());

        try {
          progressReporter.beginProcessing(collector.getElements().size());
          for (CollectingOrderingListener.Element element : collector.getElements()) {
            typecheckingIndicator.checkCanceled();
            progressReporter.beginItem(element.getAllDefinitions());

            if (checkerFactory == null) {
              element.feedTo(typechecker);
            } else {
              boolean found = false;
              List<? extends Concrete.ResolvableDefinition> allDefinitions = element.getAllDefinitions();
              for (Concrete.ResolvableDefinition definition : allDefinitions) {
                if (concreteReferences.contains(definition.getData())) {
                  found = true;
                  break;
                }
              }

              if (found) {
                List<Concrete.ResolvableDefinition> newDefinitions = new ArrayList<>(allDefinitions.size());
                for (Concrete.ResolvableDefinition definition : allDefinitions) {
                  newDefinitions.add(concreteReferences.contains(definition.getData()) ? definition : copyDefinition(definition, renamed));
                }
                element.replace(newDefinitions).feedTo(typechecker);
                break;
              } else {
                element.feedTo(dependencyTypechecker);
              }
            }

            progressReporter.endItem(element.getAllDefinitions());
          }

          withTCDefLock(() -> {
            typecheckingIndicator.checkCanceled();
            listErrorReporter.reportTo(myServer.getErrorService());
            dependencyCollector.copyTo(myServer.getDependencyCollector());
          });

          myLogger.info(() -> "<Unlock> Typechecking of definitions (" + collector.getElements().size() + ") " + (definitions == null ? "in " + myModules : definitions) + " is commited");
        } catch (Exception e) {
          dependencyTypechecker.computationInterrupted();
          typechecker.computationInterrupted();
          myLogger.info(() -> "<Unlock> Typechecking of definitions (" + collector.getElements().size() + ") " + (definitions == null ? "in " + myModules : definitions) + " is interrupted");
          throw e;
        }
      }

      return true;
    });

    myLogger.info(() -> "End typechecking definitions (" + collector.getElements().size() + ") " + (definitions == null ? "in " + myModules : definitions));
    return collector.getElements().size();
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
  public void typecheckExtensionDefinition(@NotNull FullName definition) {
    typecheck(Collections.singletonList(definition), null, null, DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty(), false);
  }
}
