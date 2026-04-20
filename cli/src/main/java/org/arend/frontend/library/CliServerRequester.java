package org.arend.frontend.library;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.module.serialization.ModuleDeserialization;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.reference.InternalReferable;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
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

    // Phase 1: parse protobuf files (does NOT touch any group referables)
    for (ModuleLocation module : server.getModules()) {
      if (!module.getLibraryName().equals(library.getLibraryName())) continue;
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) continue;

      PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
      if (binarySource == null) continue;
      if (binarySource.getTimeStamp() <= 0) continue;

      // Timestamp-based cache invalidation
      Source rawSource = library.getSource(module.getModulePath(), false);
      if (rawSource != null) {
        long rawTimestamp = rawSource.getTimeStamp();
        if (rawTimestamp > 0 && binarySource.getTimeStamp() < rawTimestamp) {
          continue;
        }
      }

      if (binarySource instanceof StreamBinarySource streamSource) {
        try {
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
    for (PendingBinaryLoad load : phase2b) {
      try {
        load.deserialization.readModule(
            server.getModuleScopeProvider(load.module.getLibraryName(), false),
            new org.arend.typechecking.order.dependency.DependencyCollector(null));
        loaded++;
        myBinaryCacheLoaded.add(load.module);
      } catch (Exception e) {
        failed++;
        ConcreteGroup group = server.getRawGroup(load.module);
        if (group != null) {
          clearTypechecked(group);
        }
      }
    }
    if (loaded > 0 || failed > 0) {
      System.out.println("[INFO] Binary cache: " + loaded + " loaded, " + failed + " failed out of " + pending.size() + " candidates");
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
