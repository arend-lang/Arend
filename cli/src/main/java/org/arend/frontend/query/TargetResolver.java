package org.arend.frontend.query;

import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.server.ArendServer;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared {@code [library::]module:def} / bare-name spec resolution for the console
 * tools that resolve a single target ({@code -fu}, {@code -ch}, {@code -sc}). Given a
 * spec it locates the enclosing module (honoring an optional {@code library::}
 * prefix and warning on ambiguous module paths), then walks the long name into the
 * raw group; a bare name (optionally dotted) is looked up through the per-library
 * symbol indexes by trailing short name and long-name suffix.
 *
 * <p>Diagnostics go to {@code System.err} (and an {@code [INFO]} resolution line to
 * {@code System.out} for the bare-name path), matching the tools' other output.
 */
public final class TargetResolver {
  private TargetResolver() {}

  /** A resolved target: the referable, the module it lives in, and that module's library. */
  public record Target(LocatedReferable referable, ModuleLocation module, String library) {}

  /** A validated {@code (module path, definition long name)} pair from a {@code MODULE:DEF} spec. */
  record ModuleDef(ModulePath modulePath, LongName definition) {}

  /**
   * Parses and validates the two halves of a {@code MODULE:DEF} spec into a
   * {@link ModuleDef}, printing the specific {@code [ERROR]} and returning
   * {@code null} when either the module path or the definition name is malformed.
   * Callers are expected to have split on {@code :} and rejected empty halves already.
   */
  static @Nullable ModuleDef parseModuleDef(String modStr, String defStr) {
    ModulePath mp = ModulePath.fromString(modStr);
    if (!FileUtils.isCorrectModulePath(mp)) {
      System.err.println("[ERROR] invalid module path '" + modStr + "'");
      return null;
    }
    LongName ln = LongName.fromString(defStr);
    if (!FileUtils.isCorrectDefinitionName(ln)) {
      System.err.println("[ERROR] invalid definition name '" + defStr + "'");
      return null;
    }
    return new ModuleDef(mp, ln);
  }

