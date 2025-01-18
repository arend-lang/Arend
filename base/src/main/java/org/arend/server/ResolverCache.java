package org.arend.server;

import org.arend.error.DummyErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.*;
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
import java.util.logging.Logger;

public class ResolverCache {
  private final ArendServer myServer;
  private final Logger myLogger = Logger.getLogger(ResolverCache.class.getName());
  private final Map<ModuleLocation, Scope> myModuleScopes = new ConcurrentHashMap<>();
  private final Map<AbstractReference, AbstractReferable> myResolverCache = Collections.synchronizedMap(new WeakHashMap<>());
  private final Map<ModuleLocation, Set<AbstractReference>> myModuleReferences = new ConcurrentHashMap<>();
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
        for (AbstractReference reference : entry.getValue()) {
          myResolverCache.remove(reference);
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

    myModuleScopes.keySet().removeIf(module -> libraries.contains(module.getLibraryName()));
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

  public Scope updateModule(ModuleLocation module, ConcreteGroup group) {
    clearModule(module);
    Scope scope = CachingScope.make(LexicalScope.opened(group));
    myModuleScopes.put(module, scope);
    return scope;
  }

  public void addReference(ModuleLocation module, AbstractReference reference, AbstractReferable referable) {
    myModuleReferences.computeIfAbsent(module, k -> Collections.newSetFromMap(new WeakHashMap<>())).add(reference);
    myResolverCache.put(reference, referable);
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
      AbstractReferable abstractRef = referable.getAbstractReferable();
      if (module != null && abstractRef != null) {
        addReference(module, abstractReference, abstractRef);
      }
    }
  }

  public void addModuleDependency(ModuleLocation module, ModuleLocation dependency) {
    myDirectDependencies.computeIfAbsent(module, k -> new ArrayList<>()).add(dependency);
    myReverseDependencies.computeIfAbsent(dependency, k -> new LinkedHashSet<>()).add(module);
  }

  public @Nullable Scope getModuleScope(@NotNull ModuleLocation module) {
    Scope scope = myModuleScopes.get(module);
    if (scope != null) return scope;
    ConcreteGroup group = myServer.getGroup(module);
    if (group == null) return null;
    synchronized (myServer) {
      return updateModule(module, group);
    }
  }

  public @Nullable AbstractReferable resolveReference(@NotNull AbstractReference reference) {
    AbstractReferable result = myResolverCache.get(reference);
    if (result == TCDefReferable.NULL_REFERABLE) return null;
    if (result != null) return result;

    myLogger.info("Reference " + reference.getReferenceText() + " is not in cache");

    ModuleLocation module = reference.getReferenceModule();
    if (module == null) {
      myResolverCache.putIfAbsent(reference, TCDefReferable.NULL_REFERABLE);
      myLogger.warning("Cannot determine module of " + reference.getReferenceText());
      return null;
    }

    myServer.resolveModules(Collections.singletonList(module), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY);
    result = myResolverCache.get(reference);
    if (result == null) {
      myResolverCache.putIfAbsent(reference, TCDefReferable.NULL_REFERABLE);
      myLogger.warning("Cannot resolve reference " + reference.getReferenceText());
      return null;
    }

    myLogger.info(() -> "Reference " + reference.getReferenceText() + " is added to cache");
    return result == TCDefReferable.NULL_REFERABLE ? null : result;
  }

  public @Nullable AbstractReferable getCachedReferable(@NotNull AbstractReference reference) {
    return myResolverCache.get(reference);
  }
}