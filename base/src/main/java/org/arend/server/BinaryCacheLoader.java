package org.arend.server;

import org.arend.core.definition.Definition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.module.error.BinaryCacheError;
import org.arend.module.serialization.DeferredBoxFixes;
import org.arend.module.serialization.ModuleDeserialization;
import org.arend.naming.reference.InternalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.impl.ArendLibraryImpl;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.StreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

public class BinaryCacheLoader {
  private final ArendServer myServer;
  private final ErrorReporter myErrorReporter;
  private final Consumer<String> myLogger;
  private final Set<ModuleLocation> myBinaryCacheLoaded = new HashSet<>();
  // What the last load did with each module of the library, for diagnostics: "loaded", or why it was not
  private final Map<ModuleLocation, String> myOutcomes = new LinkedHashMap<>();

  /** Modules seen in an earlier pass; only first sight may load from {@code .arc}. */
  private final Set<ModuleLocation> mySeenModules = new HashSet<>();

  /**
   * The order phase 2 of the last {@link #loadBinaryCache} deserialized in. Exposed so that the
   * dependencies-first invariant can be asserted rather than trusted; empty until a load has run.
   */
  private List<ModuleLocation> myLoadOrder = Collections.emptyList();

  public BinaryCacheLoader(@NotNull ArendServer server, @NotNull ErrorReporter errorReporter, Consumer<String> logger) {
    this.myServer = server;
    this.myErrorReporter = errorReporter;
    this.myLogger = logger;
  }

  public @NotNull ArendServer getServer() {
    return myServer;
  }

  /**
   * Returns the set of modules that were successfully loaded from binary cache.
   * These modules do not need to be persisted again.
   */
  public Set<ModuleLocation> getBinaryCacheLoaded() {
    return Collections.unmodifiableSet(myBinaryCacheLoaded);
  }

  /** @see #myOutcomes */
  public Map<ModuleLocation, String> getOutcomes() {
    return Collections.unmodifiableMap(myOutcomes);
  }

  /** @see #myLoadOrder */
  public List<ModuleLocation> getLoadOrder() {
    return Collections.unmodifiableList(myLoadOrder);
  }

