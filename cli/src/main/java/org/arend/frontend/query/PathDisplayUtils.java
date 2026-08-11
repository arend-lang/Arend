package org.arend.frontend.query;

import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Renders source-file paths in library-relative form ({@code arend-lib/src/Logic/Foo.ard}) for user-facing output. */
public final class PathDisplayUtils {
  private PathDisplayUtils() {}

  /** The library-relative form of {@code path}; the absolute string if no library matches, empty if {@code null}. */
  public static @NotNull String shorten(@Nullable Path path, @Nullable LibraryManager manager) {
    if (path == null) return "";
    return shorten(path.toString(), manager);
  }

  /** String-input variant of {@link #shorten(Path, LibraryManager)}, matched against library base dirs by string prefix. */
  public static @NotNull String shorten(@Nullable String pathString, @Nullable LibraryManager manager) {
    if (pathString == null || pathString.isEmpty()) return "";
    if (manager == null) return pathString;
    for (String libName : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(libName);
      if (!(lib instanceof FileSourceLibrary fl)) continue;
      Path base = libraryRoot(fl);
      if (base == null) continue;
      String baseStr = base.toAbsolutePath().normalize().toString();
      if (pathString.startsWith(baseStr)) {
        String tail = pathString.substring(baseStr.length());
        // The base directory must match at a path boundary: otherwise a library
        // whose directory name is a string-prefix of another's (e.g. "PartI" vs.
        // "PartII") would swallow the sibling, mangling ".../PartII/src/X" into
        // "PartI/I/src/X". Accept only an exact match or a following separator.
        if (!tail.isEmpty() && tail.charAt(0) != '/' && tail.charAt(0) != '\\') continue;
        // Strip any leading separator so the result is "libName/<tail>" not "libName//tail".
        if (!tail.isEmpty()) {
          tail = tail.substring(1);
        }
        return tail.isEmpty() ? fl.getLibraryName() : fl.getLibraryName() + "/" + tail;
      }
    }
    return pathString;
  }

  /** The library's root directory (the one holding {@code arend.yaml}), or {@code null} if it can't be determined. */
  private static @Nullable Path libraryRoot(@NotNull FileSourceLibrary fl) {
    Path src = fl.getSourceBasePath();
    if (src == null) return null;
    String libName = fl.getLibraryName();
    for (Path p = src.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
      Path name = p.getFileName();
      if (name != null && name.toString().equals(libName)) return p;
    }
    return null;
  }

  /** The absolute, normalized source-file path backing {@code moduleLoc} in {@code lib}, or {@code null} if unavailable. */
  public static @Nullable Path sourcePath(@Nullable SourceLibrary lib, @NotNull ModuleLocation moduleLoc) {
    if (!(lib instanceof FileSourceLibrary fl)) return null;
    Path base = fl.getSourceBasePath();
    if (base == null) return null;
    try {
      return FileUtils.sourceFile(base, moduleLoc.getModulePath()).toAbsolutePath().normalize();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** A display label for {@code moduleLoc}: its shortened source path, or the synthetic {@code <library:module>} form. Never empty. */
  public static @NotNull String label(@NotNull ModuleLocation moduleLoc, @NotNull LibraryManager manager) {
    Path src = sourcePath(manager.getLibrary(moduleLoc.getLibraryName()), moduleLoc);
    return src != null ? shorten(src, manager)
        : "<" + moduleLoc.getLibraryName() + ":" + moduleLoc.getModulePath() + ">";
  }

  /** The shortened library-relative path of a SOURCE {@code moduleLoc}, or {@code null} for non-source modules or when unresolved. */
  public static @Nullable String shortenedSourcePath(@NotNull ModuleLocation moduleLoc,
                                                     @NotNull LibraryManager manager) {
    if (moduleLoc.getLocationKind() != ModuleLocation.LocationKind.SOURCE) return null;
    Path abs = sourcePath(manager.getLibrary(moduleLoc.getLibraryName()), moduleLoc);
    return abs == null ? null : shorten(abs, manager);
  }
}
