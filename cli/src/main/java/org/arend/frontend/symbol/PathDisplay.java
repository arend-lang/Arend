package org.arend.frontend.symbol;

import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Shortens absolute source-file paths in user-facing output to library-relative form.
 *
 * <p>{@code /Users/sergey/Documents/Arend/arend-lib/src/Logic/Foo.ard} becomes
 * {@code arend-lib/src/Logic/Foo.ard} — the prefix from the filesystem root down to
 * (but not including) the library's directory name is dropped. Paths that don't fall
 * under any known library's base directory are returned unchanged so callers can't
 * accidentally lose information.
 *
 * <p>Why this lives in one place: the same shortening is needed by the
 * constructor-import advisory, name-resolve suggestion blocks, {@code -ps}, {@code -fu},
 * {@code -ss}, and {@code -ch} output formatters. Keeping the logic centralized avoids
 * each emitter re-implementing (and inevitably diverging on) the same rule.
 */
public final class PathDisplay {
  private PathDisplay() {}

  /**
   * Render {@code path} relative to the parent of its containing library's base
   * directory, or as an absolute path string if no known library matches.
   * Returns the empty string when {@code path} is {@code null}.
   */
  public static @NotNull String shorten(@Nullable Path path, @Nullable LibraryManager manager) {
    if (path == null) return "";
    return shorten(path.toString(), manager);
  }

  /**
   * String-input variant. The input is treated as a filesystem path; matching against
   * each library's base directory uses string-prefix semantics so paths whose
   * normalization may differ (symlinks, {@code ./} prefixes already stripped) still
   * shorten as long as their textual form starts with the base directory.
   */
  public static @NotNull String shorten(@Nullable String pathString, @Nullable LibraryManager manager) {
    if (pathString == null || pathString.isEmpty()) return "";
    if (manager == null) return pathString;
    for (String libName : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(libName);
      if (!(lib instanceof FileSourceLibrary fl)) continue;
      Path base = fl.getBasePath();
      if (base == null) continue;
      String baseStr = base.toAbsolutePath().normalize().toString();
      if (pathString.startsWith(baseStr)) {
        String tail = pathString.substring(baseStr.length());
        // The base directory must match at a path boundary: otherwise a library
        // whose directory name is a string-prefix of another's (e.g. "PartI" vs
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
}
