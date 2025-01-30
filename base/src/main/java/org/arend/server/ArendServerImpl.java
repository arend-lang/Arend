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
import org.arend.naming.resolving.typing.*;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.prelude.Prelude;
import org.arend.term.NamespaceCommand;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.group.Group;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.arend.util.ComputationInterruptedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.*;

public class ArendServerImpl implements ArendServer {
  private final Logger myLogger = Logger.getLogger(ArendServerImpl.class.getName());
  private final ArendServerRequester myRequester;
  private final SimpleModuleScopeProvider myPreludeModuleScopeProvider = new SimpleModuleScopeProvider();
  private final Map<ModuleLocation, GroupData> myGroups = new ConcurrentHashMap<>();
  private final Map<ModulePath, Set<ModuleLocation>> myReverseDependencies = new ConcurrentHashMap<>();
  private final LibraryService myLibraryService;
  private final ResolverCache myResolverCache;
  private final ErrorService myErrorService = new ErrorService();
  private final boolean myCacheReferences;

  private final TypingInfo myTypingInfo = new TypingInfo() {
    @Override
    public @Nullable DynamicScopeProvider getDynamicScopeProvider(Referable referable) {
      TypingInfo info = getTypingInfo(referable);
      return info == null ? null : info.getDynamicScopeProvider(referable);
    }

    @Override
    public @Nullable AbstractBody getRefBody(Referable referable) {
      TypingInfo info = getTypingInfo(referable);
      return info == null ? null : info.getRefBody(referable);
    }

    @Override
    public @Nullable AbstractBody getRefType(Referable referable) {
      TypingInfo info = getTypingInfo(referable);
      return info == null ? null : info.getRefType(referable);
    }
  };

  private final ConcreteProvider myConcreteProvider = new ConcreteProvider() {
    @Override
    public @Nullable Concrete.GeneralDefinition getConcrete(GlobalReferable referable) {
      if (!(referable instanceof LocatedReferable located)) return null;
      ModuleLocation module = located.getLocation();
      if (module == null) return null;
      GroupData groupData = myGroups.get(module);
      return groupData == null ? null : groupData.getConcreteDefinition(referable);
    }
  };

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

  public ArendServerRequester getRequester() {
    return myRequester;
  }

  void clearReverseDependencies(String libraryName) {
    for (Iterator<Map.Entry<ModuleLocation, GroupData>> iterator = myGroups.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<ModuleLocation, GroupData> entry = iterator.next();
      if (entry.getKey().getLibraryName().equals(libraryName)) {
        clearReverseDependencies(entry.getKey(), entry.getValue().getRawGroup());
        iterator.remove();
      }
    }
  }

  void clear(String libraryName) {
    synchronized (this) {
      clearReverseDependencies(libraryName);
      myResolverCache.clearLibraries(Collections.singleton(libraryName));
      myErrorService.clear();
    }
  }

  @Override
  public void updateLibrary(@NotNull ArendLibrary library, @NotNull ErrorReporter errorReporter) {
    myLibraryService.updateLibrary(library, errorReporter);
  }

  @Override
  public void removeLibrary(@NotNull String name) {
    synchronized (this) {
      myLibraryService.removeLibrary(name);
      myResolverCache.clearLibraries(Collections.singleton(name));
      clearReverseDependencies(name);
      myLogger.info(() -> "Library '" + name + "' is removed");
    }
  }

  @Override
  public void unloadLibraries(boolean onlyInternal) {
    synchronized (this) {
      Set<String> libraries = myLibraryService.unloadLibraries(onlyInternal);
      myResolverCache.clearLibraries(libraries);
      myGroups.keySet().removeIf(module -> libraries.contains(module.getLibraryName()));
      myReverseDependencies.clear();
      myLogger.info(onlyInternal ? "Internal libraries unloaded" : "Libraries unloaded");
    }
  }

  @Override
  public ArendLibrary getLibrary(@NotNull String name) {
    return myLibraryService.getLibrary(name);
  }

