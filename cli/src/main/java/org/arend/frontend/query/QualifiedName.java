package org.arend.frontend.query;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A parsed qualified name used in console tools,
 * {@code [LIBRARY::]MODULE_PATH:LONG_NAME}, used for both display and parsing.
 *
 * <p>The {@code LIBRARY::} prefix only disambiguates when several libraries are
 * loaded; with a single (non-prelude) library the module path already locates the
 * definition, so it is omitted — matching the {@code --json} output, whose
 * {@code library} field is likewise dropped in single-library scope while
 * {@code module} is always present.
 *
 * <p>Instances come from {@link #splitLibrary}: {@code library} is {@code null}
 * when the spec carried no recognized {@code ::} prefix, and {@code module} is
 * {@code null} for a bare name — one whose first colon does not split it into a valid
 * module path and definition name (in particular, any spec with no {@code :}).
 */
public record QualifiedName(@Nullable String library, @Nullable String module, @NotNull String longName) {

  public static boolean containsMultipleNonPreludeLibraries(@NotNull Collection<? extends SourceLibrary> libsInScope) {
    int n = 0;
    for (SourceLibrary lib : libsInScope) {
      if (!Prelude.LIBRARY_NAME.equals(lib.getLibraryName()) && ++n > 1) return true;
    }
    return false;
  }

  public static @NotNull String format(boolean showLibrary, @Nullable String library, @Nullable String modulePath, @NotNull String longName) {
    StringBuilder sb = new StringBuilder();
    if (showLibrary && library != null && !library.isEmpty()) sb.append(library).append("::");
    if (modulePath != null && !modulePath.isEmpty()) sb.append(modulePath).append(':');
    sb.append(longName);
    return sb.toString();
  }

  /** The set of library names in {@code libs}, for the {@code libraryNames} argument of
   *  {@link #splitLibrary} (which peels a {@code LIB::} prefix only for a real library). */
  public static @NotNull Set<String> libraryNames(@NotNull Collection<? extends SourceLibrary> libs) {
    Set<String> names = new HashSet<>();
    for (SourceLibrary lib : libs) names.add(lib.getLibraryName());
    return names;
  }

  /**
   * Parses a user-typed spec into its {@code [library::]module:longName} pieces,
   * so a label copied from any tool's output can be pasted straight back in.
   *
   * <p>A {@code library::} prefix is peeled only when the text before the first
   * {@code ::} exactly matches one of {@code libraryNames} (the libraries in scope), so
   * it never swallows a {@code ::} that is part of a definition name.
   *
   * <p>The remainder is read as {@code MODULE_PATH:LONG_NAME} only when its first colon
   * splits it into a valid module path and a valid definition name; otherwise the whole
   * remainder is a bare name ({@code module} is {@code null}) — which is what lets a
   * {@code :}-containing bare name (e.g. the List cons operator {@code ::}) reach the
   * bare-name lookup instead of being force-split into an empty module.
   */
  public static @NotNull QualifiedName splitLibrary(@NotNull String spec, @NotNull Set<String> libraryNames) {
    String rest = spec;
    String library = null;
    int sep = spec.indexOf("::");
    if (sep > 0 && libraryNames.contains(spec.substring(0, sep))) {
      library = spec.substring(0, sep);
      rest = spec.substring(sep + 2);
    }
    int colon = rest.indexOf(':');
    if (colon > 0 && colon < rest.length() - 1
        && FileUtils.isCorrectModulePath(ModulePath.fromString(rest.substring(0, colon)))
        && FileUtils.isCorrectDefinitionName(LongName.fromString(rest.substring(colon + 1)))) {
      return new QualifiedName(library, rest.substring(0, colon), rest.substring(colon + 1));
    }
    return new QualifiedName(library, null, rest);
  }

  /**
   * When a bare {@code module:def} spec (no {@code library::} prefix) names a
   * module path that physically exists in more than one in-scope library,
   * emits a {@code [WARN]} naming the owners and the one picked, and point at the
   * {@code library::} prefix as the fix. Callers skip this when the user already
   * scoped with a prefix; it is also a no-op when the module is unique. Shared by
   * {@code -fu}, {@code -sc} and {@code -ch}, which all resolve a single target
   * this way ({@code -ss}/{@code -ps} enumerate every match, so they never
   * silently drop one).
   */
  public static void warnAmbiguousModule(@NotNull ModulePath modulePath,
                                         @NotNull String chosenLibrary,
                                         @NotNull Collection<? extends SourceLibrary> libsInScope) {
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
