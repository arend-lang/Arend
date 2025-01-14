package org.arend.server;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.error.MergingErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.module.scopeprovider.SimpleModuleScopeProvider;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.CollectingResolverListener;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.prelude.Prelude;
import org.arend.term.NamespaceCommand;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.group.Group;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.arend.util.ComputationInterruptedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.*;

public class ArendServerImpl implements ArendServer {
  private record GroupData(long timestamp, ConcreteGroup group) {}

  private final Logger myLogger = Logger.getLogger(ArendServerImpl.class.getName());
  private final ArendServerRequester myRequester;
  private final SimpleModuleScopeProvider myPreludeModuleScopeProvider = new SimpleModuleScopeProvider();
  private final Map<ModuleLocation, GroupData> myGroups = new ConcurrentHashMap<>();
  private final LibraryService myLibraryService;
  private final ResolverCache myResolverCache;
  private final ErrorService myErrorService = new ErrorService();
  private final boolean myCacheReferences;

  public ArendServerImpl(@NotNull ArendServerRequester requester, boolean cacheReferences, boolean withLogging, @Nullable String logFile) {
    myRequester = requester;
    myCacheReferences = cacheReferences;
    myLogger.setLevel(withLogging ? Level.INFO : Level.OFF);
    myLogger.setUseParentHandlers(false);
    if (logFile != null) {
      try {
        FileHandler handler = new FileHandler(logFile, true);
        handler.setFormatter(new SimpleFormatter());
        myLogger.addHandler(handler);
      } catch (Exception e) {
        myLogger.addHandler(new ConsoleHandler());
        myLogger.severe(e.getMessage());
      }
    } else {
      myLogger.addHandler(new ConsoleHandler());
    }
    myLibraryService = new LibraryService(this);
    myResolverCache = new ResolverCache(this);
  }

  void copyLogger(Logger to) {
    to.setLevel(myLogger.getLevel());
    to.setUseParentHandlers(myLogger.getUseParentHandlers());
    for (Handler handler : myLogger.getHandlers()) {
      to.addHandler(handler);
    }
  }

  ResolverCache getResolverCache() {
    return myResolverCache;
  }

  @Override
  public void updateLibrary(@NotNull ArendLibrary library, @NotNull ErrorReporter errorReporter) {
    myLibraryService.updateLibrary(library, errorReporter);
  }

  @Override
  public void removeLibrary(@NotNull String name) {
    synchronized (this) {
      myLibraryService.removeLibrary(name);
      myResolverCache.clearLibrary(name);
      myGroups.keySet().removeIf(module -> module.getLibraryName().equals(name));
      myLogger.info(() -> "Library '" + name + "' is removed");
    }
  }

  @Override
  public boolean isLibraryLoaded(@NotNull String name) {
    return myLibraryService.isLibraryLoaded(name);
  }

  @Override
  public void addReadOnlyModule(@NotNull ModuleLocation module, @NotNull ConcreteGroup group) {
    if (module.getLibraryName().equals(Prelude.LIBRARY_NAME)) {
      myPreludeModuleScopeProvider.addModule(module.getModulePath(), CachingScope.make(LexicalScope.opened(group)));
    }

    myGroups.compute(module, (mod,prevPair) -> {
      if (prevPair != null) {
        myLogger.warning("Read-only module '" + mod + "' is already added" + (prevPair.timestamp < 0 ? "" : " as a writable module"));
        return prevPair;
      }
      myResolverCache.updateModule(mod, group);
      myLogger.info(() -> "Added a read-only module '" + mod + "'");
      return new GroupData(-1, group);
    });
  }

  @Override
  public void updateModule(long modificationStamp, @NotNull ModuleLocation module, @NotNull Supplier<ConcreteGroup> supplier) {
    synchronized (this) {
      boolean[] updated = new boolean[1];
      GroupData newData = myGroups.compute(module, (k, prevData) -> {
        if (prevData != null) {
          if (prevData.timestamp < 0) {
            myLogger.severe("Read-only module '" + module + "' cannot be updated");
            return prevData;
          } else if (prevData.timestamp >= modificationStamp) {
            myLogger.fine(() -> "Module '" + module + "' is not updated; previous timestamp " + prevData.timestamp + " >= new timestamp " + modificationStamp);
            return prevData;
          }
        }

        ConcreteGroup group = supplier.get();
        if (group == null) {
          myLogger.info(() -> "Module '" + module + "' is not updated");
          return prevData;
        }

        updated[0] = true;
        myResolverCache.updateModule(module, group);

        myLogger.info(() -> prevData == null ? "Module '" + module + "' is added" : "Module '" + module + "' is updated");
        return new GroupData(modificationStamp, group);
      });

      if (updated[0]) {
        new DefinitionResolveNameVisitor(new SimpleConcreteProvider(updateDefinitions(newData.group)), true, DummyErrorReporter.INSTANCE, ResolverListener.EMPTY).resolveGroup(newData.group, getGroupScope(module, newData.group));
      }
    }
  }