  @Override
  public void addReadOnlyModule(@NotNull ModuleLocation module, @NotNull ConcreteGroup group) {
    boolean isPrelude = module.getLibraryName().equals(Prelude.LIBRARY_NAME);
    if (isPrelude) {
      if (myPreludeModuleScopeProvider.isRegistered(module.getModulePath())) {
        myLogger.warning("Read-only module '" + module + "' is already added");
      } else {
        myPreludeModuleScopeProvider.addModule(module.getModulePath(), CachingScope.make(LexicalScope.opened(group)));
      }
    }

    myGroups.compute(module, (mod,prevPair) -> {
      if (prevPair != null) {
        myLogger.warning("Read-only module '" + mod + "' is already added" + (prevPair.isReadOnly() ? "" : " as a writable module"));
        return prevPair;
      }
      myResolverCache.clearModule(mod);

      GlobalTypingInfo typingInfo;
      if (isPrelude) {
        typingInfo = new GlobalTypingInfo(null);
        new TypingInfoVisitor(typingInfo).processGroup(group, getParentGroupScope(module, group));
      } else {
        typingInfo = null;
      }

      myLogger.info(() -> "Added a read-only module '" + mod + "'");
      return new GroupData(group, typingInfo);
    });
  }

  private void clearReverseDependencies(ModuleLocation module, ConcreteGroup group) {
    for (ConcreteStatement statement : group.statements()) {
      if (statement.command() != null && statement.command().getKind() == NamespaceCommand.Kind.IMPORT) {
        Set<ModuleLocation> modules = myReverseDependencies.get(new ModulePath(statement.command().getPath()));
        if (modules != null) {
          modules.remove(module);
        }
      }
    }
  }

  private boolean resetReverseDependencies(ModulePath module, Set<ModulePath> visited) {
    if (!visited.add(module)) return false;
    Set<ModuleLocation> reverseDependencies = myReverseDependencies.get(module);
    if (reverseDependencies != null) {
      for (ModuleLocation dependency : reverseDependencies) {
        if (resetReverseDependencies(dependency.getModulePath(), visited)) {
          GroupData groupData = myGroups.get(dependency);
          if (groupData != null) {
            groupData.clearResolved();
          }
        }
      }
    }
    return true;
  }

  @Override
  public void updateModule(long modificationStamp, @NotNull ModuleLocation moduleLocation, @NotNull Supplier<ConcreteGroup> supplier) {
    synchronized (this) {
      myGroups.compute(moduleLocation, (module, prevData) -> {
        if (prevData != null) {
          if (prevData.isReadOnly()) {
            myLogger.severe("Read-only module '" + module + "' cannot be updated");
            return prevData;
          } else if (prevData.getTimestamp() >= modificationStamp) {
            myLogger.fine(() -> "Module '" + module + "' is not updated; previous timestamp " + prevData.getTimestamp() + " >= new timestamp " + modificationStamp);
            return prevData;
          }
        }

        ConcreteGroup group = supplier.get();
        if (group == null) {
          myLogger.info(() -> "Module '" + module + "' is not updated");
          return prevData;
        }

        myResolverCache.clearModule(module);

        if (prevData != null) {
          clearReverseDependencies(module, prevData.getRawGroup());
        }
        resetReverseDependencies(module.getModulePath(), new HashSet<>());

        myLogger.info(() -> prevData == null ? "Module '" + module + "' is added" : "Module '" + module + "' is updated");
        return new GroupData(modificationStamp, group);
      });
    }
  }

  private static @NotNull Map<GlobalReferable, Concrete.GeneralDefinition> updateDefinitions(Group group) {
    Map<GlobalReferable, Concrete.GeneralDefinition> defMap = new HashMap<>();
    group.traverseGroup(subgroup -> {
      if (subgroup instanceof ConcreteGroup cGroup) {
        Concrete.ResolvableDefinition definition = cGroup.definition();
        if (definition instanceof DefinableMetaDefinition metaDef) {
          defMap.put(metaDef.getData(), metaDef);
        } else if (definition != null) {
          defMap.put(cGroup.referable(), definition);
        }
      }
    });
    return defMap;
  }

