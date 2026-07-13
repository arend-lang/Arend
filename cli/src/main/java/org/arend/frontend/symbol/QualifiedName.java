package org.arend.frontend.symbol;

import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.SourceLibrary;
import org.arend.prelude.Prelude;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Single source of truth for the console tools' qualified-name convention,
 * {@code [LIBRARY::]MODULE_PATH:LONG_NAME}, shared by {@code -ss}, {@code -ps},
 * {@code -fu}, {@code -sc} and {@code -ch} for both DISPLAY and PARSING.
 *
 * <p>The {@code LIBRARY::} prefix only disambiguates when several libraries are
 * loaded; with a single (non-prelude) library it is redundant — the module path
 * already locates the definition — so it is omitted. This matches the tools'
 * {@code --json} output, whose {@code library} field is likewise dropped in
 * single-library scope while {@code module} is always present. Keeping the rule
 * here means every emitter and every spec-parser stays in lock-step.
 */
public final class QualifiedName {
  private QualifiedName() {}

  /**
   * True when more than one non-prelude library is in scope, so labels need the
   * {@code LIBRARY::} prefix to be unambiguous. Prelude is never counted: it is
   * always present and never the thing being disambiguated.
   */
  public static boolean showLibrary(@NotNull Collection<? extends SourceLibrary> libsInScope) {
    int n = 0;
    for (SourceLibrary lib : libsInScope) {
      if (!Prelude.LIBRARY_NAME.equals(lib.getLibraryName()) && ++n > 1) return true;
    }
    return false;
  }

  /**
   * Formats {@code [library::]module:longName}. The {@code library::} prefix is
   * emitted only when {@code showLibrary} (and the name is non-empty); an empty
   * module path (generated/meta entries) drops the {@code module:} segment rather
   * than print a leading colon. The long name is taken verbatim, so callers may
   * pass an already-highlighted rendering.
   */
  public static @NotNull String format(boolean showLibrary, @Nullable String library,
                                       @Nullable String modulePath, @NotNull String longName) {
    StringBuilder sb = new StringBuilder();
    if (showLibrary && library != null && !library.isEmpty()) sb.append(library).append("::");
    if (modulePath != null && !modulePath.isEmpty()) sb.append(modulePath).append(':');
    sb.append(longName);
    return sb.toString();
  }

  /** Result of {@link #splitLibrary}: an optional library name and the remaining {@code module:def} spec. */
  public record Split(@Nullable String library, @NotNull String rest) {}

  /**
   * Strips an optional leading {@code library::} prefix from a user-typed spec,
   * so a label copied from any tool's output can be pasted straight back in.
   * Only strips when the remainder is a well-formed {@code MODULE_PATH:LONG_NAME}
   * — this leaves a definition whose name itself contains {@code ::} (the List
   * cons operator, spec {@code Data.List:::}) for the caller's plain parser.
   * Returns {@code {null, spec}} when no library prefix is present.
   */
  public static @NotNull Split splitLibrary(@NotNull String spec) {
    int sep = spec.indexOf("::");
    if (sep > 0) {
      String rest = spec.substring(sep + 2);
      int colon = rest.indexOf(':');
      if (colon > 0 && colon < rest.length() - 1
          && FileUtils.isCorrectModulePath(ModulePath.fromString(rest.substring(0, colon)))
          && FileUtils.isCorrectDefinitionName(LongName.fromString(rest.substring(colon + 1)))) {
        return new Split(spec.substring(0, sep), rest);
      }
    }
    return new Split(null, spec);
  }

  /**
   * When a bare {@code module:def} spec (no {@code library::} prefix) names a
   * module path that physically exists in more than one in-scope library,
   * {@code ArendServer.findModule(mp, null, …)} silently returns the first — so
   * the user may be inspecting a different definition than they meant. Emit a
   * {@code [WARN]} naming the owners and the one picked, and point at the
   * {@code library::} prefix as the fix. No-op when the user already scoped with
   * a prefix ({@code hadLibraryPrefix}) or the module is unique. Shared by
   * {@code -fu}, {@code -sc} and {@code -ch}, which all resolve a single target
   * this way ({@code -ss}/{@code -ps} enumerate every match, so they never
   * silently drop one).
   */
  public static void warnAmbiguousModule(boolean hadLibraryPrefix, @NotNull ModulePath modulePath,
                                         @NotNull String chosenLibrary,
                                         @NotNull Collection<? extends SourceLibrary> libsInScope) {
    if (hadLibraryPrefix) return;
    List<String> owners = new ArrayList<>();
    for (SourceLibrary lib : libsInScope) {
      if (lib.findModules(false).contains(modulePath) || lib.findModules(true).contains(modulePath)) {
        owners.add(lib.getLibraryName());
      }
    }
    if (owners.size() <= 1) return;
    Collections.sort(owners);
    System.err.println("[WARN] module '" + modulePath + "' is defined in " + owners
        + "; using '" + chosenLibrary + "'. Prefix the spec with '<library>::' to choose another.");
  }
}