  private static @NotNull Map<GlobalReferable, Concrete.GeneralDefinition> updateDefinitions(Group group) {
    Map<GlobalReferable, Concrete.GeneralDefinition> defMap = new HashMap<>();
    group.traverseGroup(subgroup -> {
      if (subgroup instanceof ConcreteGroup cGroup) {
        Concrete.ResolvableDefinition definition = cGroup.definition();
        if (definition instanceof Concrete.ReferableDefinition && cGroup.referable() instanceof ConcreteLocatedReferable referable) {
          definition = definition.accept(new ReplaceDataVisitor(true), null);
          defMap.put(referable, definition);
          referable.setDefinition((Concrete.ReferableDefinition) definition);
        }
      }
    });
    return defMap;
  }

  @Override
  public void removeModule(@NotNull ModuleLocation module) {
    myResolverCache.clearModule(module);
    if (myGroups.remove(module) != null) {
      myLogger.info(() -> "Module '" + module + "' is deleted");
    }
  }

  private ModuleLocation findDependency(ModulePath modulePath, String fromLibrary, boolean fromTests, boolean withReadOnly) {
    List<String> libraries = new ArrayList<>(3);
    libraries.add(fromLibrary);
    ArendLibraryImpl arendLib = myLibraryService.getLibrary(fromLibrary);
    if (arendLib != null) libraries.addAll(arendLib.getLibraryDependencies());
    List<ModuleLocation.LocationKind> kinds = new ArrayList<>(3);
    kinds.add(ModuleLocation.LocationKind.SOURCE);
    if (fromTests) kinds.add(ModuleLocation.LocationKind.TEST);
    kinds.add(ModuleLocation.LocationKind.GENERATED);

    for (String library : libraries) {
      for (ModuleLocation.LocationKind kind : kinds) {
        ModuleLocation location = new ModuleLocation(library, kind, modulePath);
        myRequester.requestModuleUpdate(this, location);
        var modulePair = myGroups.get(location);
        if (modulePair != null) {
          return withReadOnly || modulePair.timestamp >= 0 ? location : null;
        }
      }
    }

    return null;
  }