  /**
   * Resolves {@code spec} to a single target, or returns {@code null} after printing a
   * diagnostic. {@code toolTag} (e.g. {@code "-ch"}) appears in error text.
   * {@code kindFilter} restricts bare-name candidates to the given kinds ({@code null}
   * = any kind); {@code kindNoun} (e.g. {@code "class or record"}) names them in the
   * not-found message.
   */
  public static @Nullable Target resolve(String spec, String toolTag, ArendServer server,
                                  List<SourceLibrary> libsInScope, Map<SourceLibrary, SymbolIndex> indexes,
                                  boolean showLibrary, @Nullable Set<SymbolIndex.Kind> kindFilter, String kindNoun) {
    // Accept an optional `library::` prefix (see QualifiedName.splitLibrary); the
    // library scopes module resolution below. Only a real in-scope library name is
    // peeled, so a `::`-containing bare name (e.g. `::`) falls through to the index lookup.
    QualifiedName split = QualifiedName.splitLibrary(spec, QualifiedName.libraryNames(libsInScope));
    String fromLibrary = split.library();
    if (split.module() != null) {
      String modStr = split.module();
      String defStr = split.longName();
      if (modStr.isEmpty() || defStr.isEmpty()) {
        System.err.println("[ERROR] empty module or definition path in " + toolTag + " spec '" + spec + "'");
        return null;
      }
      ModuleDef md = parseModuleDef(modStr, defStr);
      if (md == null) return null;
      ModulePath mp = md.modulePath();
      LongName ln = md.definition();
      ModuleLocation found = server.findModule(mp, fromLibrary, true, true);
      if (found == null) {
        System.err.println("[ERROR] Module not found: " + modStr);
        return null;
      }
      if (fromLibrary == null) QualifiedName.warnAmbiguousModule(mp, found.getLibraryName(), libsInScope);
      ConcreteGroup group = server.getRawGroup(found);
      if (group == null) {
        System.err.println("[ERROR] Module not loaded: " + modStr);
        return null;
      }
      LocatedReferable ref = walkLongName(group, ln);
      if (ref == null) {
        System.err.println("[ERROR] Definition not found: " + spec);
        return null;
      }
      return new Target(ref, found, found.getLibraryName());
    }

    // Bare-name lookup via the symbol index. The parsed name (a peeled `LIB::`
    // prefix, if any, is not part of it) is treated as a long-name suffix: match
    // entries by trailing short name, then -- for a dotted bare name -- keep only
    // those whose full long-name ends with the user's segments.
    String bareName = split.longName();
    LongName userLongName = LongName.fromString(bareName);
    if (!FileUtils.isCorrectDefinitionName(userLongName)) {
      System.err.println("[ERROR] invalid definition name '" + bareName + "'");
      return null;
    }
    List<String> userSegments = userLongName.toList();
    String shortName = userSegments.getLast();
    List<SymbolIndex.Entry> matches = new ArrayList<>();
    Map<SymbolIndex.Entry, SourceLibrary> libOf = new HashMap<>();
    for (Map.Entry<SourceLibrary, SymbolIndex> e : indexes.entrySet()) {
      for (SymbolIndex.Entry entry : e.getValue().allEntries()) {
        if (kindFilter != null && !kindFilter.contains(entry.kind())) continue;
        if (!entry.shortName().equals(shortName)) continue;
        if (userSegments.size() > 1
            && !endsWith(LongName.fromString(entry.longName()).toList(), userSegments)) continue;
        matches.add(entry);
        libOf.put(entry, e.getKey());
      }
    }
    if (matches.isEmpty()) {
      System.err.println("[ERROR] No " + kindNoun + " named '" + spec + "' in scope. Use -ss to find candidates.");
      return null;
    }
    if (matches.size() > 1) {
      System.err.println("[ERROR] '" + spec + "' is ambiguous. Use one of:");
      List<String> labels = new ArrayList<>();
      for (SymbolIndex.Entry e : matches) {
        labels.add("  " + QualifiedName.format(showLibrary, libOf.get(e).getLibraryName(),
            e.modulePath().toString(), e.longName()));
      }
      Collections.sort(labels);
      for (String l : labels) System.err.println(l);
      return null;
    }
    SymbolIndex.Entry only = matches.getFirst();
    SourceLibrary lib = libOf.get(only);
    ModulePath mp = only.modulePath();
    ModuleLocation moduleLoc = server.findModule(mp, lib.getLibraryName(), true, true);
    if (moduleLoc == null) {
      System.err.println("[ERROR] Could not load module " + mp);
      return null;
    }
    LongName ln = LongName.fromString(only.longName());
    ConcreteGroup group = server.getRawGroup(moduleLoc);
    if (group == null) {
      System.err.println("[ERROR] Module not loaded: " + mp);
      return null;
    }
    LocatedReferable ref = walkLongName(group, ln);
    if (ref == null) {
      System.err.println("[ERROR] Definition not found via index: " + only.longName());
      return null;
    }
    System.out.println("[INFO] Resolved '" + spec + "' -> "
        + QualifiedName.format(showLibrary, lib.getLibraryName(), mp.toString(), only.longName()));
    return new Target(ref, moduleLoc, lib.getLibraryName());
  }

  /** True iff {@code tail} is a (non-empty) suffix of {@code full} (segment-wise). */
  private static boolean endsWith(List<String> full, List<String> tail) {
    if (tail.size() > full.size()) return false;
    int offset = full.size() - tail.size();
    for (int i = 0; i < tail.size(); i++) {
      if (!full.get(offset + i).equals(tail.get(i))) return false;
    }
    return true;
  }

  /**
   * Walks a dotted long name from {@code group} into the referable it names, descending
   * through subgroups and finally into constructors / fields (which must be the last
   * segment). Returns {@code null} if any segment doesn't resolve.
   */
  static @Nullable LocatedReferable walkLongName(ConcreteGroup group, LongName ln) {
    LocatedReferable result = group.referable();
    List<String> names = ln.toList();
    for (int i = 0; i < names.size(); i++) {
      String segment = names.get(i);
      ConcreteGroup next = findChildGroup(group, segment);
      if (next != null) {
        group = next;
        result = next.referable();
        continue;
      }
      LocatedReferable internal = findInternalReferable(group, segment);
      if (internal != null) {
        return i == names.size() - 1 ? internal : null;
      }
      return null;
    }
    return result;
  }

  /** The immediate static or dynamic subgroup of {@code group} named {@code name}, or {@code null}. */
  static @Nullable ConcreteGroup findChildGroup(ConcreteGroup group, String name) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null && name.equals(sub.referable().textRepresentation())) {
        return sub;
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      if (name.equals(dyn.referable().textRepresentation())) {
        return dyn;
      }
    }
    return null;
  }

  /** The internal referable (constructor / field) of {@code group} named {@code name}, or {@code null}. */
  static @Nullable LocatedReferable findInternalReferable(ConcreteGroup group, String name) {
    for (LocatedReferable r : group.getInternalReferables()) {
      if (name.equals(r.textRepresentation())) return r;
    }
    return null;
  }
}
