package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.ArendExtension;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.Precedence;
import org.arend.ext.util.Pair;
import org.arend.module.FullName;
import org.arend.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.module.scopeprovider.SimpleModuleScopeProvider;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.typing.*;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.naming.scope.local.ListScope;
import org.arend.prelude.Prelude;
import org.arend.server.*;
import org.arend.server.modifier.RawImportAdder;
import org.arend.server.modifier.RawImportRemover;
import org.arend.server.modifier.RawModifier;
import org.arend.server.modifier.RawSequenceModifier;
import org.arend.source.error.LocationError;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.term.concrete.LocalVariablesCollector;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
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
import java.util.concurrent.atomic.AtomicReference;
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

    myGroups.compute(module, (mod, prevPair) -> {
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

      updateReferables(group, module);

      myLogger.info(() -> "Added a read-only module '" + mod + "'");
      return new GroupData(group, typingInfo);
    });
  }

  void addReverseDependencies(ModulePath module, ModuleLocation dependency) {
    myReverseDependencies.computeIfAbsent(module, k -> new HashSet<>()).add(dependency);
  }

  private void clearReverseDependencies(ModuleLocation module, ConcreteGroup group) {
    for (ConcreteStatement statement : group.statements()) {
      if (statement.command() != null && statement.command().isImport()) {
        Set<ModuleLocation> modules = myReverseDependencies.get(new ModulePath(statement.command().module().getPath()));
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

          GroupData newData = new GroupData(modificationStamp, group, prevData);
          updateReferables(newData.getRawGroup(), module);

          myLogger.info(() -> prevData == null ? "Module '" + module + "' is added" : "Module '" + module + "' is updated");
          return newData;
        });
      }
    });
  }

  private void addGeneratedName(ArendLibraryImpl library, LocatedReferable referable) {
    if (library == null) return;
    library.putGeneratedName(referable.getRefName(), referable);
    if (referable.getAliasName() != null) {
      library.putGeneratedName(referable.getAliasName(), referable);
    }
  }

  private void updateReferables(ConcreteGroup group, ModuleLocation module) {
    ArendLibraryImpl library = myLibraryService.getLibrary(module.getLibraryName());
    group.traverseGroup(subgroup -> {
      addGeneratedName(library, subgroup.referable());
      if (subgroup.referable() instanceof TCDefReferable tcRef && tcRef.getData() instanceof AbstractReferable referable) {
        myRequester.addReference(module, referable, tcRef);
      }
      if (subgroup.definition() instanceof Concrete.DataDefinition dataDef) {
        for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
          for (Concrete.Constructor constructor : clause.getConstructors()) {
            if (constructor.getData().getData() instanceof AbstractReferable referable) {
              addGeneratedName(library, constructor.getData());
              myRequester.addReference(module, referable, constructor.getData());
            }
          }
        }
      } else if (subgroup.definition() instanceof Concrete.ClassDefinition classDef) {
        for (Concrete.ClassElement element : classDef.getElements()) {
          if (element instanceof Concrete.ClassField field && field.getData().getData() instanceof AbstractReferable referable) {
            addGeneratedName(library, field.getData());
            myRequester.addReference(module, referable, field.getData());
          }
        }
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
    List<String> libraries = new ArrayList<>();
    if (fromLibrary == null) {
      libraries.addAll(getLibraries());
    } else {
      libraries.add(fromLibrary);
      ArendLibraryImpl arendLib = myLibraryService.getLibrary(fromLibrary);
      if (arendLib != null) libraries.addAll(arendLib.getLibraryDependencies());
    }

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

  @Override
  public @NotNull ModuleScopeProvider getModuleScopeProvider(@Nullable String libraryName, boolean withTests) {
    return new ModuleScopeProvider() {
      @Override
      public @Nullable Scope forModule(@NotNull ModulePath modulePath) {
        Scope result = myPreludeModuleScopeProvider.forModule(modulePath);
        if (result != null) return result;
        ModuleLocation found = findDependency(modulePath, libraryName, withTests, true);
        if (found == null) return null;
        GroupData groupData = myGroups.get(found);
        return groupData == null ? null : groupData.getFileScope();
      }

      @Override
      public @NotNull GlobalReferable findModule(@NotNull ModulePath modulePath) {
        ModuleLocation location = modulePath.equals(Prelude.MODULE_PATH) ? Prelude.MODULE_LOCATION : findDependency(modulePath, libraryName, withTests, true);
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
        return new LazyScope(() -> new ModuleScope(ArendServerImpl.this, libraryName, withTests));
      }
    };
  }

  Scope getParentGroupScope(ModuleLocation module, ConcreteGroup group) {
    return ScopeFactory.parentScopeForGroup(group, getModuleScopeProvider(module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST), true);
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

  private static class CompletionException extends RuntimeException {
  }

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
    } catch (CompletionException ignored) {
    }

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
          group = subgroup;
          continue loop;
        }
      }
      for (ConcreteGroup subgroup : group.dynamicGroups()) {
        if (subgroup.referable().equals(ancestor)) {
          scope = LexicalScope.insideOf(subgroup, scope, false);
          group = subgroup;
          continue loop;
        }
      }
      return null;
    }

    return scope;
  }

  @Override
  public @NotNull Pair<RawModifier, List<LongName>> makeReferencesAvailable(
    @NotNull List<LocatedReferable> referables,
    @NotNull ConcreteGroup currentFile,
    @NotNull RawAnchor anchor,
    @NotNull ErrorReporter errorReporter) {
    // Check that referables are located in available modules and collect them in refMap
    ModuleLocation anchorModule = anchor.parent().getLocation();
    List<RawModifier> nsCmdActions = new ArrayList<>();
    List<LongName> result = new ArrayList<>();

    if (anchorModule == null) {
      errorReporter.report(LocationError.definition(anchor.parent(), null));
      return new Pair<>(new RawSequenceModifier(nsCmdActions), result);
    }

    Map<ModulePath, List<LocatedReferable>> refMap = new HashMap<>();
    for (LocatedReferable referable : referables) {
      ModuleLocation module = referable.getLocation();
      if (module == null) {
        errorReporter.report(LocationError.definition(referable, null));
        continue;
      }

      ModuleLocation found = findDependency(module.getModulePath(), anchorModule.getLibraryName(), anchorModule.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
      if (!module.equals(found)) {
        errorReporter.report(LocationError.definition(null, module.getModulePath()));
      }

      refMap.computeIfAbsent(module.getModulePath(), m -> new ArrayList<>()).add(referable);
    }

    // Calculate the set of local referables
    List<Referable> localReferables = new ArrayList<>();
    if (anchor.data() != null && anchor.parent() instanceof TCDefReferable tcRef) {
      DefinitionData definitionData = getResolvedDefinition(tcRef);
      if (definitionData != null) {
        LocalVariablesCollector collector = new LocalVariablesCollector(anchor.data());
        definitionData.definition().accept(collector, null);
        @Nullable List<Referable> collectedResult = collector.getResult();
        if (collectedResult != null) localReferables.addAll(collectedResult);
      }
    }

    Scope currentScope = new MergeScope(new ListScope(localReferables), getReferableScope(anchor.parent()));
    Collection<? extends Referable> currentScopeElements = currentScope.getElements();
    HashMap<Referable, String> currentScopeMap = new HashMap<>();
    for (Referable referable : currentScopeElements) {
      if (referable instanceof RedirectingReferable)
        currentScopeMap.put(((RedirectingReferable) referable).getOriginalReferable(), referable.getRefName());
      currentScopeMap.put(referable, referable.getRefName());
    }

    Set<LocatedReferable> anchorAncestors = new HashSet<>();
    LocatedReferable locatedReferable = anchor.parent();
    do {
      anchorAncestors.add(locatedReferable);
      locatedReferable = locatedReferable.getLocatedReferableParent();
    } while (locatedReferable != null);

    HashMap<ConcreteNamespaceCommand, @Nullable HashSet<String>> itemsToAdd = new HashMap<>();
    HashMap<ModuleLocation, @Nullable HashSet<String>> importsToAdd = new HashMap<>();

    for (LocatedReferable referable : referables) {
      ModuleLocation targetModuleLocation = referable.getLocation();
      ConcreteGroup targetModuleFile = targetModuleLocation != null ? this.getRawGroup(targetModuleLocation) : null;
      List<Referable> targetModuleDefinitions = new LinkedList<>();
      if (targetModuleFile != null) for (ConcreteStatement statement : targetModuleFile.statements()) {
        ConcreteGroup group = statement.group();
        if (group != null) targetModuleDefinitions.add(group.referable());
      }

      AtomicReference<ConcreteNamespaceCommand> namespaceCommand = new AtomicReference<>();
      AtomicReference<Boolean> preludeImportedManually = new AtomicReference<>(false); //TODO:

      currentFile.traverseGroup(subgroup -> subgroup.statements().forEach(statement -> {
        ConcreteNamespaceCommand command = statement.command();
        if (command != null && command.isImport()) {
          Boolean isPrelude = Prelude.MODULE_PATH.toList().equals(command.module().getPath());

          ModuleLocation commandTarget = isPrelude? Prelude.MODULE_LOCATION : findDependency(new ModulePath(command.module().getPath()),
            anchorModule.getLibraryName(), anchorModule.getLocationKind() == ModuleLocation.LocationKind.TEST, true);

          if (commandTarget.equals(referable.getLocation())) {
            namespaceCommand.set(command);
          }

          if (isPrelude) {
            preludeImportedManually.set(true);
          }
        }
      }));

      boolean nonEmptyScopeIntersection = (!Prelude.MODULE_LOCATION.equals(targetModuleLocation) &&
        targetModuleDefinitions.stream().anyMatch(stat -> currentScope.resolveName(stat.getRefName()) != null));
      List<String> calculatedName = new ArrayList<>();

      if (namespaceCommand.get() != null || anchorModule.equals(targetModuleLocation)) {
        LocatedReferable currReferable;
        LocatedReferable parent = referable;
        Boolean foundNameInScope = false;
        String contextName;

        do {
          currReferable = parent;
          parent = currReferable.getLocatedReferableParent();

          contextName = currentScopeMap.get(currReferable);
          if (contextName != null) {
            Referable resolveResult = currentScope.resolveName(contextName);
            if (resolveResult instanceof RedirectingReferable redirecting) resolveResult = redirecting.getOriginalReferable();

            if (resolveResult == currReferable) {
              calculatedName.add(0, contextName);
              foundNameInScope = true;
              break;
            } else if (currReferable.getAliasName() != null && currentScope.resolveName(currReferable.getAliasName()) == currReferable) {
              calculatedName.add(0, currReferable.getAliasName());
              foundNameInScope = true;
              break;
            }
          }

          if (currReferable instanceof InternalReferable &&
              !(currReferable instanceof FieldReferable fieldReferable && fieldReferable.isParameterField()) &&
            parent != null && !(parent instanceof ModuleReferable)) {
            parent = parent.getLocatedReferableParent();
          }

          calculatedName.add(0, currReferable.getRefName());
        } while (!(parent instanceof ModuleReferable));

        final LocatedReferable topLevelReferable = currReferable;
        boolean topLevelReferableIsProtected = topLevelReferable.getAccessModifier() == AccessModifier.PROTECTED;

        if (contextName == null) contextName = calculatedName.getFirst();

        boolean scopeObstructed = !foundNameInScope && currentScope.resolveName(contextName) != null;

        if (scopeObstructed) {
          calculatedName.addAll(0, targetModuleLocation.getModulePath().toList());
        }

        ConcreteNamespaceCommand cmd = namespaceCommand.get();
        if (cmd != null) {
          boolean topLevelNameImported = (cmd.isUsing() && !topLevelReferableIsProtected || cmd.renamings().stream().anyMatch(nameRenaming ->
            nameRenaming.reference().getRefName().equals(topLevelReferable.getRefName()) ||
              nameRenaming.reference().getRefName().equals(topLevelReferable.getAliasName()))) && cmd.hidings().stream().noneMatch(nameHiding ->
            nameHiding.reference().getRefName().equals(topLevelReferable.getRefName())
          );

          if (!foundNameInScope && !topLevelNameImported && !scopeObstructed) {
            itemsToAdd.computeIfAbsent(cmd, c -> new HashSet<>()).add(contextName);
          }
        }
      } else {
        LocatedReferable currReferable;
        LocatedReferable parent = referable;
        do {
          currReferable = parent;

          if (currReferable.getAliasName() != null)
            calculatedName.add(0, currReferable.getAliasName()); else
              calculatedName.add(0, currReferable.getRefName());

          parent = currReferable.getLocatedReferableParent();

          if (currReferable instanceof InternalReferable &&
            !(currReferable instanceof FieldReferable fieldReferable && fieldReferable.isParameterField()) &&
            parent != null && !(parent instanceof ModuleReferable)) {
            parent = parent.getLocatedReferableParent();
          }
        } while (parent != null && !(parent instanceof ModuleReferable));

        Referable referableInScope = currentScope.resolveName(currReferable.getRefName());
        String topLevelName = calculatedName.getFirst();
        boolean topLevelReferableIsProtected = currReferable.getAccessModifier() == AccessModifier.PROTECTED;
        boolean scopeObstructed = referableInScope != null && referableInScope != currReferable;

        if (scopeObstructed) {
          calculatedName.addAll(0, targetModuleLocation.getModulePath().toList());
        }
        if (itemsToAdd.containsKey(namespaceCommand.get())) {
          HashSet<String> individualImports = itemsToAdd.get(targetModuleLocation);
          if (individualImports != null) individualImports.add(topLevelName);
        } else if (importsToAdd.containsKey(targetModuleLocation)) {
          HashSet<String> individualImports = importsToAdd.get(targetModuleLocation);
          if (individualImports != null) individualImports.add(topLevelName);
        } else {
          if (topLevelReferableIsProtected || nonEmptyScopeIntersection) {
            if (scopeObstructed)
              importsToAdd.put(targetModuleLocation, new HashSet<>());
             else
              importsToAdd.put(targetModuleLocation, new HashSet<>(Collections.singletonList(topLevelName)));
          } else if (!Prelude.MODULE_LOCATION.equals(targetModuleLocation) || scopeObstructed)
            importsToAdd.put(targetModuleLocation, null);
        }
      }
      result.add(new LongName(calculatedName));
    }

    for (Map.Entry<ConcreteNamespaceCommand, HashSet<String>> entry : itemsToAdd.entrySet()) {
      nsCmdActions.add(new RawImportRemover(entry.getKey()));
      HashSet<String> names = entry.getValue();
      ArrayList<ConcreteNamespaceCommand.NameHiding> hidings = new ArrayList<>(entry.getKey().hidings().stream().filter(nameHiding -> {
        String hidingName = nameHiding.reference().getRefName();
        if (names.contains(hidingName)) {
          names.remove(hidingName);
          return false;
        }
        return true;
      }).toList());
      ArrayList<ConcreteNamespaceCommand.NameRenaming> renamings = new ArrayList<>(entry.getKey().renamings());
      renamings.addAll(names.stream().map(name ->
        new ConcreteNamespaceCommand.NameRenaming(null, Scope.ScopeContext.STATIC, new NamedUnresolvedReference(null, name), null, null)).toList());

      renamings.sort(Comparator.comparing(nameRenaming -> nameRenaming.reference().getRefName()));

      ConcreteNamespaceCommand newCommand = new ConcreteNamespaceCommand(null, true, entry.getKey().module(), entry.getKey().isUsing(), renamings, hidings);
      nsCmdActions.add(new RawImportAdder(newCommand));
    }

    for (Map.Entry<ModuleLocation, HashSet<String>> entry : importsToAdd.entrySet()) if (!entry.getKey().equals(anchorModule)) {
      ArrayList<ConcreteNamespaceCommand.NameRenaming> renamings = new ArrayList<>();
      HashSet<String> individualImports = entry.getValue();
      if (individualImports != null) renamings.addAll(individualImports.stream().map(name ->
        new ConcreteNamespaceCommand.NameRenaming(null, Scope.ScopeContext.STATIC, new NamedUnresolvedReference(null, name), null, null)).toList());
      else renamings = null;

      if (renamings != null) renamings.sort(Comparator.comparing(nameRenaming -> nameRenaming.reference().getRefName()));

      ConcreteNamespaceCommand newCommand = new ConcreteNamespaceCommand(null, true,
        new LongUnresolvedReference(null, null, entry.getKey().getModulePath().toList()), renamings == null, renamings != null ? renamings : new ArrayList<>(), new ArrayList<>());
      nsCmdActions.add(new RawImportAdder(newCommand));
    }

    return new Pair<>(new RawSequenceModifier(nsCmdActions), result);
  }

}
