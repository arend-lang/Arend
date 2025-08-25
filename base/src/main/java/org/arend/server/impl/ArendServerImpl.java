package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.ArendExtension;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.Precedence;
import org.arend.ext.util.Pair;
import org.arend.ext.module.FullName;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.module.scopeprovider.SimpleModuleScopeProvider;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.typing.*;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.prelude.Prelude;
import org.arend.server.*;
import org.arend.server.modifier.RawModifier;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.ArendExtensionProvider;
import org.arend.typechecking.instance.ArendInstances;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.*;

public class ArendServerImpl implements ArendServer {
  private final static Logger myLogger = Logger.getLogger(ArendServerImpl.class.getName());
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
    public @Nullable ArendExtension getArendExtension(@NotNull String libraryName) {
      ArendLibraryImpl library = myLibraryService.getLibrary(libraryName);
      return library == null ? null : library.getExtension();
    }
  };

  private final InstanceScopeProvider myInstanceScopeProvider = referable -> {
    FullName fullName = referable.getRefFullName();
    if (fullName.module == null) return new ArendInstances();
    GroupData groupData = myGroups.get(fullName.module);
    if (groupData == null) return new ArendInstances();
    DefinitionData defData = groupData.getDefinitionData(fullName.longName);
    return defData == null ? new ArendInstances() : defData.instances();
  };

  public ArendServerImpl(@NotNull ArendServerRequester requester, boolean cacheReferences, boolean withLogging) {
    myRequester = requester;
    myCacheReferences = cacheReferences;
    myLogger.setLevel(withLogging ? Level.INFO : Level.OFF);
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

        for (ConcreteStatement statement : group.statements()) {
          if (statement.group() != null && statement.group().referable().getRefName().equals("Array")) {
            AbstractBody body = typingInfo.getRefBody(statement.group().referable());
            if (body != null) {
              typingInfo.addReferableBody(statement.group().referable(), new AbstractBody(0, body.getReferable(), 0));
            }
          }
        }
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
      if (module.getLocationKind() == ModuleLocation.LocationKind.GENERATED) addGeneratedName(library, subgroup.referable());
      if (subgroup.referable() instanceof TCDefReferable tcRef && tcRef.getData() instanceof AbstractReferable referable) {
        myRequester.addReference(module, referable, tcRef);
      }
      if (subgroup.definition() instanceof Concrete.DataDefinition dataDef) {
        for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
          for (Concrete.Constructor constructor : clause.getConstructors()) {
            if (constructor.getData().getData() instanceof AbstractReferable referable) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.GENERATED) addGeneratedName(library, constructor.getData());
              myRequester.addReference(module, referable, constructor.getData());
            }
          }
        }
      } else if (subgroup.definition() instanceof Concrete.ClassDefinition classDef) {
        for (Concrete.ClassElement element : classDef.getElements()) {
          if (element instanceof Concrete.ClassField field && field.getData().getData() instanceof AbstractReferable referable) {
            if (module.getLocationKind() == ModuleLocation.LocationKind.GENERATED) addGeneratedName(library, field.getData());
            myRequester.addReference(module, referable, field.getData());
          }
        }
      }
    });
  }

  public static @NotNull Map<GlobalReferable, Concrete.GeneralDefinition> updateDefinitions(ConcreteGroup group) {
    Map<GlobalReferable, Concrete.GeneralDefinition> defMap = new HashMap<>();
    group.traverseGroup(subgroup -> {
      Concrete.ResolvableDefinition definition = subgroup.definition();
      if (definition != null) {
        defMap.put(definition.getData(), definition);
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

  @Override
  public @Nullable ModuleLocation findModule(@NotNull ModulePath modulePath, @Nullable String fromLibrary, boolean withTests, boolean withReadOnly) {
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
    if (withTests) kinds.add(ModuleLocation.LocationKind.TEST);
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
        ModuleLocation found = ArendServerImpl.this.findModule(modulePath, libraryName, withTests, true);
        if (found == null) return null;
        GroupData groupData = myGroups.get(found);
        return groupData == null ? null : groupData.getFileScope();
      }

      @Override
      public @NotNull GlobalReferable findModule(@NotNull ModulePath modulePath) {
        ModuleLocation location = modulePath.equals(Prelude.MODULE_PATH) ? Prelude.MODULE_LOCATION : ArendServerImpl.this.findModule(modulePath, libraryName, withTests, true);
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
  public void addErrorReporter(@NotNull ErrorReporter errorReporter) {
    myErrorService.addErrorReporter(errorReporter);
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
      Scope scope = getParentGroupScope(module, group);
      new TypingInfoVisitor(typingInfo).processGroup(group, scope);
      ArendExtension extension = myExtensionProvider.getArendExtension(module.getLibraryName());
      new DefinitionResolveNameVisitor(new SimpleConcreteProvider(updateDefinitions(group)), typingInfo, DummyErrorReporter.INSTANCE, extension == null ? null : extension.getLiteralTypechecker(), new ResolverListener() {
        @Override
        public void resolving(AbstractReference abstractReference, Scope scope, Scope.ScopeContext context, boolean finished) {
          if (reference.equals(abstractReference)) {
            Collection<? extends Referable> elements = scope.getElements(context);
            int i = result.size();
            List<Integer> aliases = new ArrayList<>();
            for (Referable element : elements) {
              result.add(element);
              if (element instanceof AliasReferable) {
                aliases.add(i);
              }
              i++;
            }

            for (int j = aliases.size() - 1; j >= 0; j--) {
              int aliasIndex = aliases.get(j);
              GlobalReferable original = (((AliasReferable) result.get(aliasIndex)).getOriginalReferable());
              if (elements.contains(original)) {
                result.remove(aliasIndex);
              } else {
                result.set(aliasIndex, original);
              }
            }

            found[0] = true;
            if (finished) {
              throw new CompletionException();
            }
          }
        }
      }).resolveGroup(group, scope, new ArendInstances(), null);
    } catch (CompletionException ignored) {
    }

    myLogger.fine(() -> found[0] ? "Finish completion for '" + reference.getReferenceText() + "' with " + result.size() + " results" : "Cannot find completion variants for '" + reference.getReferenceText() + "'");
    return result;
  }

  @Override
  public @Nullable Scope getReferableScope(@NotNull LocatedReferable referable) {
    if (referable.getKind() == GlobalReferable.Kind.CONSTRUCTOR || referable.getKind() == GlobalReferable.Kind.FIELD) {
      LocatedReferable parent = referable.getLocatedReferableParent();
      if (parent != null) referable = parent;
    }

    List<LocatedReferable> ancestors = new ArrayList<>();
    ModuleLocation module = LocatedReferable.Helper.getAncestors(referable, ancestors);
    GroupData groupData = module == null ? null : myGroups.get(module);
    if (groupData == null) return null;

    ConcreteGroup group = groupData.getRawGroup();
    Scope scope = LexicalScope.insideOf(group, getParentGroupScope(module, groupData.getRawGroup()), false);
    loop: for (LocatedReferable ancestor : ancestors) {
      int nextAncestorIndex = ancestors.indexOf(ancestor) + 1;

      for (ConcreteStatement statement : group.statements()) {
        ConcreteGroup subgroup = statement.group();
        if (subgroup != null && subgroup.referable().equals(ancestor)) {
          boolean isDynamicContext = nextAncestorIndex < ancestors.size() &&
            subgroup.dynamicGroups().stream().anyMatch(it ->
              it.referable() == ancestors.get(nextAncestorIndex));
          scope = LexicalScope.insideOf(subgroup, scope, isDynamicContext);
          group = subgroup;
          continue loop;
        }
      }
      for (ConcreteGroup subgroup : group.dynamicGroups()) {
        if (subgroup.referable().equals(ancestor)) {
          boolean isDynamicContext = nextAncestorIndex < ancestors.size() &&
            subgroup.dynamicGroups().stream().anyMatch(it ->
              it.referable() == ancestors.get(nextAncestorIndex));
          scope = LexicalScope.insideOf(subgroup, scope, isDynamicContext);
          group = subgroup;
          continue loop;
        }
      }
      return null;
    }

    if (referable.getKind().isRecord()) {
      DynamicScopeProvider dynamicScopeProvider = myTypingInfo.getDynamicScopeProvider(referable);
      if (dynamicScopeProvider != null) {
        scope = new MergeScope(new DynamicScope(dynamicScopeProvider, myTypingInfo, DynamicScope.Extent.WITH_SUPER_DYNAMIC), scope);
      }
    }

    return CachingScope.make(scope);
  }

  @Override
  public @NotNull Pair<RawModifier, List<LongName>> makeReferencesAvailable(@NotNull List<LocatedReferable> referables, @Nullable ConcreteGroup group, @NotNull RawAnchor anchor, @NotNull ErrorReporter errorReporter) {
    SingleFileReferenceResolver resolver = new SingleFileReferenceResolver(this, errorReporter, group);
    ArrayList<LongName> longNames = new ArrayList<>(referables.size());
    for (LocatedReferable referable : referables) {
      longNames.add(resolver.calculateLongName(referable, anchor));
    }
    return new Pair<>(resolver.getModifier(), longNames);
  }
}
