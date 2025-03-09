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
import org.arend.server.modifier.RawModifier;
import org.arend.server.modifier.RawSequenceModifier;
import org.arend.source.error.LocationError;
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
        localReferables = collector.getResult();
      }
    }

    ModuleScopeProvider moduleScopeProvider = getModuleScopeProvider(anchorModule.getLibraryName(), anchorModule.getLocationKind() == ModuleLocation.LocationKind.TEST);
    Scope currentScope = new MergeScope(new ListScope(localReferables), LocatedReferable.Helper.resolveNamespace(anchor.parent(), moduleScopeProvider));
    Collection<? extends Referable> currentScopeElements = currentScope.getElements();

    /*Set<String> currentScopeElementNames = new HashSet<>();
    for (Referable referable : currentScopeElements) { currentScopeElementNames.add(referable.textRepresentation()); } */

    Set<LocatedReferable> anchorAncestors = new HashSet<>();
    LocatedReferable locatedReferable = anchor.parent();
    do {
      anchorAncestors.add(locatedReferable);
      locatedReferable = locatedReferable.getLocatedReferableParent();
    } while (locatedReferable != null);

    HashMap<LocatedReferable, List<ConcreteNamespaceCommand>> existingNamespaceCommands =
      getExistingNamespaceCommands(currentFile, anchorAncestors);

            /* TODO[server2]
            if (psi instanceof ArendDefClass) {
                ClassFieldImplScope scope = new ClassFieldImplScope((ArendDefClass) psi, true);
                for (Location location : locations) {
                    location.checkShortNameInScope(scope);
                }
            }
            */


    HashMap<ConcreteNamespaceCommand, List<String>> itemsToAdd = new HashMap<>();
    HashMap<ModuleLocation, List<String>> importsToAdd = new HashMap<>();

    for (LocatedReferable referable : referables) {
      ModuleLocation targetModuleLocation = referable.getLocation();
      ConcreteGroup targetModuleFile = targetModuleLocation != null ? this.getRawGroup(targetModuleLocation) : null;
      List<Referable> targetModuleDefinitions = new LinkedList<>();
      if (targetModuleFile != null) for (ConcreteStatement statement : targetModuleFile.statements()) {
        ConcreteGroup group = statement.group();
        if (group != null) targetModuleDefinitions.add(group.referable());
      }

      List<CalculatedName> names = new ArrayList<>();
      CalculatedName defaultName = new CalculatedName(this, referable, false, false);
      names.add(defaultName);

      if (referable.hasAlias())
        names.add(new CalculatedName(this, referable, false, true));

      if (referable instanceof InternalReferable) {
        names.add(new CalculatedName(this, referable, true, false));
        if (referable.hasAlias()) names.add(new CalculatedName(this, referable, true, true));
      }

      AtomicReference<ConcreteNamespaceCommand> existingImportCommand = new AtomicReference<>(null);
      AtomicReference<Boolean> preludeImportedManually = new AtomicReference<>(false);

      currentFile.traverseGroup(subgroup -> subgroup.statements().forEach(statement -> {
        ConcreteNamespaceCommand command = statement.command();
        if (command != null && command.isImport()) {
          ModuleLocation commandTarget = findDependency(new ModulePath(command.module().getPath()),
            anchorModule.getLibraryName(), anchorModule.getLocationKind() == ModuleLocation.LocationKind.TEST, true);

          if (commandTarget.equals(referable.getLocation())) {
            existingImportCommand.set(command);
            for (CalculatedName location : names)
              location.processStatCmd(command, moduleScopeProvider);
          }

          if (Prelude.MODULE_PATH.equals(commandTarget.getModulePath())) {
            preludeImportedManually.set(true);
          }
        }
      }));

      boolean referableIsProtected = referable.getAccessModifier() == AccessModifier.PROTECTED;
      boolean nonEmptyScopeIntersection = (!Prelude.MODULE_LOCATION.equals(referable.getLocation()) &&
        targetModuleDefinitions.stream().anyMatch(stat -> currentScope.resolveName(stat.getRefName()) != null));

      for (Map.Entry<LocatedReferable, List<ConcreteNamespaceCommand>> entry : existingNamespaceCommands.entrySet()) {
        for (CalculatedName name : names) {
          name.processParentGroup(entry.getKey());
          if (entry.getValue() != null) for (ConcreteNamespaceCommand nsCmd : entry.getValue())
            name.processStatCmd(nsCmd, moduleScopeProvider);
        }
      }

      for (CalculatedName name : names) {
        System.out.println("Name: " + name.getLongName());
      }

      /* if (existingImportCommand.get() != null) {
        for (CalculatedName name : names) {
          if (name.getReferenceNames().isEmpty() || referableIsProtected) {
            name.addLongNameAsReferenceName();

            fileResolveActions.put(name, new AddIdToUsingAction(currentFile, targetFile, name));
          }
        }
        fallbackImportAction = null;
      } else {
        fallbackImportAction = new ImportFileAction(currentFile, targetFile, minimalImportMode ? Collections.emptyList() : null);
        if (isPrelude(targetFile) && !preludeImportedManually.get()) fallbackImportAction = null;

        for (CalculatedName name : names) {
          List<String> fName = name.getLongName();
          List<String> importList = fName.isEmpty() ? Collections.emptyList() : Collections.singletonList(fName.get(0));
          name.addLongNameAsReferenceName();
          fileResolveActions.put(name, (referableIsProtected || nonEmptyScopeIntersection) ?
            new ImportFileAction(currentFile, targetFile, importList) : fallbackImportAction);
        }
      } */

      /* Scope correctedScope = currentScope;

      if (deferredImports != null && !deferredImports.isEmpty()) {
        List<Scope> scopes = Collections.singletonList(correctedScope);
        for (DeferredImport deferredImport : deferredImports) {
          scopes.add(deferredImport.getAmendedScope());
        }
        correctedScope = new MergeScope(scopes);
      }

      if (!defaultLocation.getLongName().isEmpty()) {
        correctedScope = new MergeScope(correctedScope, defaultLocation.getComplementScope());
      } */

      /*
      final Boolean hasProtectedAccessModifier = referable.getAccessModifier() == AccessModifier.PROTECTED;
      final ModuleLocation targetFileLocation = referable.getLocation();

      List<LocatedReferable> ancestors = new LinkedList<>();
      LocatedReferable currAncestor = referable;
      do {
        ancestors.add(currAncestor);
        Boolean ancestorInScope = currentScopeElements.contains(currAncestor);

        currAncestor = currAncestor.getLocatedReferableParent();
      }
      while (currAncestor != null);

      HashSet<ConcreteNamespaceCommand> nsCmds = new HashSet<>();
      group.traverseGroup(subgroup -> subgroup.statements().forEach(statement -> {
        ConcreteNamespaceCommand command = statement.command();
        if (command != null && command.isImport()) {
          ModuleLocation commandTarget = findDependency(new ModulePath(command.module().getPath()),
            anchorModule.getLibraryName(), anchorModule.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
          if (commandTarget.equals(targetFileLocation)) nsCmds.add(command);
        }
      }));


    */}

    return new Pair<>(new RawSequenceModifier(nsCmdActions), result);
  }

  private static @NotNull HashMap<LocatedReferable, List<ConcreteNamespaceCommand>> getExistingNamespaceCommands(@NotNull ConcreteGroup currentFile, Set<LocatedReferable> anchorAncestors) {
    HashMap<LocatedReferable, List<ConcreteNamespaceCommand>> existingNamespaceCommands = new LinkedHashMap<>();
    currentFile.traverseGroup(subgroup -> {
      if (anchorAncestors.contains(subgroup.referable())) {
        subgroup.statements().forEach(statement -> {
          ConcreteNamespaceCommand command = statement.command();
          if (command != null && !command.isImport())
            existingNamespaceCommands.computeIfAbsent(subgroup.referable(), k -> new ArrayList<>()).add(command);
        });
      }
    });
    return existingNamespaceCommands;
  }


  public record ImportDecision(List<String> refName, Boolean alias) implements Comparable<ImportDecision> {
    @Override
    public int compareTo(@NotNull ArendServerImpl.ImportDecision other) {
      int lD = this.refName.size() - other.refName.size();

      if (lD != 0) return lD; // if this is more optimal => result < 0 => this < other
      if (this.alias && !other.alias) return -1;
      if (!this.alias && other.alias) return 1; // other is more optimal

      return this.refName.getFirst().compareTo(this.refName.getLast());
    }
  }

  /* public class ReferenceNameCalculator {
    public static Pair<RawModifier, List<String>> doCalculateReferenceName(
      CalculatedName referableLocation,
      ConcreteGroup currentFile,
      RawAnchor anchor) {

      ModuleReferable targetFile = referableLocation.getContainingFile();
      ModuleLocation targetModulePath = Objects.requireNonNull(targetFile.getModuleLocation());

      List<LocationData> locations = new ArrayList<>();
      locations.add(referableLocation);

      if (referableLocation.getTarget() instanceof ArendClassField ||
        referableLocation.getTarget() instanceof ArendConstructor) {
        LocationData newLocation = LocationData.createLocationData(referableLocation.getTarget(), true);
        if (newLocation != null) locations.add(newLocation);
      }

      if (referableLocation.getTarget() instanceof ReferableBase && ((ReferableBase<?>) referableLocation.getTarget()).getAlias() != null) {
        LocationData newLocation = LocationData.createLocationData(referableLocation.getTarget(), true);
        if (newLocation != null) locations.add(newLocation);
        if (referableLocation.getTarget() instanceof ArendClassField ||
          referableLocation.getTarget() instanceof ArendConstructor) {
          LocationData aliasLocation = LocationData.createLocationData(referableLocation.getTarget(), true, true);
          if (aliasLocation != null) locations.add(aliasLocation);
        }
      }

      NsCmdRefactoringAction fallbackImportAction;
      boolean targetFileAlreadyImported = false;
      boolean preludeImportedManually = false;
      Map<CalculatedName, NsCmdRefactoringAction> fileResolveActions = new HashMap<>();

      for (ArendStatCmd statement : currentFile.getStatements()) {
        NamespaceCommand command = statement.getNamespaceCommand();
        if (command == null) continue;

        String nsCmdLongName = command.getLongName() != null ? command.getLongName().getReferent().textRepresentation() : null;
        if (Prelude.MODULE_PATH.toString().equals(nsCmdLongName)) {
          preludeImportedManually = true;
        }

        if (targetFile.getFullName().equals(nsCmdLongName)) {
          targetFileAlreadyImported = true;
          for (CalculatedName location : locations) {
            location.processStatCmd(command);
          }
        }
      }

      boolean minimalImportMode = referableLocation.getTarget().getAccessModifier() == AccessModifier.PROTECTED ||
        (!targetFile.getFullName().equals(Prelude.MODULE_PATH.toString()) &&
          targetFile.getStatements().stream().anyMatch(stat -> stat.getGroup() != null));

      if (deferredImports != null) {
        for (NsCmdRefactoringAction deferredImport : deferredImports) {
          if (deferredImport.getCurrentFile() == currentFile) {
            if (deferredImport instanceof ImportFileAction) {
              preludeImportedManually |= Prelude.MODULE_PATH.toString().equals(((ImportFileAction) deferredImport).getLongName().toString());
            }
            if (deferredImport.getLongName().toString().equals(targetFile.getFullName())) {
              targetFileAlreadyImported = true;
              for (CalculatedName location : locations) {
                location.processDeferredImport(deferredImport);
              }
            }
          }
        }
      }

      if (targetFileAlreadyImported) {
        for (CalculatedName location : locations) {
          if (location.getReferenceNames().isEmpty() || referableLocation.getTarget().getAccessModifier() == AccessModifier.PROTECTED) {
            location.addLongNameAsReferenceName();
            fileResolveActions.put(location, new AddIdToUsingAction(currentFile, targetFile, location));
          }
        }
        fallbackImportAction = null;
      } else {
        fallbackImportAction = new ImportFileAction(currentFile, targetFile, minimalImportMode ? Collections.emptyList() : null);
        if (isPrelude(targetFile) && !preludeImportedManually) fallbackImportAction = null;

        for (CalculatedName location : locations) {
          List<String> fName = location.getLongName();
          List<String> importList = fName.isEmpty() ? Collections.emptyList() : Collections.singletonList(fName.get(0));
          location.addLongNameAsReferenceName();
          fileResolveActions.put(location, minimalImportMode ? new ImportFileAction(currentFile, targetFile, importList) : fallbackImportAction);
        }
      }

      List<String> veryLongName = new ArrayList<>();
      List<ImportDecision> resultingDecisions = new ArrayList<>();

      for (CalculatedName location : locations) {
        for (List<String> referenceName : location.getReferenceNames()) {
          if (referenceName.isEmpty() || Scope.resolveName(null, referenceName).getAbstractReferable() == referableLocation.getTarget()) {
            resultingDecisions.add(new ImportDecision(referenceName, fileResolveActions.get(location), location.isAlias()));
          }
        }
      }

      if (resultingDecisions.isEmpty()) {
        if (isPrelude(targetFile) && !preludeImportedManually && fallbackImportAction == null) {
          fallbackImportAction = new ImportFileAction(currentFile, targetFile, null);
        }
        veryLongName.addAll(targetModulePath.getModulePath());
        veryLongName.addAll(referableLocation.getLongName());
        resultingDecisions.add(new ImportDecision(veryLongName, fallbackImportAction, false));
      }
      Collections.sort(resultingDecisions);

      List<String> resultingName = resultingDecisions.get(0).getRefName();
      NsCmdRefactoringAction importAction = (targetFile != currentFile || (resultingName.isEmpty() && resultingName.equals(veryLongName))) ? resultingDecisions.get(0).getNsAction() : null;

      return new Pair<>(importAction, resultingName);
    }
  } */
}