  /**
   * Loads typechecked definitions from binary caches for the given library.
   * This is a two-phase process:
   * <ol>
   *   <li>Phase 1: For each module with a valid .arc file, parse the protobuf and fill in
   *       Definition shells on the existing (raw-loaded) group. This does not require
   *       dependency modules to be loaded.</li>
   *   <li>Phase 2: Resolve cross-module call targets and fill in definition bodies.
   *       This requires all dependency modules to have completed phase 1. A definition that
   *       refers to a definition which is not loaded (its module has no usable .arc, or its
   *       .arc was saved without it) is dropped and left to typechecking, and so is a definition
   *       that holds a dropped one; the rest of its module is loaded.</li>
   * </ol>
   *
   * <p>Phase 2 needs more from its dependencies than phase 1 provides. Resolving a call target
   * only needs the shell -- object identity is preserved, because filling a definition mutates
   * it in place -- but the smart constructors that build the deserialized expressions
   * <em>inspect</em> the callee, and a shell answers those questions wrongly rather than
   * failing. So phase 2 runs dependencies-first, and {@link DeferredBoxFixes} covers what
   * ordering cannot: import cycles mean no order satisfies every module.
   *
   * @param libraryName            the name of the library whose modules should be loaded from binary.
   * @param binarySourceProvider   provides the binary source for a module, or {@code null} if unavailable.
   * @param rawTimestampProvider   provides the timestamp of the raw source for a module, or 0 if it has none.
   */
  public void loadBinaryCache(@NotNull String libraryName,
                              @NotNull Function<ModuleLocation, StreamBinarySource> binarySourceProvider,
                              @NotNull ToLongFunction<ModuleLocation> rawTimestampProvider) {
    List<PendingBinaryLoad> pending = new ArrayList<>();
    ArendLibrary serverLib = myServer.getLibrary(libraryName);
    SerializableKeyRegistryImpl keyRegistry = serverLib instanceof ArendLibraryImpl impl ? impl.getKeyRegistry() : null;


    // Phase 1: parse protobuf files (does NOT touch any group referables)
    for (ModuleLocation module : myServer.getModules()) {
      if (!module.getLibraryName().equals(libraryName)) continue;
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) continue;

      // Before any early exit below, so "seen" holds whichever branch the earlier pass took.
      boolean firstSight = mySeenModules.add(module);

      StreamBinarySource binarySource = binarySourceProvider.apply(module);
      if (binarySource == null) {
        myOutcomes.put(module, "no .arc");
        continue;
      }
      long arcTimestamp = binarySource.getTimeStamp();
      if (arcTimestamp <= 0) {
        myOutcomes.put(module, "no timestamp of the .arc");
        continue;
      }

      long rawTimestamp = rawTimestampProvider.applyAsLong(module);
      if (rawTimestamp > 0 && arcTimestamp < rawTimestamp) {
        myOutcomes.put(module, "stale: the .arc (" + arcTimestamp + ") is older than the source (" + rawTimestamp + ")");
        // Its cores, if it has any, are invalidated per definition by
        // ArendCheckerImpl.resolveModules -- not wholesale here. Clearing the whole module
        // would drop definitions the edit did not touch, and every dependent holding one of
        // those would then be comparing against a fresh object.
        myBinaryCacheLoaded.remove(module);
        continue;
      }

      // What a warm server already holds beats what is on disk. Re-deserializing a module whose
      // cores are in memory mints fresh Definition objects, and identity is load-bearing: a
      // meta's @Dependency-captured class fields are bound once, when the meta was typechecked,
      // so a freshly deserialized replacement makes ClassCallExpression.isSubClassOf fail and
      // takes linarith and equation down with it.
      ConcreteGroup memGroup = myServer.getRawGroup(module);
      if (memGroup != null && hasTypechecked(memGroup)) {
        myOutcomes.put(module, "kept: already in memory");
        myBinaryCacheLoaded.add(module);
        continue;
      }
      // Seen before, still has definitions to typecheck, yet holds no core: it was invalidated
      // since the last pass. Its own .arc describes what it used to be, so restoring from it
      // would put back exactly the state that was just thrown away -- and, because that replaces
      // the core without re-elaborating, never re-bind the metas that captured the old one.
      if (memGroup != null && !firstSight && hasTypecheckableDefinitions(memGroup)) {
        myOutcomes.put(module, "invalidated since the last load");
        myBinaryCacheLoaded.remove(module);
        continue;
      }

      try {
        binarySource.setKeyRegistry(keyRegistry);
        ModuleDeserialization deser = binarySource.parseProtobuf(myErrorReporter);
        if (deser != null) {
          pending.add(new PendingBinaryLoad(module, deser));
          myOutcomes.put(module, "candidate");
        } else {
          myOutcomes.put(module, "unreadable .arc");
        }
      } catch (Exception e) {
        myOutcomes.put(module, "unreadable .arc: " + e);
        reportBinaryCacheError(myErrorReporter, module, "protobuf parsing", e);
        // The .arc exists but is unreadable. Drop any in-memory state a previous pass loaded
        // from it, for the same reason as the stale-mtime branch: without this, a module that
        // suddenly fails to parse silently keeps the state of the cache it can no longer read.
        ConcreteGroup group = myServer.getRawGroup(module);
        if (group != null) clearTypechecked(group);
        myBinaryCacheLoaded.remove(module);
      }
    }

    // A .arc that calls into a definition which is not loaded in this pass (its module has no usable .arc, or its
    // .arc was saved without it) is not dropped here as a whole: phase 2b drops just the definitions that call it,
    // and phase 2c those that hold one of them, and the rest of the module is loaded.
    int candidates = pending.size();

    // Process dependencies before dependents, so that phase 2b's expression building sees
    // filled-in callees wherever the import graph allows it.
    pending = sortDependenciesFirst(pending, myServer);
    myLoadOrder = pending.stream().map(PendingBinaryLoad::module).toList();

    // Phase 2a: fill in Definition shells on all groups (no cross-module scope needed)
    List<PendingBinaryLoad> phase2b = new ArrayList<>();
    for (PendingBinaryLoad load : pending) {
      ConcreteGroup group = myServer.getRawGroup(load.module);
      if (group == null) continue;
      try {
        load.deserialization.readDefinitions(group);
        phase2b.add(load);
      } catch (Exception e) {
        myOutcomes.put(load.module, "failed: definition shells: " + e);
        reportBinaryCacheError(myErrorReporter, load.module, "definition shell loading", e);
        clearTypechecked(group);
      }
    }

    // Phase 2b: resolve cross-module call targets and fill in definition bodies.
    // Now all modules have their Definition shells from phase 2a, so scope
    // lookups can find cross-module references. A definition that refers to one
    // that is not loaded is dropped, not its module.
    int failed = 0;
    List<PendingBinaryLoad> loadedLoads = new ArrayList<>();
    Map<ModuleLocation, Integer> definitionCounts = new HashMap<>();
    Map<ModuleLocation, List<String>> droppedDefinitions = new HashMap<>();
    DeferredBoxFixes boxFixes = new DeferredBoxFixes();
    for (PendingBinaryLoad load : phase2b) {
      ConcreteGroup group = myServer.getRawGroup(load.module);
      try {
        load.deserialization.setDeferredBoxFixes(boxFixes);
        List<Pair<Definition, String>> dropped = load.deserialization.readModuleDroppingMissing(
            myServer.getModuleScopeProvider(load.module.getLibraryName(), false),
            dependencyListener(myServer));
        definitionCounts.put(load.module, load.deserialization.getDefinitionCount());
        for (Pair<Definition, String> pair : dropped) {
          droppedDefinitions.computeIfAbsent(load.module, k -> new ArrayList<>()).add(pair.proj1.getName() + " (" + pair.proj2 + ")");
        }
        loadedLoads.add(load);
      } catch (Exception e) {
        myOutcomes.put(load.module, "failed: definition bodies: " + e);
        reportBinaryCacheError(myErrorReporter, load.module, "definition body loading", e);
        failed++;
        if (group != null) {
          clearTypechecked(group);
        }
      }
    }