  @Override
  public void removeModule(@NotNull ModuleLocation module) {
    synchronized (this) {
      myResolverCache.clearModule(module);
      GroupData groupData = myGroups.remove(module);
      if (groupData != null) {
        clearReverseDependencies(module, groupData.getRawGroup());
        myLogger.info(() -> "Module '" + module + "' is deleted");
      }
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
          return withReadOnly || !modulePair.isReadOnly() ? location : null;
        }
      }
    }

    return null;
  }

  private ModuleScopeProvider getModuleScopeProviderFor(ModuleLocation module) {
    return new ModuleScopeProvider() {
      @Override
      public @Nullable Scope forModule(@NotNull ModulePath modulePath) {
        Scope result = myPreludeModuleScopeProvider.forModule(modulePath);
        if (result != null) return result;
        ModuleLocation found = findDependency(modulePath, module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
        if (found == null) return null;
        GroupData groupData = myGroups.get(found);
        return groupData == null ? null : groupData.getFileScope();
      }

      @Override
      public @NotNull GlobalReferable findModule(@NotNull ModulePath modulePath) {
        ModuleLocation location = modulePath.equals(Prelude.MODULE_PATH) ? Prelude.MODULE_LOCATION : findDependency(modulePath, module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
        if (location != null) {
          GroupData groupData = myGroups.get(location);
          if (groupData != null) {
            return groupData.getFileReferable();
          }
        }
        return new ModuleReferable(modulePath);
      }

      @Override
      public @NotNull Scope getModuleScope() {
        return new LazyScope(() -> new ModuleScope(ArendServerImpl.this, module.getLibraryName(), module.getLocationKind()));
      }
    };
  }

  private Scope getParentGroupScope(ModuleLocation module, Group group) {
    return ScopeFactory.parentScopeForGroup(group, getModuleScopeProviderFor(module), true);
  }

  private TypingInfo getTypingInfo(Referable referable) {
    if (!(referable instanceof LocatedReferable located)) return null;
    ModuleLocation module = located.getLocation();
    if (module == null) return null;
    GroupData groupData = myGroups.get(module);
    return groupData == null ? null : groupData.getTypingInfo();
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
          for (ConcreteStatement statement : groupData.getRawGroup().statements()) {
            indicator.checkCanceled();
            if (statement.command() != null && statement.command().getKind() == NamespaceCommand.Kind.IMPORT) {
              ModuleLocation dependency = findDependency(new ModulePath(statement.command().getPath()), module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
              if (dependency != null) toVisit.add(dependency);
            }
          }
        }
      }

      synchronized (this) {
        for (ModuleLocation module : visited) {
          GroupData groupData = myGroups.get(module);
          if (groupData != null) {
            for (ConcreteStatement statement : groupData.getRawGroup().statements()) {
              if (statement.command() != null && statement.command().getKind() == NamespaceCommand.Kind.IMPORT) {
                myReverseDependencies.computeIfAbsent(new ModulePath(statement.command().getPath()), k -> new HashSet<>()).add(module);
              }
            }
          }
        }
      }

      Map<ModuleLocation, Long> moduleVersions = new HashMap<>();
      for (ModuleLocation module : visited) {
        GroupData groupData = myGroups.get(module);
        if (groupData != null) {
          moduleVersions.put(module, groupData.getTimestamp());
          if (groupData.getTypingInfo() == null) {
            GlobalTypingInfo typingInfo = new GlobalTypingInfo(null);
            new TypingInfoVisitor(typingInfo).processGroup(groupData.getRawGroup(), getParentGroupScope(module, groupData.getRawGroup()));
            groupData.setTypingInfo(typingInfo);
            myLogger.info(() -> "Header of module '" + module + "' is resolved");
          }
        }
      }

      Map<ModuleLocation, ConcreteProvider> concreteProviders = new HashMap<>();
      for (ModuleLocation module : modules) {
        GroupData groupData = myGroups.get(module);
        if (groupData != null) {
          Map<GlobalReferable, Concrete.GeneralDefinition> defMap = new HashMap<>();
          groupData.getRawGroup().traverseGroup(group -> {
            if (group instanceof ConcreteGroup cGroup) {
              Concrete.ResolvableDefinition definition = cGroup.definition();
              if (definition != null) {
                defMap.put(cGroup.referable(), definition.accept(new ReplaceDataVisitor(true), null));
              }
            }
          });
          concreteProviders.put(module, new SimpleConcreteProvider(defMap));
        }
      }

      CollectingResolverListener resolverListener = new CollectingResolverListener(listener, myCacheReferences);
      Map<ModuleLocation, ListErrorReporter> errorReporterMap = new HashMap<>();
      Map<ModuleLocation, List<GroupData.DefinitionData>> resolverResult = new HashMap<>();
      for (ModuleLocation module : modules) {
        indicator.checkCanceled();
        GroupData groupData = myGroups.get(module);
        if (groupData != null) {
          resolverListener.moduleLocation = module;
          ErrorReporter currentErrorReporter;
          if (groupData.isReadOnly()) {
            currentErrorReporter = DummyErrorReporter.INSTANCE;
          } else {
            ListErrorReporter listErrorReporter = new ListErrorReporter();
            errorReporterMap.put(module, listErrorReporter);
            currentErrorReporter = new MergingErrorReporter(errorReporter, listErrorReporter);
          }
          new DefinitionResolveNameVisitor(concreteProviders.get(module), myTypingInfo, currentErrorReporter, resolverListener).resolveGroup(groupData.getRawGroup(), getParentGroupScope(module, groupData.getRawGroup()));

          List<GroupData.DefinitionData> definitionData = new ArrayList<>();
          groupData.getRawGroup().traverseGroup(group -> {
            Concrete.GeneralDefinition def = concreteProviders.get(module).getConcrete(group.getReferable());
            if (def instanceof Concrete.Definition definition) {
              definitionData.add(new GroupData.DefinitionData(definition, null));
            }
          });
          resolverResult.put(module, definitionData);
        }
        listener.moduleResolved(module);
        myLogger.info(() -> "Module '" + module + "' is resolved");
      }

      synchronized (this) {
        for (ModuleLocation module : visited) {
          GroupData groupData = myGroups.get(module);
          if (groupData == null || !groupData.isReadOnly() && groupData.getTimestamp() != moduleVersions.get(module)) {
            myLogger.info(() -> "Version of " + module + " changed; didn't resolve modules " + modules);
            return;
          }
        }

        for (ModuleLocation module : modules) {
          indicator.checkCanceled();
          GroupData groupData = myGroups.get(module);
          if (groupData != null) {
            CollectingResolverListener.ModuleCacheStructure cache = resolverListener.getCacheStructure(module);
            if (cache != null) {
              for (CollectingResolverListener.ResolvedReference resolvedReference : cache.cache()) {
                myResolverCache.addReference(module, resolvedReference.reference(), resolvedReference.referable());
              }
              for (CollectingResolverListener.ReferablePair pair : cache.referables()) {
                myResolverCache.addReferable(module, pair.referable(), pair.tcReferable());
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

            groupData.setResolvedDefinitions(concreteProviders.get(module), resolverResult.get(module));
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
  public @Nullable ConcreteGroup getRawGroup(@NotNull ModuleLocation module) {
    GroupData groupData = myGroups.get(module);
    return groupData == null ? null : groupData.getRawGroup();
  }

  GroupData getGroupData(@NotNull ModuleLocation module) {
    return myGroups.get(module);
  }

  @Override
  public @Nullable AbstractReferable resolveReference(@NotNull AbstractReference reference) {
    return myResolverCache.resolveReference(reference);
  }

  @Override
  public @Nullable AbstractReferable getCachedReferable(@NotNull AbstractReference reference) {
    return myResolverCache.getCachedReferable(reference);
  }

  @Override
  public void cacheReference(@NotNull UnresolvedReference reference, @NotNull Referable referable) {
    myResolverCache.addReference(reference, referable);
  }

  @Override
  public @NotNull List<GeneralError> getErrorList(@NotNull ModuleLocation module) {
    return myErrorService.getErrorList(module);
  }

  @Override
  public @NotNull Collection<ModuleLocation> getModulesWithErrors() {
    return myErrorService.getModulesWithErrors();
  }

  private static class CompletionException extends RuntimeException {}

  @Override
  public @NotNull List<Referable> getCompletionVariants(@Nullable ConcreteGroup group, @NotNull AbstractReference reference) {
    myLogger.fine(() -> "Begin completion for '" + reference.getReferenceText() + "'");

    ModuleLocation module = reference.getReferenceModule();
    if (group == null && module != null) {
      GroupData groupData = myGroups.get(module);
      if (groupData != null) group = groupData.getRawGroup();
    }
    if (module == null || group == null) {
      myLogger.fine(() -> "Completion for '" + reference.getReferenceText() + "' failed: cannot find module");
      return Collections.emptyList();
    }

    List<Referable> result = new ArrayList<>();
    boolean[] found = new boolean[1];
    try {
      GlobalTypingInfo typingInfo = new GlobalTypingInfo(myTypingInfo);
      new TypingInfoVisitor(typingInfo).processGroup(group, getParentGroupScope(module, group));
      new DefinitionResolveNameVisitor(new SimpleConcreteProvider(updateDefinitions(group)), typingInfo, DummyErrorReporter.INSTANCE, new ResolverListener() {
        @Override
        public void resolving(AbstractReference abstractReference, Scope scope, Scope.ScopeContext context, boolean finished) {
          if (abstractReference == reference) {
            result.addAll(scope.getElements(context));
            found[0] = true;
            if (finished) {
              throw new CompletionException();
            }
          }
        }
      }).resolveGroup(group, getParentGroupScope(module, group));
    } catch (CompletionException ignored) {}

    myLogger.fine(() -> found[0] ? "Finish completion for '" + reference.getReferenceText() + "' with " + result.size() + " results" : "Cannot find completion variants for '" + reference.getReferenceText() + "'");
    return result;
  }

  @Override
  public @Nullable TCDefReferable getTCReferable(@NotNull AbstractReferable referable) {
    return myResolverCache.getTCReferable(referable);
  }
}
