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
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.source.StreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BinaryLoader {
  private final LibraryManager myLibraryManager;
  private boolean myRecompile = false;
  private final Set<ModuleLocation> myBinaryCacheLoaded = new HashSet<>();

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

    // Phase 1: parse protobuf files (does NOT touch any group referables)
    for (ModuleLocation module : server.getModules()) {
      if (!module.getLibraryName().equals(library.getLibraryName())) continue;
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) continue;

      PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
      if (binarySource == null) continue;
      long arcTimestamp = binarySource.getTimeStamp();
      if (arcTimestamp <= 0) continue;

      Source rawSource = library.getSource(module.getModulePath(), false);
      if (rawSource != null) {
        long rawTimestamp = rawSource.getTimeStamp();
        if (rawTimestamp > 0 && arcTimestamp < rawTimestamp) continue;
      }

      if (binarySource instanceof StreamBinarySource streamSource) {
        try {
          streamSource.setKeyRegistry(keyRegistry);
          ModuleDeserialization deser = streamSource.parseProtobuf(errorReporter);
          if (deser != null) {
            pending.add(new PendingBinaryLoad(module, deser));
          }
        } catch (Exception e) {
          reportBinaryCacheError(errorReporter, module, "protobuf parsing", e);
          // Skip this module — will be re-typechecked from source
        }
      }
    }

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
            new org.arend.typechecking.order.dependency.DependencyCollector(null));
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
    if (loaded > 0 || failed > 0 || incomplete > 0) {
      System.out.println("[INFO] Binary cache: " + loaded + " loaded"
          + (incomplete > 0 ? ", " + incomplete + " incomplete" : "")
          + (failed > 0 ? ", " + failed + " failed" : "")
          + " out of " + pending.size() + " candidates"
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

  private static void reportBinaryCacheError(ErrorReporter errorReporter, ModuleLocation module, String phase, Exception e) {
    errorReporter.report(new BinaryCacheError(module.getModulePath(), phase, e));
  }

  private record PendingBinaryLoad(ModuleLocation module, ModuleDeserialization deserialization) {}
}