    // Every module of this pass is filled in now, so any defcall that was built while its
    // callee was still a shell can finally get its \box wrapping. Must happen before phase 2c,
    // which inspects the loaded expressions.
    int boxFixCount = boxFixes.apply();

    // Phase 2c: a definition can hold another one that is not loaded after all: one dropped in
    // phase 2b after it was filled (a forward reference within a module, or an import cycle such
    // as Algebra.StrictlyOrdered <-> Arith.Nat, which prevents processing dependencies first), or
    // one whose TCDefReferable now resolves to something else, which breaks object identity.
    // Such a definition is dropped too, until none is left: just the definition, so that the rest
    // of its module stays loaded.
    while (true) {
      List<Pair<PendingBinaryLoad, Definition>> toDrop = new ArrayList<>();
      for (PendingBinaryLoad load : loadedLoads) {
        ConcreteGroup group = myServer.getRawGroup(load.module);
        if (group == null) continue;
        walkDefinitions(group, def -> {
          OrphanShellFinder finder = new OrphanShellFinder();
          finder.scan(def);
          if (finder.found) toDrop.add(new Pair<>(load, def));
          return false;
        });
      }
      if (toDrop.isEmpty()) break;
      for (Pair<PendingBinaryLoad, Definition> pair : toDrop) {
        ModuleDeserialization.dropDefinition(pair.proj2);
        droppedDefinitions.computeIfAbsent(pair.proj1.module, k -> new ArrayList<>()).add(pair.proj2.getName() + " (refers to a definition that is not loaded)");
      }
    }

    // A module is loaded if all of its definitions in the .arc are; a module loaded in part keeps
    // those, and is not reported as loaded, so that the rest of it is typechecked and saved again.
    int loaded = 0;
    int partial = 0;
    int droppedCount = 0;
    for (PendingBinaryLoad load : loadedLoads) {
      List<String> dropped = droppedDefinitions.get(load.module);
      if (dropped == null) {
        loaded++;
        myBinaryCacheLoaded.add(load.module);
        myOutcomes.put(load.module, "loaded");
      } else {
        partial++;
        droppedCount += dropped.size();
        myBinaryCacheLoaded.remove(load.module);
        int total = definitionCounts.getOrDefault(load.module, dropped.size());
        myOutcomes.put(load.module, "loaded partially: " + Math.max(0, total - dropped.size()) + " of " + total
            + " definitions, dropped " + String.join(", ", dropped));
      }
    }

