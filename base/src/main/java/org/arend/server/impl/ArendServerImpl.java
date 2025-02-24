package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.ArendExtension;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.Precedence;
import org.arend.module.FullName;
import org.arend.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.module.scopeprovider.SimpleModuleScopeProvider;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.typing.*;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.prelude.Prelude;
import org.arend.server.*;
import org.arend.term.NamespaceCommand;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.ArendExtensionProvider;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.arend.util.list.PersistentList;
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
  private final ErrorService myErrorService = new ErrorService();
  private final DependencyCollector myDependencyCollector = new DependencyCollector(null);
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

    @Override
    public @NotNull Precedence getRefPrecedence(GlobalReferable referable, TypingInfo typingInfo) {
      if (referable.getKind() != GlobalReferable.Kind.COCLAUSE_FUNCTION) return referable.getPrecedence();
      TypingInfo info = getTypingInfo(referable);
      return info == null ? referable.getPrecedence() : info.getRefPrecedence(referable, this);
    }
  };

  private final ArendExtensionProvider myExtensionProvider = new ArendExtensionProvider() {
    @Override
    public @Nullable ArendExtension getArendExtension(TCDefReferable ref) {
      ModuleLocation module = ref.getLocation();
      if (module == null) return null;
      ArendLibraryImpl library = myLibraryService.getLibrary(module.getLibraryName());
      return library == null ? null : library.getExtension();
    }
  };

  private final InstanceScopeProvider myInstanceScopeProvider = new InstanceScopeProvider() {
    @Override
    public @NotNull PersistentList<TCDefReferable> getInstancesFor(@NotNull TCDefReferable referable) {
      FullName fullName = referable.getRefFullName();
      if (fullName.module == null) return PersistentList.empty();
      GroupData groupData = myGroups.get(fullName.module);
      if (groupData == null) return PersistentList.empty();
      DefinitionData defData = groupData.getDefinitionData(fullName.longName);
      return defData == null ? PersistentList.empty() : defData.instances();
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
    copyLogger(ArendCheckerImpl.getLogger());

    myLogger.info(() -> "Server started");
  }

  void copyLogger(Logger to) {
    to.setLevel(myLogger.getLevel());
    to.setUseParentHandlers(myLogger.getUseParentHandlers());
    for (Handler handler : myLogger.getHandlers()) {
      to.addHandler(handler);
    }
  }

  boolean doCacheReferences() {
    return myCacheReferences;
  }

  public ArendServerRequester getRequester() {
    return myRequester;
  }

  public ArendExtensionProvider getExtensionProvider() {
    return myExtensionProvider;
  }

  public InstanceScopeProvider getInstanceScopeProvider() {
    return myInstanceScopeProvider;
  }

  public DependencyCollector getDependencyCollector() {
    return myDependencyCollector;
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
    clearReverseDependencies(libraryName);
    myErrorService.clear();
  }

  @Override
  public void updateLibrary(@NotNull ArendLibrary library, @NotNull ErrorReporter errorReporter) {
    myLibraryService.updateLibrary(library, errorReporter);
  }

  @Override
  public void removeLibrary(@NotNull String name) {
    synchronized (this) {
      myLibraryService.removeLibrary(name);
      clearReverseDependencies(name);
      myLogger.info(() -> "Library '" + name + "' is removed");
    }
  }

  @Override
  public void unloadLibraries(boolean onlyInternal) {
    synchronized (this) {
      Set<String> libraries = myLibraryService.unloadLibraries(onlyInternal);
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

  void addReverseDependencies(ModulePath module, ModuleLocation dependency) {
    myReverseDependencies.computeIfAbsent(module, k -> new HashSet<>()).add(dependency);
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
    myRequester.runUnderReadLock(() -> {
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

          if (prevData != null) {
            clearReverseDependencies(module, prevData.getRawGroup());
          }
          resetReverseDependencies(module.getModulePath(), new HashSet<>());

          myLogger.info(() -> prevData == null ? "Module '" + module + "' is added" : "Module '" + module + "' is updated");
          return new GroupData(modificationStamp, group, prevData);
        });
      }
    });
  }

  private static @NotNull Map<GlobalReferable, Concrete.GeneralDefinition> updateDefinitions(ConcreteGroup group) {
    Map<GlobalReferable, Concrete.GeneralDefinition> defMap = new HashMap<>();
    group.traverseGroup(subgroup -> {
      Concrete.ResolvableDefinition definition = subgroup.definition();
      if (definition instanceof DefinableMetaDefinition metaDef) {
        defMap.put(metaDef.getData(), metaDef);
      } else if (definition != null) {
        defMap.put(subgroup.referable(), definition);
      }
    });
    return defMap;
  }

  @Override
  public void removeModule(@NotNull ModuleLocation module) {
    synchronized (this) {
      GroupData groupData = myGroups.remove(module);
      if (groupData != null) {
        clearReverseDependencies(module, groupData.getRawGroup());
        myLogger.info(() -> "Module '" + module + "' is deleted");
      }
    }
  }

  ModuleLocation findDependency(ModulePath modulePath, String fromLibrary, boolean fromTests, boolean withReadOnly) {
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

  Scope getParentGroupScope(ModuleLocation module, ConcreteGroup group) {
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
  public @NotNull ArendChecker getCheckerFor(@NotNull List<? extends @NotNull ModuleLocation> modules) {
    return modules.isEmpty() ? ArendChecker.EMPTY : new ArendCheckerImpl(this, modules);
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
  public @NotNull TypingInfo getTypingInfo() {
    return myTypingInfo;
  }

  @Override
  public @Nullable ConcreteGroup getRawGroup(@NotNull ModuleLocation module) {
    GroupData groupData = myGroups.get(module);
    return groupData == null ? null : groupData.getRawGroup();
  }

  public @Nullable GroupData getGroupData(@NotNull ModuleLocation module) {
    return myGroups.get(module);
  }

  @Override
  public @NotNull Collection<? extends DefinitionData> getResolvedDefinitions(@NotNull ModuleLocation module) {
    GroupData groupData = myGroups.get(module);
    Collection<? extends DefinitionData> result = groupData == null ? null : groupData.getResolvedDefinitions();
    return result == null ? Collections.emptyList() : result;
  }

  @Override
  public @Nullable DefinitionData getResolvedDefinition(@NotNull TCDefReferable referable) {
    FullName fullName = referable.getRefFullName();
    if (fullName.module == null) return null;
    GroupData groupData = myGroups.get(fullName.module);
    return groupData == null ? null : groupData.getDefinitionData(fullName.longName);
  }

  @Override
  public @NotNull Map<ModuleLocation, List<GeneralError>> getErrorMap() {
    return myErrorService.getAllErrors();
  }

  @Override
  public @NotNull List<GeneralError> getTypecheckingErrors(@NotNull ModuleLocation module) {
    return myErrorService.getTypecheckingErrors(module);
  }

  @Override
  public boolean hasErrors() {
    return myErrorService.hasErrors();
  }

  ErrorService getErrorService() {
    return myErrorService;
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
      }).resolveGroup(group, getParentGroupScope(module, group), PersistentList.empty(), null);
    } catch (CompletionException ignored) {}

    myLogger.fine(() -> found[0] ? "Finish completion for '" + reference.getReferenceText() + "' with " + result.size() + " results" : "Cannot find completion variants for '" + reference.getReferenceText() + "'");
    return result;
  }

  @Override
  public @Nullable Scope getReferableScope(@NotNull LocatedReferable referable) {
    List<LocatedReferable> ancestors = new ArrayList<>();
    ModuleLocation module = LocatedReferable.Helper.getAncestors(referable, ancestors);
    GroupData groupData = module == null ? null : myGroups.get(module);
    if (groupData == null) return null;

    ConcreteGroup group = groupData.getRawGroup();
    Scope scope = LexicalScope.insideOf(group, getParentGroupScope(module, groupData.getRawGroup()), false);
    loop:
    for (LocatedReferable ancestor : ancestors) {
      for (ConcreteStatement statement : group.statements()) {
        ConcreteGroup subgroup = statement.group();
        if (subgroup != null && subgroup.referable().equals(ancestor)) {
          scope = LexicalScope.insideOf(subgroup, scope, false);
          continue loop;
        }
      }
      for (ConcreteGroup subgroup : group.dynamicGroups()) {
        if (subgroup.referable().equals(ancestor)) {
          scope = LexicalScope.insideOf(subgroup, scope, false);
          continue loop;
        }
      }
      return null;
    }

    return scope;
  }
}
