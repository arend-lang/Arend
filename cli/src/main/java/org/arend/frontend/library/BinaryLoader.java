package org.arend.frontend.library;

import org.arend.core.definition.Definition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.module.error.BinaryCacheError;
import org.arend.module.serialization.DeferredBoxFixes;
import org.arend.module.serialization.ModuleDeserialization;
import org.arend.naming.reference.InternalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.server.impl.ArendLibraryImpl;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.source.StreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BinaryLoader {
  private final LibraryManager myLibraryManager;
  private boolean myRecompile = false;
  private final Set<ModuleLocation> myBinaryCacheLoaded = new HashSet<>();

  /** Modules seen in an earlier pass; only first sight may load from {@code .arc}. */
  private final Set<ModuleLocation> mySeenModules = new HashSet<>();

  /**
   * The order phase 2 of the last {@link #loadBinaryCache} deserialized in. Exposed so that the
   * dependencies-first invariant can be asserted rather than trusted; empty until a load has run,
   * and after one that {@link #setRecompile} skipped.
   */
  private List<ModuleLocation> myLoadOrder = Collections.emptyList();

  public BinaryLoader(LibraryManager myLibraryManager) {
    this.myLibraryManager = myLibraryManager;
  }

  /**
   * Returns the set of modules that were successfully loaded from binary cache.
   * These modules do not need to be persisted again.
   */
  public Set<ModuleLocation> getBinaryCacheLoaded() {
    return Collections.unmodifiableSet(myBinaryCacheLoaded);
  }

  /** @see #myLoadOrder */
  public List<ModuleLocation> getLoadOrder() {
    return Collections.unmodifiableList(myLoadOrder);
  }

  public void setRecompile(boolean recompile) {
    myRecompile = recompile;
  }

  /**
   * Loads typechecked definitions from binary caches for the given library.
   * This is a two-phase process:
   * <ol>
   *   <li>Phase 1: For each module with a valid .arc file, parse the protobuf and fill in
   *       Definition shells on the existing (raw-loaded) group. This does not require
   *       dependency modules to be loaded.</li>
   *   <li>Phase 1b: Drop candidates whose callees are not being loaded, since phase 2b
   *       could not resolve their call targets.</li>
   *   <li>Phase 2: Resolve cross-module call targets and fill in definition bodies.
   *       This requires all dependency modules to have completed phase 1.</li>
   * </ol>
   *
   * <p>Phase 2 needs more from its dependencies than phase 1 provides. Resolving a call target
   * only needs the shell -- object identity is preserved, because filling a definition mutates
   * it in place -- but the smart constructors that build the deserialized expressions
   * <em>inspect</em> the callee, and a shell answers those questions wrongly rather than
   * failing. So phase 2 runs dependencies-first, and {@link DeferredBoxFixes} covers what
   * ordering cannot: import cycles mean no order satisfies every module.
   *
   * @param library  the library whose modules should be loaded from binary.
   * @param server   the server containing the raw-loaded modules.
   */
  public void loadBinaryCache(@NotNull SourceLibrary library, @NotNull ArendServer server) {
    if (myRecompile) return;

    ErrorReporter errorReporter = myLibraryManager.getErrorReporter();
    List<PendingBinaryLoad> pending = new ArrayList<>();
    ArendLibrary serverLib = server.getLibrary(library.getLibraryName());
    SerializableKeyRegistryImpl keyRegistry = serverLib instanceof ArendLibraryImpl impl ? impl.getKeyRegistry() : null;

    // Every source module of this library known to the server. A call target outside this set
    // lives in a dependency (or in Prelude) and is loaded by a pass we do not control here.
    Set<ModulePath> libraryModules = new HashSet<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLibraryName().equals(library.getLibraryName()) && module.getLocationKind() == ModuleLocation.LocationKind.SOURCE) {
        libraryModules.add(module.getModulePath());
      }
    }
    // Modules of this library whose definitions will be in memory by the time phase 2b resolves
    // call targets: the ones deserialized below, plus the ones that already carry a core.
    Set<ModulePath> willBeLoaded = new HashSet<>();

    // Phase 1: parse protobuf files (does NOT touch any group referables)
    for (ModuleLocation module : server.getModules()) {
      if (!module.getLibraryName().equals(library.getLibraryName())) continue;
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) continue;

      // Before any early exit below, so "seen" holds whichever branch the earlier pass took.
      boolean firstSight = mySeenModules.add(module);

      PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
      if (binarySource == null) continue;
      long arcTimestamp = binarySource.getTimeStamp();
      if (arcTimestamp <= 0) continue;

      Source rawSource = library.getSource(module.getModulePath(), false);
      if (rawSource != null) {
        long rawTimestamp = rawSource.getTimeStamp();
        if (rawTimestamp > 0 && arcTimestamp < rawTimestamp) {
          // Its cores, if it has any, are invalidated per definition by
          // ArendCheckerImpl.resolveModules -- not wholesale here. Clearing the whole module
          // would drop definitions the edit did not touch, and every dependent holding one of
          // those would then be comparing against a fresh object.
          myBinaryCacheLoaded.remove(module);
          continue;
        }
      }

      // What a warm server already holds beats what is on disk. Re-deserializing a module whose
      // cores are in memory mints fresh Definition objects, and identity is load-bearing: a
      // meta's @Dependency-captured class fields are bound once, when the meta was typechecked,
      // so a freshly deserialized replacement makes ClassCallExpression.isSubClassOf fail and
      // takes linarith and equation down with it.
      ConcreteGroup memGroup = server.getRawGroup(module);
      if (memGroup != null && hasTypechecked(memGroup)) {
        myBinaryCacheLoaded.add(module);
        willBeLoaded.add(module.getModulePath());
        continue;
      }
      // Seen before, still has definitions to typecheck, yet holds no core: it was invalidated
      // since the last pass. Its own .arc describes what it used to be, so restoring from it
      // would put back exactly the state that was just thrown away -- and, because that replaces
      // the core without re-elaborating, never re-bind the metas that captured the old one.
      if (memGroup != null && !firstSight && hasTypecheckableDefinitions(memGroup)) {
        myBinaryCacheLoaded.remove(module);
        continue;
      }

      if (binarySource instanceof StreamBinarySource streamSource) {
        try {
          streamSource.setKeyRegistry(keyRegistry);
          ModuleDeserialization deser = streamSource.parseProtobuf(errorReporter);
          if (deser != null) {
            pending.add(new PendingBinaryLoad(module, deser, calleesInLibrary(deser, module.getModulePath(), libraryModules)));
            willBeLoaded.add(module.getModulePath());
          }
        } catch (Exception e) {
          reportBinaryCacheError(errorReporter, module, "protobuf parsing", e);
          // The .arc exists but is unreadable. Drop any in-memory state a previous pass loaded
          // from it, for the same reason as the stale-mtime branch: without this, a module that
          // suddenly fails to parse silently keeps the state of the cache it can no longer read.
          ConcreteGroup group = server.getRawGroup(module);
          if (group != null) clearTypechecked(group);
          myBinaryCacheLoaded.remove(module);
        }
      }
    }

    // Phase 1b: close the candidate set under "calls into". Phase 1 judges each module against
    // its *own* source only, so an untouched module whose dependency was just edited stays a
    // candidate while the dependency is dropped; phase 2b then asks for a call target that was
    // never filled in and fails with "Definition M:d is not loaded". A .arc is usable only if
    // every module of this library it links against is loaded in the same pass, which is not a
    // property of any single module -- hence a fixed point rather than one sweep.
    //
    // Nothing needs clearing for a module dropped here: it never got past parsing, so
    // re-typechecking it from source is all that is left to do.
    int candidates = pending.size();
    while (true) {
      Set<ModuleLocation> unusable = new HashSet<>();
      for (PendingBinaryLoad load : pending) {
        for (ModulePath callee : load.callees) {
          if (!willBeLoaded.contains(callee)) {
            unusable.add(load.module);
            break;
          }
        }
      }
      if (unusable.isEmpty()) break;
      for (ModuleLocation module : unusable) {
        willBeLoaded.remove(module.getModulePath());
        myBinaryCacheLoaded.remove(module);
      }
      pending.removeIf(load -> unusable.contains(load.module));
    }
    int stale = candidates - pending.size();

    // Process dependencies before dependents, so that phase 2b's expression building sees
    // filled-in callees wherever the import graph allows it.
    pending = sortDependenciesFirst(pending, server);
    myLoadOrder = pending.stream().map(PendingBinaryLoad::module).toList();

    // Phase 2a: fill in Definition shells on all groups (no cross-module scope needed)
    List<PendingBinaryLoad> phase2b = new ArrayList<>();
    for (PendingBinaryLoad load : pending) {
      ConcreteGroup group = server.getRawGroup(load.module);
      if (group == null) continue;
      try {
        load.deserialization.readDefinitions(group);
        phase2b.add(load);
      } catch (Exception e) {
        reportBinaryCacheError(errorReporter, load.module, "definition shell loading", e);
        clearTypechecked(group);
      }
    }

    // Phase 2b: resolve cross-module call targets and fill in definition bodies.
    // Now all modules have their Definition shells from phase 2a, so scope
    // lookups can find cross-module references.
    int loaded = 0;
    int failed = 0;
    int incomplete = 0;
    List<PendingBinaryLoad> loadedLoads = new ArrayList<>();
    DeferredBoxFixes boxFixes = new DeferredBoxFixes();
    for (PendingBinaryLoad load : phase2b) {
      ConcreteGroup group = server.getRawGroup(load.module);
      try {
        load.deserialization.setDeferredBoxFixes(boxFixes);
        load.deserialization.readModule(
            server.getModuleScopeProvider(load.module.getLibraryName(), false),
            dependencyListener(server));
        loaded++;
        myBinaryCacheLoaded.add(load.module);
        loadedLoads.add(load);
      } catch (Exception e) {
        reportBinaryCacheError(errorReporter, load.module, "definition body loading", e);
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

    // Phase 2c: a module that fillInDefinition partway through leaves later
    // definitions in NEEDS_TYPE_CHECKING state with null result type.  Modules
    // that already captured those shell objects (via getCallTarget during their
    // own fillInDefinition — order matters because circular imports like
    // Algebra.StrictlyOrdered ↔ Arith.Nat prevent topological processing) keep
    // holding them.  Likewise, when a module is later cleared, its filled
    // definitions stay reachable through other modules' expression trees but
    // its TCDefReferables now resolve to a freshly re-typechecked replacement,
    // breaking object-identity invariants.
    //
    // Iteratively walk each loaded module's expressions and drop any whose
    // captured Definition references are stale (a shell, or pointing at an
    // object that no longer matches its TCDefReferable's current typechecked).
    int promotedToIncomplete = 0;
    while (true) {
      List<PendingBinaryLoad> toClear = new ArrayList<>();
      for (PendingBinaryLoad load : loadedLoads) {
        ConcreteGroup group = server.getRawGroup(load.module);
        if (group != null && hasOrphanShellReference(group)) {
          toClear.add(load);
        }
      }
      if (toClear.isEmpty()) break;
      for (PendingBinaryLoad load : toClear) {
        ConcreteGroup group = server.getRawGroup(load.module);
        if (group != null) clearTypechecked(group);
        myBinaryCacheLoaded.remove(load.module);
      }
      loadedLoads.removeAll(toClear);
      promotedToIncomplete += toClear.size();
    }
    loaded -= promotedToIncomplete;
    incomplete += promotedToIncomplete;
    if (loaded > 0 || failed > 0 || incomplete > 0 || stale > 0) {
      System.out.println("[INFO] Binary cache: " + loaded + " loaded"
          + (stale > 0 ? ", " + stale + " stale" : "")
          + (incomplete > 0 ? ", " + incomplete + " incomplete" : "")
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
  private static boolean hasTypechecked(ConcreteGroup group) {
    boolean[] found = { false };
    walkDefinitions(group, def -> { found[0] = true; return true; });
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
  private static boolean hasOrphanShellReference(ConcreteGroup group) {
    OrphanShellFinder finder = new OrphanShellFinder();
    walkDefinitions(group, def -> {
      finder.scan(def);
      return finder.found;
    });
    return finder.found;
  }

  /** Walks {@code group} and feeds each typecheckable {@link Definition} to {@code visit}; stops on true. */
  private static void walkDefinitions(ConcreteGroup group, java.util.function.Predicate<Definition> visit) {
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

  private static void clearTypechecked(ConcreteGroup group) {
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
        : new DependencyCollector(null);
  }

  private static void reportBinaryCacheError(ErrorReporter errorReporter, ModuleLocation module, String phase, Exception e) {
    errorReporter.report(new BinaryCacheError(module.getModulePath(), phase, e));
  }

  private record PendingBinaryLoad(ModuleLocation module, ModuleDeserialization deserialization, Set<ModulePath> callees) {}

  /**
   * The modules of {@code libraryModules} that {@code deser}'s call targets point into, excluding
   * {@code self}: a module's own constructors and class fields are listed as call targets too, and
   * those are filled in along with their parent, never by another pass.
   */
  private static Set<ModulePath> calleesInLibrary(ModuleDeserialization deser, ModulePath self, Set<ModulePath> libraryModules) {
    Set<ModulePath> result = new HashSet<>();
    for (ModulePath callee : deser.getCallTargetModules()) {
      if (!callee.equals(self) && libraryModules.contains(callee)) result.add(callee);
    }
    return result;
  }
}
