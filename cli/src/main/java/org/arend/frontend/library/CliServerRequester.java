package org.arend.frontend.library;

import org.arend.core.definition.Definition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.module.error.BinaryCacheError;
import org.arend.module.serialization.ModuleDeserialization;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.reference.InternalReferable;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.impl.ArendLibraryImpl;
import org.arend.server.impl.ArendServerImpl;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.source.StreamBinarySource;
import org.arend.term.group.ConcreteGroup;
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
  /** Modules seen in an earlier pass; only first sight may load from {@code .arc}. */
  private final Set<ModuleLocation> mySeenModules = new HashSet<>();

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
          myBinaryCacheLoaded.remove(module);
          continue;
        }
      }

      {
        ConcreteGroup memGroup = server.getRawGroup(module);
        if (memGroup != null && hasTypechecked(memGroup)) {
          myBinaryCacheLoaded.add(module);
          continue;
        }
        if (memGroup != null && !firstSight && hasTypecheckableDefinitions(memGroup)) {
          myBinaryCacheLoaded.remove(module);
          continue;
        }
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
          // Parse failure: the .arc file exists but is unreadable. Drop any
          // pre-existing in-memory state for this module for the same reason as
          // the stale-mtime branch above — without this, a previously-loaded
          // module that suddenly fails to parse on refresh would silently keep
          // its old typechecked state.
          ConcreteGroup group = server.getRawGroup(module);
          if (group != null) clearTypechecked(group);
          myBinaryCacheLoaded.remove(module);
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
    for (PendingBinaryLoad load : phase2b) {
      ConcreteGroup group = server.getRawGroup(load.module);
      try {
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
      String line = "[INFO] Binary cache: " + loaded + " loaded"
          + (incomplete > 0 ? ", " + incomplete + " incomplete" : "")
          + (failed > 0 ? ", " + failed + " failed" : "")
          + " out of " + pending.size() + " candidates";
      System.out.println(line);
    }
  }

  /**
   * Returns true if any typecheckable definition in the group has an expression
   * (parameter type, result type, body) that references another {@link Definition}
   * still stuck in {@link Definition.TypeCheckingStatus#NEEDS_TYPE_CHECKING} — i.e.
   * an orphan shell left behind when its owning module's phase-2b failed partway
   * through fillInDefinition.
   */
  /** True if any definition reachable from {@code group} already has a typechecked core definition in memory. */
  private static boolean hasTypechecked(ConcreteGroup group) {
    boolean[] found = { false };
    walkDefinitions(group, def -> { found[0] = true; return true; });
    return found[0];
  }

  /**
   * True if anything reachable from {@code group} is typecheckable, whether it holds a core.
   * Separates a module that <em>lost</em> its definitions from one that never had any.
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
