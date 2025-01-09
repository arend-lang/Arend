package org.arend.server;

import org.arend.error.DummyErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.dfs.MapDFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class ResolverCache {
  private final ArendServer myServer;
  private final Logger myLogger = Logger.getLogger(ResolverCache.class.getName());
  private final Map<ModuleLocation, Scope> myModuleScopes = new ConcurrentHashMap<>();
  private final Map<AbstractReference, AbstractReferable> myResolverCache = Collections.synchronizedMap(new WeakHashMap<>());
  private final Map<ModuleLocation, Set<AbstractReference>> myModuleReferences = new ConcurrentHashMap<>();
  private final Map<ModuleLocation, List<ModuleLocation>> myDirectDependencies = new ConcurrentHashMap<>();
  private final Map<ModuleLocation, Set<ModuleLocation>> myReverseDependencies = new ConcurrentHashMap<>();

  public ResolverCache(ArendServer server, Logger originalLogger) {
    myServer = server;
    myLogger.setLevel(originalLogger.getLevel());
    myLogger.setUseParentHandlers(originalLogger.getUseParentHandlers());
    for (Handler handler : originalLogger.getHandlers()) {
      myLogger.addHandler(handler);
    }
  }

  public void clearLibrary(String libraryName) {
    for (var it = myModuleReferences.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      if (entry.getKey().getLibraryName().equals(libraryName)) {
        it.remove();
        for (AbstractReference reference : entry.getValue()) {
          myResolverCache.remove(reference);
        }
      }
    }

    for (var iterator = myDirectDependencies.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      if (entry.getKey().getLibraryName().equals(libraryName)) {
        iterator.remove();
      } else {
        entry.getValue().removeIf(dependency -> dependency.getLibraryName().equals(libraryName));
      }
    }

    for (var iterator = myReverseDependencies.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      if (entry.getKey().getLibraryName().equals(libraryName)) {
        iterator.remove();
      } else {
        entry.getValue().removeIf(dependency -> dependency.getLibraryName().equals(libraryName));
      }
    }

  }

  public void clearModule(ModuleLocation module) {
    MapDFS<ModuleLocation> dfs = new MapDFS<>(myReverseDependencies);
    dfs.visit(module);

    for (ModuleLocation dependency : dfs.getVisited()) {
      Set<AbstractReference> references = myModuleReferences.remove(dependency);
      if (references != null) {
        for (AbstractReference reference : references) {
          myResolverCache.remove(reference);
        }
      }
    }

    myModuleScopes.remove(module);
    List<ModuleLocation> dependencies = myDirectDependencies.remove(module);
    if (dependencies != null) {
      for (ModuleLocation dependency : dependencies) {
        Set<ModuleLocation> reverseDependencies = myReverseDependencies.get(dependency);
        if (reverseDependencies != null) {
          reverseDependencies.remove(module);
        }
      }
    }
  }

  public void updateModule(ModuleLocation module, ConcreteGroup group) {
    clearModule(module);
    myModuleScopes.put(module, CachingScope.make(LexicalScope.opened(group)));
  }

  public void addReference(ModuleLocation module, AbstractReference reference, AbstractReferable referable) {
    myModuleReferences.computeIfAbsent(module, k -> Collections.newSetFromMap(new WeakHashMap<>())).add(reference);
    myResolverCache.put(reference, referable);
  }

  public void addModuleDependency(ModuleLocation module, ModuleLocation dependency) {
    myDirectDependencies.computeIfAbsent(module, k -> new ArrayList<>()).add(dependency);
    myReverseDependencies.computeIfAbsent(dependency, k -> new LinkedHashSet<>()).add(module);
  }

  public @Nullable Scope getModuleScope(@NotNull ModuleLocation module) {
    return myModuleScopes.get(module);
  }

  public @Nullable AbstractReferable resolveReference(@NotNull AbstractReference reference) {
    AbstractReferable result = myResolverCache.get(reference);
    if (result == TCDefReferable.NULL_REFERABLE) return null;
    if (result != null) return result;

    myLogger.info("Reference " + reference + " is not in cache");

    ModuleLocation module = reference.getReferenceModule();
    if (module == null) {
      myLogger.warning("Cannot determine module of " + reference);
      return null;
    }

    myServer.resolveModules(Collections.singletonList(module), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY);
    result = myResolverCache.get(reference);
    if (result == null) {
      myLogger.warning("Cannot resolve reference " + reference);
      return null;
    }

    myLogger.info("Reference " + reference + " is added to cache");
    return result == TCDefReferable.NULL_REFERABLE ? null : result;
  }
}
