package org.arend.server.impl;

import org.arend.error.DummyErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.ResolverListener;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.dfs.MapDFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ResolverCache {
  private record ModuleCache(Set<AbstractReferable> referables, Set<AbstractReference> references) {
    void addReferable(AbstractReferable referable) {
      referables.add(referable);
    }

    void addReference(AbstractReference reference) {
      references.add(reference);
    }
  }

  private final ArendServerImpl myServer;
  private final Logger myLogger = Logger.getLogger(ResolverCache.class.getName());
  private final Map<AbstractReference, Referable> myResolverCache = Collections.synchronizedMap(new WeakHashMap<>());
  private final Map<AbstractReferable, TCDefReferable> myReferableCache = Collections.synchronizedMap(new WeakHashMap<>());
  private final Map<ModuleLocation, ModuleCache> myModuleReferences = new ConcurrentHashMap<>();
  private final Map<ModuleLocation, List<ModuleLocation>> myDirectDependencies = new ConcurrentHashMap<>();
  private final Map<ModuleLocation, Set<ModuleLocation>> myReverseDependencies = new ConcurrentHashMap<>();

  public ResolverCache(ArendServerImpl server) {
    myServer = server;
    server.copyLogger(myLogger);
  }

  public void clearLibraries(Set<String> libraries) {
    for (var it = myModuleReferences.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      if (libraries.contains(entry.getKey().getLibraryName())) {
        it.remove();
        for (AbstractReference reference : entry.getValue().references) {
          myResolverCache.remove(reference);
        }
        for (AbstractReferable referable : entry.getValue().referables) {
          myReferableCache.remove(referable);
        }
      }
    }

    for (var iterator = myDirectDependencies.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      if (libraries.contains(entry.getKey().getLibraryName())) {
        iterator.remove();
      } else {
        entry.getValue().removeIf(dependency -> libraries.contains(dependency.getLibraryName()));
      }
    }

    for (var iterator = myReverseDependencies.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      if (libraries.contains(entry.getKey().getLibraryName())) {
        iterator.remove();
      } else {
        entry.getValue().removeIf(dependency -> libraries.contains(dependency.getLibraryName()));
      }
    }
  }

  public void clearModule(ModuleLocation module) {
    MapDFS<ModuleLocation> dfs = new MapDFS<>(myReverseDependencies);
    dfs.visit(module);

    for (ModuleLocation dependency : dfs.getVisited()) {
      ModuleCache cache = myModuleReferences.remove(dependency);
      if (cache != null) {
        for (AbstractReference reference : cache.references) {
          myResolverCache.remove(reference);
        }
        for (AbstractReferable referable : cache.referables) {
          myReferableCache.remove(referable);
        }
      }
    }

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

  public void addReference(ModuleLocation module, AbstractReference reference, Referable referable) {
    myModuleReferences.computeIfAbsent(module, k -> new ModuleCache(Collections.newSetFromMap(new WeakHashMap<>()), Collections.newSetFromMap(new WeakHashMap<>()))).addReference(reference);
    myResolverCache.put(reference, referable);
  }

  public void addReferable(ModuleLocation module, AbstractReferable referable, TCDefReferable tcReferable) {
    myModuleReferences.computeIfAbsent(module, k -> new ModuleCache(Collections.newSetFromMap(new WeakHashMap<>()), Collections.newSetFromMap(new WeakHashMap<>()))).addReferable(referable);
    myReferableCache.put(referable, tcReferable);
  }

  public void addReference(UnresolvedReference reference, Referable referable) {
    if (reference instanceof LongUnresolvedReference unresolved) {
      List<AbstractReference> references = unresolved.getReferenceList();
      if (references.isEmpty()) return;
      ModuleLocation module = references.get(0).getReferenceModule();
      if (module == null) return;
      for (int i = unresolved.getPath().size() - 1; i >= 0; i--) {
        if (i < references.size() && references.get(i) != null) {
          addReference(module, references.get(i), referable);
        }
        if (!(referable instanceof LocatedReferable)) return;
        referable = ((LocatedReferable) referable).getLocatedReferableParent();
        if (referable == null) return;
      }
    } else if (reference.getData() instanceof AbstractReference abstractReference) {
      ModuleLocation module = abstractReference.getReferenceModule();
      if (module != null) {
        addReference(module, abstractReference, referable);
      }
    }
  }

  public void addModuleDependency(ModuleLocation module, ModuleLocation dependency) {
    myDirectDependencies.computeIfAbsent(module, k -> new ArrayList<>()).add(dependency);
    myReverseDependencies.computeIfAbsent(dependency, k -> new LinkedHashSet<>()).add(module);
  }

  public @Nullable Referable resolveReference(@NotNull AbstractReference reference) {
    Referable result = myResolverCache.get(reference);
    if (result == TCDefReferable.NULL_REFERABLE) return null;
    if (result != null) return result;

    myLogger.info("Reference " + reference.getReferenceText() + " is not in cache");

    ModuleLocation module = reference.getReferenceModule();
    if (module == null) {
      myResolverCache.putIfAbsent(reference, TCDefReferable.NULL_REFERABLE);
      myLogger.warning("Cannot determine module of " + reference.getReferenceText());
      return null;
    }

    GroupData groupData = myServer.getGroupData(module);
    if (groupData != null && groupData.isResolved()) {
      myResolverCache.putIfAbsent(reference, TCDefReferable.NULL_REFERABLE);
      myLogger.warning("Reference " + reference.getReferenceText() + " was not resolved");
      return null;
    }

    myServer.resolveModules(Collections.singletonList(module), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY, myServer.getDependencies(Collections.singletonList(module), UnstoppableCancellationIndicator.INSTANCE), false, false);
    result = myResolverCache.get(reference);

    if (result == null) {
      myResolverCache.putIfAbsent(reference, TCDefReferable.NULL_REFERABLE);
      myLogger.warning("Cannot resolve reference " + reference.getReferenceText());
      return null;
    }

    myLogger.info(() -> "Reference " + reference.getReferenceText() + " is added to cache");
    return result == TCDefReferable.NULL_REFERABLE ? null : result;
  }

  public @Nullable Referable getCachedReferable(@NotNull AbstractReference reference) {
    return myResolverCache.get(reference);
  }

  public TCDefReferable getTCReferable(@NotNull AbstractReferable referable) {
    return myReferableCache.get(referable);
  }
}