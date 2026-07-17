package org.arend.frontend.library;

import org.arend.ext.module.ModuleLocation;
import org.arend.server.ArendServer;
import org.arend.server.BinaryCacheLoader;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.source.StreamBinarySource;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class BinaryLoader {
  private final LibraryManager myLibraryManager;
  private boolean myRecompile = false;
  private BinaryCacheLoader myDelegate;

  public BinaryLoader(LibraryManager myLibraryManager) {
    this.myLibraryManager = myLibraryManager;
  }

  /**
   * Returns the set of modules that were successfully loaded from binary cache.
   * These modules do not need to be persisted again.
   */
  public Set<ModuleLocation> getBinaryCacheLoaded() {
    return myDelegate == null ? Collections.emptySet() : myDelegate.getBinaryCacheLoaded();
  }

  public void setRecompile(boolean recompile) {
    myRecompile = recompile;
  }

  /**
   * Loads typechecked definitions from binary caches for the given library.
   *
   * @param library  the library whose modules should be loaded from binary.
   * @param server   the server containing the raw-loaded modules.
   */
  public void loadBinaryCache(@NotNull SourceLibrary library, @NotNull ArendServer server) {
    if (myRecompile) return;

    if (myDelegate == null || myDelegate.getServer() != server) {
      myDelegate = new BinaryCacheLoader(server, myLibraryManager.getErrorReporter(), message -> System.out.println("[INFO] " + message));
    }

    myDelegate.loadBinaryCache(library.getLibraryName(), module -> {
      PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
      return binarySource instanceof StreamBinarySource streamSource ? streamSource : null;
    }, module -> {
      Source rawSource = library.getSource(module.getModulePath(), false);
      return rawSource == null ? 0 : rawSource.getTimeStamp();
    });
  }
}
