package org.arend.server;

import org.arend.core.definition.Definition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.module.error.BinaryCacheError;
import org.arend.module.serialization.ModuleDeserialization;
import org.arend.naming.reference.InternalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.impl.ArendLibraryImpl;
import org.arend.source.StreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.order.dependency.DependencyCollector;
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
   * @param libraryName            the name of the library whose modules should be loaded from binary.
   * @param binarySourceProvider   provides the binary source for a module, or {@code null} if unavailable.
   * @param rawTimestampProvider   provides the timestamp of the raw source for a module.
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

      StreamBinarySource binarySource = binarySourceProvider.apply(module);
      if (binarySource == null) continue;
      long arcTimestamp = binarySource.getTimeStamp();
      if (arcTimestamp <= 0) continue;

      long rawTimestamp = rawTimestampProvider.applyAsLong(module);
      if (rawTimestamp > 0 && arcTimestamp < rawTimestamp) continue;

      try {
        binarySource.setKeyRegistry(keyRegistry);
        ModuleDeserialization deser = binarySource.parseProtobuf(myErrorReporter);
        if (deser != null) {
          pending.add(new PendingBinaryLoad(module, deser));
        }
      } catch (Exception e) {
        reportBinaryCacheError(myErrorReporter, module, "protobuf parsing", e);
        // Skip this module — will be re-typechecked from source
      }
    }

    // Phase 2a: fill in Definition shells on all groups (no cross-module scope needed)
    List<PendingBinaryLoad> phase2b = new ArrayList<>();
    for (PendingBinaryLoad load : pending) {
      ConcreteGroup group = myServer.getRawGroup(load.module);
      if (group == null) continue;
      try {
        load.deserialization.readDefinitions(group);
        phase2b.add(load);
      } catch (Exception e) {
        reportBinaryCacheError(myErrorReporter, load.module, "definition shell loading", e);
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
    for (PendingBinaryLoad load : phase2b) {
      ConcreteGroup group = myServer.getRawGroup(load.module);
      try {
        load.deserialization.readModule(
                myServer.getModuleScopeProvider(load.module.getLibraryName(), false), new DependencyCollector(myServer));
        loaded++;
        myBinaryCacheLoaded.add(load.module);
        loadedLoads.add(load);
      } catch (Exception e) {
        reportBinaryCacheError(myErrorReporter, load.module, "definition body loading", e);
        failed++;
        if (group != null) {
          clearTypechecked(group);
        }
      }
    }

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
        ConcreteGroup group = myServer.getRawGroup(load.module);
        if (group != null && hasOrphanShellReference(group)) {
          toClear.add(load);
        }
      }
      if (toClear.isEmpty()) break;
      for (PendingBinaryLoad load : toClear) {
        ConcreteGroup group = myServer.getRawGroup(load.module);
        if (group != null) clearTypechecked(group);
        myBinaryCacheLoaded.remove(load.module);
      }
      loadedLoads.removeAll(toClear);
      promotedToIncomplete += toClear.size();
    }
    loaded -= promotedToIncomplete;
    incomplete += promotedToIncomplete;
    if ((loaded > 0 || failed > 0 || incomplete > 0) && myLogger != null) {
      myLogger.accept("Binary cache: " + loaded + " loaded"
              + (incomplete > 0 ? ", " + incomplete + " incomplete" : "")
              + (failed > 0 ? ", " + failed + " failed" : "")
              + " out of " + pending.size() + " candidates");
    }
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