    if ((loaded > 0 || partial > 0 || failed > 0) && myLogger != null) {
      myLogger.accept("Binary cache: " + loaded + " loaded"
          + (partial > 0 ? ", " + partial + " loaded partially (" + droppedCount + " definitions dropped)" : "")
          + (failed > 0 ? ", " + failed + " failed" : "")
          + " out of " + candidates + " candidates"
          // Routinely non-zero and not a warning: ordering only sequences whole modules, so any
          // forward reference within a module -- plus every import cycle -- still builds a
          // defcall before its callee is filled. Reported because this is the path that used to
          // silently produce bogus source-level errors.
          + (boxFixCount > 0 ? ", " + boxFixCount + " deferred box fix(es)" : ""));
    }
  }

  /**
   * Orders {@code pending} so that a module comes after everything it imports. Import cycles are
   * unavoidable in practice (e.g. {@code Algebra.StrictlyOrdered} and {@code Arith.Nat} through
   * {@code LinearlyOrderedCSemiring.Dec} / {@code NatSemiring}); the DFS breaks them at an
   * arbitrary edge rather than failing, and {@link DeferredBoxFixes} repairs the fallout.
   */
  private static List<PendingBinaryLoad> sortDependenciesFirst(List<PendingBinaryLoad> pending, ArendServer server) {
    Map<ModulePath, PendingBinaryLoad> byPath = new HashMap<>();
    for (PendingBinaryLoad load : pending) {
      byPath.put(load.module().getModulePath(), load);
    }
    List<PendingBinaryLoad> sorted = new ArrayList<>(pending.size());
    Set<PendingBinaryLoad> done = new HashSet<>();
    Set<PendingBinaryLoad> onPath = new HashSet<>();
    for (PendingBinaryLoad load : pending) {
      visitDependencies(load, byPath, server, done, onPath, sorted);
    }
    return sorted;
  }

  private static void visitDependencies(PendingBinaryLoad load, Map<ModulePath, PendingBinaryLoad> byPath,
                                        ArendServer server, Set<PendingBinaryLoad> done,
                                        Set<PendingBinaryLoad> onPath, List<PendingBinaryLoad> sorted) {
    if (done.contains(load) || !onPath.add(load)) return;
    ConcreteGroup group = server.getRawGroup(load.module());
    if (group != null) {
      for (ConcreteStatement statement : group.statements()) {
        if (statement.command() == null || !statement.command().isImport()) continue;
        PendingBinaryLoad dependency = byPath.get(new ModulePath(statement.command().module().getPath()));
        if (dependency != null && dependency != load) {
          visitDependencies(dependency, byPath, server, done, onPath, sorted);
        }
      }
    }
    onPath.remove(load);
    done.add(load);
    sorted.add(load);
  }

  /** True if any definition reachable from {@code group} already holds a typechecked core. */
  public static boolean hasTypechecked(ConcreteGroup group) {
    boolean[] found = { false };
    walkDefinitions(group, def -> { found[0] = true; return true; });
    return found[0];
  }

  public static boolean hasIncompleteDefinition(ConcreteGroup group) {
    boolean[] found = { false };
    walkDefinitions(group, def -> { found[0] = def.status().needsTypeChecking(); return found[0]; });
    return found[0];
  }

  /**
   * True if anything reachable from {@code group} is typecheckable, whether or not it holds a
   * core. Separates a module that <em>lost</em> its definitions from one that never had any --
   * without it, a module with nothing to typecheck looks invalidated on every pass.
   */
  private static boolean hasTypecheckableDefinitions(ConcreteGroup group) {
    if (group.referable() instanceof TCDefReferable ref && ref.getKind().isTypecheckable()) return true;
    for (InternalReferable internalRef : group.getInternalReferables()) {
      if (internalRef instanceof TCDefReferable ref && ref.getKind().isTypecheckable()) return true;
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && hasTypecheckableDefinitions(statement.group())) return true;
    }
    for (ConcreteGroup dynamicGroup : group.dynamicGroups()) {
      if (hasTypecheckableDefinitions(dynamicGroup)) return true;
    }
    return false;
  }

  /**
   * Returns true if any typecheckable definition in the group has an expression
   * (parameter type, result type, body) that references another {@link Definition}
   * still stuck in {@link Definition.TypeCheckingStatus#NEEDS_TYPE_CHECKING} — i.e.
   * an orphan shell left behind when its owning module's phase-2b failed partway
   * through fillInDefinition.
   */
  public static boolean hasOrphanShellReference(ConcreteGroup group) {
    OrphanShellFinder finder = new OrphanShellFinder();
    walkDefinitions(group, def -> {
      finder.scan(def);
      return finder.found;
    });
    return finder.found;
  }

  /** Walks {@code group} and feeds each typecheckable {@link Definition} to {@code visit}; stops on true. */
  private static void walkDefinitions(ConcreteGroup group, Predicate<Definition> visit) {
    LocatedReferable ref = group.referable();
    if (ref instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
      Definition def = tcRef.getTypechecked();
      if (def != null && visit.test(def)) return;
    }
    for (InternalReferable internalRef : group.getInternalReferables()) {
      if (internalRef instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
        Definition def = tcRef.getTypechecked();
        if (def != null && visit.test(def)) return;
      }
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) walkDefinitions(statement.group(), visit);
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      walkDefinitions(dynGroup, visit);
    }
  }

  public static void clearTypechecked(ConcreteGroup group) {
    LocatedReferable ref = group.referable();
    if (ref instanceof TCDefReferable tcRef) {
      tcRef.setTypechecked(null);
    }
    for (var internalRef : group.getInternalReferables()) {
      internalRef.setTypechecked(null);
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) {
        clearTypechecked(statement.group());
      }
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      clearTypechecked(dynGroup);
    }
  }

  /**
   * The server's own dependency graph, so edges recorded while deserializing survive the call.
   * Falls back to a throwaway for servers that expose none (test doubles), which consume no edges.
   */
  private static DependencyListener dependencyListener(ArendServer server) {
    return server instanceof ArendServerImpl impl
        ? impl.getDependencyCollector()
        : new DependencyCollector(server);
  }

  private static void reportBinaryCacheError(ErrorReporter errorReporter, ModuleLocation module, String phase, Exception e) {
    errorReporter.report(new BinaryCacheError(module.getModulePath(), phase, e));
  }

  private record PendingBinaryLoad(ModuleLocation module, ModuleDeserialization deserialization) {}

}
