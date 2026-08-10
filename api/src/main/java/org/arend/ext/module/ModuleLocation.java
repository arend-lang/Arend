package org.arend.ext.module;

import java.util.Objects;

public class ModuleLocation {
  public enum LocationKind { SOURCE, TEST, GENERATED }

  private final String myLibraryName;
  private final LocationKind myLocationKind;
  private final ModulePath myModulePath;

  public ModuleLocation(String libraryName, LocationKind locationKind, ModulePath modulePath) {
    myLibraryName = libraryName;
    myLocationKind = locationKind;
    myModulePath = modulePath;
  }

  public String getLibraryName() {
    return myLibraryName;
  }

  public LocationKind getLocationKind() {
    return myLocationKind;
  }

  public ModulePath getModulePath() {
    return myModulePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ModuleLocation that = (ModuleLocation) o;
    return myLibraryName.equals(that.myLibraryName) &&
      myLocationKind == that.myLocationKind &&
      myModulePath.equals(that.myModulePath);
  }

  /**
   * Uses the kind's {@code ordinal()} rather than the constant itself, because {@code
   * Enum.hashCode()} is the identity hash: it differs between JVM runs, and within one run it
   * depends on how many objects were hashed on this thread before the constant was. Modules are
   * kept in hash-ordered collections that get iterated (notably {@code ArendServer.getModules()},
   * a {@code ConcurrentHashMap}), so an unstable hash makes that iteration order unstable too —
   * which is how the same command over the same files could take a different code path depending
   * on whether an unrelated startup probe ran first.
   */
  @Override
  public int hashCode() {
    return Objects.hash(myLibraryName, myLocationKind.ordinal(), myModulePath);
  }

  @Override
  public String toString() {
    return myModulePath.toString();
  }
}