  private Scope getGroupScope(ModuleLocation module, Group group) {
    return CachingScope.make(ScopeFactory.forGroup(group, new ModuleScopeProvider() {
      @Override
      public @Nullable Scope forModule(@NotNull ModulePath modulePath) {
        Scope result = myPreludeModuleScopeProvider.forModule(modulePath);
        if (result != null) return result;
        ModuleLocation found = findDependency(modulePath, module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
        return found == null ? null : myResolverCache.getModuleScope(found);
      }

      @Override
      public @NotNull GlobalReferable findModule(@NotNull ModulePath modulePath) {
        ModuleLocation location = modulePath.equals(Prelude.MODULE_PATH) ? Prelude.MODULE_LOCATION : findDependency(modulePath, module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
        if (location != null) {
          GroupData groupData = myGroups.get(location);
          if (groupData != null) {
            return groupData.group().referable();
          }
        }
        return new ModuleReferable(modulePath);
      }
    }));
  }

  @Override
  public void resolveModules(@NotNull List<? extends @NotNull ModuleLocation> modules, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {
    if (modules.isEmpty()) return;
    try {
      myLogger.info(() -> "Begin resolving modules " + modules);

      Set<ModuleLocation> visited = new HashSet<>();
      Deque<ModuleLocation> toVisit = new ArrayDeque<>();
      for (ModuleLocation module : modules) {
        indicator.checkCanceled();
        myRequester.requestModuleUpdate(this, module);
        toVisit.add(module);
      }
      while (!toVisit.isEmpty()) {
        indicator.checkCanceled();
        ModuleLocation module = toVisit.pop();
        if (!visited.add(module)) continue;
        GroupData groupData = myGroups.get(module);
        if (groupData != null) {
          for (ConcreteStatement statement : groupData.group.statements()) {
            indicator.checkCanceled();
            if (statement.command() != null && statement.command().getKind() == NamespaceCommand.Kind.IMPORT) {
              ModuleLocation dependency = findDependency(new ModulePath(statement.command().getPath()), module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
              if (dependency != null) toVisit.add(dependency);
            }
          }
        }
      }

      Map<ModuleLocation, Long> moduleVersions = new HashMap<>();
      Map<GlobalReferable, Concrete.GeneralDefinition> defMap = new HashMap<>();
      for (ModuleLocation module : modules) {
        GroupData groupData = myGroups.get(module);
        if (groupData != null) {
          moduleVersions.put(module, groupData.timestamp);
          groupData.group.traverseGroup(group -> {
            if (group instanceof ConcreteGroup cGroup) {
              Concrete.ResolvableDefinition definition = cGroup.definition();
              if (definition != null) {
                defMap.put(cGroup.referable(), definition.accept(new ReplaceDataVisitor(true), null));
              }
            }
          });
        }
      }

      CollectingResolverListener resolverListener = new CollectingResolverListener(listener, myCacheReferences);
      Map<ModuleLocation, ListErrorReporter> errorReporterMap = new HashMap<>();
      for (ModuleLocation module : modules) {
        indicator.checkCanceled();
        GroupData groupData = myGroups.get(module);
        if (groupData != null) {
          resolverListener.moduleLocation = module;
          ListErrorReporter listErrorReporter = new ListErrorReporter();
          errorReporterMap.put(module, listErrorReporter);
          new DefinitionResolveNameVisitor(new SimpleConcreteProvider(defMap), false, new MergingErrorReporter(errorReporter, listErrorReporter), resolverListener).resolveGroupWithTypes(groupData.group, getGroupScope(module, groupData.group));
        }
        listener.moduleResolved(module);
        myLogger.info(() -> "Module '" + module + "' is resolved");
      }

      synchronized (this) {
        for (ModuleLocation module : modules) {
          indicator.checkCanceled();
          GroupData groupData = myGroups.get(module);
          if (groupData != null && (groupData.timestamp < 0 || groupData.timestamp == moduleVersions.get(module))) {
            CollectingResolverListener.ModuleCacheStructure cache = resolverListener.getCacheStructure(module);
            if (cache != null) {
              for (CollectingResolverListener.ResolvedReference resolvedReference : cache.cache()) {
                myResolverCache.addReference(module, resolvedReference.reference(), resolvedReference.referable());
              }
              for (ModulePath modulePath : cache.importedModules()) {
                ModuleLocation dependency = findDependency(modulePath, module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, false);
                if (dependency != null) {
                  myResolverCache.addModuleDependency(module, dependency);
                }
              }
            }
            ListErrorReporter reporter = errorReporterMap.get(module);
            if (reporter != null) {
              myErrorService.setErrors(module, reporter.getErrorList());
            }
          }
        }
      }

      myLogger.info(() -> "End resolving modules " + modules);
    } catch (ComputationInterruptedException e) {
      myLogger.info(() -> "Resolving of modules " + modules + " is interrupted");
    }
  }

  @Override
  public @NotNull Collection<? extends ModuleLocation> getModules() {
    return myGroups.keySet();
  }

  @Override
  public @NotNull Set<String> getLibraries() {
    return myLibraryService.getLibraries();
  }

  @Override
  public @Nullable ConcreteGroup getGroup(@NotNull ModuleLocation module) {
    GroupData groupData = myGroups.get(module);
    return groupData == null ? null : groupData.group;
  }

  @Override
  public @Nullable AbstractReferable resolveReference(@NotNull AbstractReference reference) {
    return myResolverCache.resolveReference(reference);
  }

  @Override
  public @NotNull List<GeneralError> getErrorList(@NotNull ModuleLocation module) {
    return myErrorService.getErrorList(module);
  }

  @Override
  public @NotNull Collection<ModuleLocation> getModulesWithErrors() {
    return myErrorService.getModulesWithErrors();
  }
}
