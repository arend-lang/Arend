package org.arend.frontend.library;

import org.arend.core.definition.Definition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.module.serialization.ModuleDeserialization;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.reference.InternalReferable;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.impl.ArendLibraryImpl;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.source.StreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.arend.repl.Repl.REPL_NAME;

public class CliServerRequester implements ArendServerRequester {
  private final LibraryManager myLibraryManager;
  private boolean myRecompile = false;
  private final Set<ModuleLocation> myBinaryCacheLoaded = new HashSet<>();

  public CliServerRequester(LibraryManager libraryManager) {
    myLibraryManager = libraryManager;
  }

  public LibraryManager getLibraryManager() {
    return myLibraryManager;
  }

  /**
   * Returns the set of modules that were successfully loaded from binary cache.
   * These modules do not need to be persisted again.
   */
  public Set<ModuleLocation> getBinaryCacheLoaded() {
    return Collections.unmodifiableSet(myBinaryCacheLoaded);
  }

  @Override
  public void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module) {
    if (module.getLocationKind() == ModuleLocation.LocationKind.GENERATED) return;
    SourceLibrary library = myLibraryManager.getLibrary(module.getLibraryName());
    if (library == null) return;

    boolean inTests = module.getLocationKind() == ModuleLocation.LocationKind.TEST;
    Source rawSource = library.getSource(module.getModulePath(), inTests);
    if (rawSource == null) return;
    rawSource.load(server, myLibraryManager.getErrorReporter());
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
   * @param library  the library whose modules should be loaded from binary.
   * @param server   the server containing the raw-loaded modules.
   */
  public void loadBinaryCache(@NotNull SourceLibrary library, @NotNull ArendServer server) {
    if (myRecompile) return;

    ErrorReporter errorReporter = myLibraryManager.getErrorReporter();
    List<PendingBinaryLoad> pending = new ArrayList<>();
    ArendLibrary serverLib = server.getLibrary(library.getLibraryName());
    SerializableKeyRegistryImpl keyRegistry = serverLib instanceof ArendLibraryImpl impl ? impl.getKeyRegistry() : null;
    Map<ModuleLocation, Long> maxAncestorMtime = new HashMap<>();

    // Phase 1: parse protobuf files (does NOT touch any group referables)
    for (ModuleLocation module : server.getModules()) {
      if (!module.getLibraryName().equals(library.getLibraryName())) continue;
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) continue;

      PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
      if (binarySource == null) continue;
      long arcTimestamp = binarySource.getTimeStamp();
      if (arcTimestamp <= 0) continue;

      // Transitive timestamp-based cache invalidation: invalidate this .arc if any
      // .ard in its transitive import closure (including its own source) is newer.
      // Catches the case where an upstream refactor changes a class hierarchy without
      // touching this module's own .ard — the cached expressions still reference the
      // old hierarchy and would produce phantom errors.
      long maxAncestor = transitiveMaxArdMtime(module, library, server, maxAncestorMtime, new HashSet<>());
      if (maxAncestor > 0 && arcTimestamp < maxAncestor) {
        System.out.println("[INFO] Binary cache stale: " + module + " (transitive .ard newer than cache)");
        continue;
      }

      if (binarySource instanceof StreamBinarySource streamSource) {
        try {
          streamSource.setKeyRegistry(keyRegistry);
          ModuleDeserialization deser = streamSource.parseProtobuf(errorReporter);
          if (deser != null) {
            pending.add(new PendingBinaryLoad(module, deser));
          }
        } catch (Exception e) {
          // Skip this module — will be re-typechecked from source
        }
      }
    }

    // Phase 2a: fill in Definition shells on all groups (no cross-module scope needed)
    List<PendingBinaryLoad> phase2b = new ArrayList<>();
    for (PendingBinaryLoad load : pending) {
      ConcreteGroup group = server.getRawGroup(load.module);
      if (group == null) continue;
      try {
        load.deserialization.readDefinitions(group);
        phase2b.add(load);
      } catch (Exception e) {
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
      ConcreteGroup group = server.getRawGroup(load.module);
      try {
        load.deserialization.readModule(
            server.getModuleScopeProvider(load.module.getLibraryName(), false),
            new org.arend.typechecking.order.dependency.DependencyCollector(null));
        // Erroneous definitions are not persisted into ARC, so a deserialized module
        // may contain TCDefReferables without a typechecked counterpart (or with a
        // stale HAS_ERRORS status from older binaries). Detect this and force a
        // full re-typecheck of the module from source — otherwise diagnostics
        // disappear on subsequent runs and a previously-broken file looks clean.
        if (group != null && hasMissingTypechecked(group)) {
          clearTypechecked(group);
          incomplete++;
        } else {
          loaded++;
          myBinaryCacheLoaded.add(load.module);
          loadedLoads.add(load);
        }
      } catch (Exception e) {
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
          + " out of " + pending.size() + " candidates");
    }
  }

  /**
   * Returns the maximum {@code .ard} timestamp across {@code module}'s own source
   * and the transitive closure of its {@code \import}s within the same library.
   * Used to invalidate this module's {@code .arc} when any upstream source has been
   * edited since the cache was written (an upstream refactor of a class hierarchy
   * can leave the local {@code .ard} untouched yet make the cached expressions
   * reference structure that no longer matches the freshly retypechecked upstream).
   *
   * <p>Cycle-guarded: circular imports (e.g. {@code Algebra.StrictlyOrdered} ↔
   * {@code Arith.Nat}) are detected via {@code visiting} and short-circuited.
   * Cross-library imports and modules without a discoverable raw group are skipped
   * — they contribute their own source timestamp (if any) but no further walk.
   */
  private static long transitiveMaxArdMtime(
      ModuleLocation module, SourceLibrary library, ArendServer server,
      Map<ModuleLocation, Long> memo, Set<ModuleLocation> visiting) {
    Long cached = memo.get(module);
    if (cached != null) return cached;
    if (!visiting.add(module)) return 0L;
    long max = 0L;
    boolean inTests = module.getLocationKind() == ModuleLocation.LocationKind.TEST;
    Source src = library.getLibraryName().equals(module.getLibraryName())
        ? library.getSource(module.getModulePath(), inTests) : null;
    if (src != null) {
      long ts = src.getTimeStamp();
      if (ts > 0) max = ts;
    }
    ConcreteGroup group = server.getRawGroup(module);
    if (group != null) {
      for (ConcreteStatement statement : group.statements()) {
        ConcreteNamespaceCommand cmd = statement.command();
        if (cmd == null || !cmd.isImport()) continue;
        ModulePath depPath = new ModulePath(cmd.module().getPath());
        ModuleLocation dep = server.findModule(depPath, module.getLibraryName(), inTests, false);
        if (dep == null) continue;
        max = Math.max(max, transitiveMaxArdMtime(dep, library, server, memo, visiting));
      }
    }
    visiting.remove(module);
    memo.put(module, max);
    return max;
  }

  /**
   * Returns true if the group contains a typecheckable definition whose typechecked
   * counterpart is missing (null) or marked HAS_ERRORS. Inline metas and other
   * non-typecheckable kinds (FIELD, CONSTRUCTOR, LEVEL, META, OTHER) are skipped —
   * they legitimately have no typechecked counterpart in the cache.
   */
  private static boolean hasMissingTypechecked(ConcreteGroup group) {
    LocatedReferable ref = group.referable();
    if (ref instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
      Definition def = tcRef.getTypechecked();
      if (def == null || def.status() == Definition.TypeCheckingStatus.HAS_ERRORS) {
        return true;
      }
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && hasMissingTypechecked(statement.group())) return true;
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      if (hasMissingTypechecked(dynGroup)) return true;
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

  private record PendingBinaryLoad(ModuleLocation module, ModuleDeserialization deserialization) {}

  @Override
  public @Nullable List<String> getFiles(@NotNull String libraryName, boolean inTests, @NotNull List<String> prefix) {
    List<String> libraries = new ArrayList<>();
    if (libraryName.equals(REPL_NAME)) {
      libraries.addAll(getLibraryManager().getLibraries().stream().filter(library -> !library.equals(REPL_NAME)).toList());
    } else {
      libraries.add(libraryName);
    }
    List<String> result = new ArrayList<>();
    for (String libName : libraries) {
      SourceLibrary sourceLibrary = myLibraryManager.getLibrary(libName);
      if (sourceLibrary instanceof FileSourceLibrary fileSourceLibrary) {
        Path dir = fileSourceLibrary.sourceBasePath;
        if (inTests) {
          dir = fileSourceLibrary.testBasePath;
        }
        if (dir == null) {
          continue;
        }
        boolean isBreak = false;
        for (String name : prefix) {
          dir = dir.resolve(name);
          if (!dir.toFile().exists()) {
            isBreak = true;
            break;
          }
        }
        if (isBreak) {
          continue;
        }
        File[] files = dir.toFile().listFiles();
        if (files != null) {
          for (File file : files) {
            if (file.isDirectory()) {
              result.add(file.getName());
            } else if (file.getName().endsWith(FileUtils.EXTENSION)) {
              String fileName = file.getName();
              result.add(fileName.substring(0, fileName.lastIndexOf(FileUtils.EXTENSION)));
            }
          }
        }
      }
    }
    return result;
  }
}
