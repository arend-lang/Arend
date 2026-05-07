package org.arend.frontend.symbol;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.Scope.ScopeContext;
import org.arend.server.ArendServer;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Implements `-sc` referable-scope dump, intended for debugging
 * reference-resolution issues.
 *
 * Pipeline:
 *   1. Resolve a {@code MODULE_PATH:GROUP_PATH} (or bare-name via the symbol
 *      index) into a {@link LocatedReferable}.
 *   2. Call {@link ArendServer#getReferableScope(LocatedReferable)} for the
 *      ambient scope at that referable's position.
 *   3. List entries (optionally filtered by a {@link SymbolPattern}), one per
 *      line, as {@code SHORT_NAME -> LIB::MODULE:LONG_NAME}.
 */
public final class ReferableScope {

  // ---- options + arg parsing ---------------------------------------------

  public enum Ctx { STATIC, DYNAMIC, ALL }

  public static final class Options {
    public Ctx context = Ctx.STATIC;
  }

  public record Parsed(@NotNull String spec, @Nullable SymbolPattern pattern, @NotNull Options options) {}

  /**
   * The first non-option positional arg is the referable spec; the second is
   * an optional name-filter pattern (same grammar as {@code -ss}).
   */
  public static @Nullable Parsed parseArgs(String[] args) {
    Options opts = new Options();
    String spec = null;
    String patternSrc = null;
    for (String arg : args) {
      switch (arg) {
        case "context=static" -> opts.context = Ctx.STATIC;
        case "context=dynamic" -> opts.context = Ctx.DYNAMIC;
        case "context=all" -> opts.context = Ctx.ALL;
        default -> {
          if (arg.startsWith("context=")) {
            System.err.println("[ERROR] Unknown -sc context: " + arg);
            return null;
          } else if (spec == null) {
            spec = arg;
          } else if (patternSrc == null) {
            patternSrc = arg;
          } else {
            System.err.println("[ERROR] -sc accepts at most a spec and a pattern: extra '" + arg + "'");
            return null;
          }
        }
      }
    }
    if (spec == null) {
      System.err.println("[ERROR] -sc requires a <MODULE_PATH>:<GROUP_PATH> (or bare-name) spec");
      return null;
    }
    SymbolPattern pattern = null;
    if (patternSrc != null) {
      try {
        pattern = SymbolPattern.compile(patternSrc, false);
      } catch (IllegalArgumentException e) {
        System.err.println("[ERROR] Bad -sc pattern: " + e.getMessage());
        return null;
      }
    }
    return new Parsed(spec, pattern, opts);
  }

  // ---- top-level driver --------------------------------------------------

  public static int run(@NotNull String spec,
                        @Nullable SymbolPattern pattern,
                        @NotNull Options options,
                        @NotNull List<SourceLibrary> requestedLibraries,
                        @NotNull LibraryManager libraryManager,
                        @NotNull ArendServer server,
                        @NotNull ErrorReporter errorReporter) {
    List<SourceLibrary> libsInScope = librariesInScope(libraryManager);
    if (libsInScope.isEmpty()) {
      System.err.println("[ERROR] No libraries in scope.");
      return 0;
    }

    // Refresh symbol indexes so bare-name lookup works AND every source module
    // is registered on the server (the server needs the raw group of the
    // target's module for getReferableScope to walk into).
    Map<SourceLibrary, SymbolIndex> indexes = new LinkedHashMap<>();
    for (SourceLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      for (ModulePath mp : lib.findModules(false)) {
        if (idx.isStale(lib, mp)) {
          server.findModule(mp, lib.getLibraryName(), false, false);
        }
      }
      idx.refresh(lib, server, false);
      idx.save();
      indexes.put(lib, idx);
    }

    Resolved target = resolveTarget(spec, server, libsInScope, indexes);
    if (target == null) return 0;

    Scope scope = server.getReferableScope(target.referable);
    if (scope == null) {
      System.err.println("[ERROR] No scope available at " + label(target));
      return 0;
    }

    System.out.println("--- Scope at " + label(target) + " ---");
    int total = 0;
    int matched = 0;
    if (options.context == Ctx.ALL) {
      // Print each context section in turn.
      matched += dumpSection(scope, ScopeContext.STATIC,  "STATIC",  pattern);
      matched += dumpSection(scope, ScopeContext.DYNAMIC, "DYNAMIC", pattern);
      matched += dumpSection(scope, ScopeContext.PLEVEL,  "PLEVEL",  pattern);
      matched += dumpSection(scope, ScopeContext.HLEVEL,  "HLEVEL",  pattern);
      total = scope.getElements(null).size();
    } else {
      ScopeContext ctx = options.context == Ctx.DYNAMIC ? ScopeContext.DYNAMIC : ScopeContext.STATIC;
      Collection<? extends Referable> elements = scope.getElements(ctx);
      total = elements.size();
      matched = printEntries(elements, pattern);
    }

    if (pattern != null) {
      System.out.println("--- " + matched + " match(es) of " + total + " entries (pattern: " + pattern.source() + ") ---");
    } else {
      System.out.println("--- " + total + " entries ---");
    }
    return 0;
  }

  private static int dumpSection(Scope scope, ScopeContext ctx, String header, @Nullable SymbolPattern pattern) {
    Collection<? extends Referable> elements = scope.getElements(ctx);
    if (elements.isEmpty()) return 0;
    System.out.println();
    System.out.println("[" + header + "]");
    return printEntries(elements, pattern);
  }

  private static int printEntries(Collection<? extends Referable> elements, @Nullable SymbolPattern pattern) {
    int matched = 0;
    for (Referable ref : elements) {
      String name = ref.textRepresentation();
      if (pattern != null && !pattern.matches(name)) continue;
      System.out.println(name + " -> " + targetLabel(ref));
      matched++;
    }
    return matched;
  }

  private static String targetLabel(Referable ref) {
    if (ref instanceof LocatedReferable lr) {
      ModuleLocation loc = lr.getLocation();
      LongName ln = lr.getRefLongName();
      String mod = loc == null ? "?" : loc.getLibraryName() + "::" + loc.getModulePath();
      return mod + ":" + ln + " [" + lr.getKind() + "]";
    }
    return "(local " + ref.getClass().getSimpleName() + ")";
  }

  // ---- target resolution -------------------------------------------------

  private record Resolved(LocatedReferable referable, ModuleLocation module, String libraryName) {}

  private static String label(Resolved r) {
    return r.libraryName + "::" + r.module.getModulePath() + ":" + r.referable.getRefLongName();
  }

  private static @Nullable Resolved resolveTarget(String spec, ArendServer server,
      List<SourceLibrary> libsInScope, Map<SourceLibrary, SymbolIndex> indexes) {
    int colon = spec.indexOf(':');
    if (colon >= 0) {
      String modStr = spec.substring(0, colon);
      String defStr = spec.substring(colon + 1);
      if (modStr.isEmpty() || defStr.isEmpty()) {
        System.err.println("[ERROR] empty module or definition path in -sc spec '" + spec + "'");
        return null;
      }
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
      ModuleLocation found = server.findModule(mp, null, true, true);
      if (found == null) {
        System.err.println("[ERROR] Module not found: " + modStr);
        return null;
      }
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
      return new Resolved(ref, found, found.getLibraryName());
    }

    // Bare-name lookup via the symbol index, accepting any kind.
    List<SymbolIndex.Entry> matches = new ArrayList<>();
    Map<SymbolIndex.Entry, SourceLibrary> libOf = new HashMap<>();
    for (Map.Entry<SourceLibrary, SymbolIndex> e : indexes.entrySet()) {
      for (SymbolIndex.Entry entry : e.getValue().allEntries()) {
        if (entry.shortName().equals(spec)) {
          matches.add(entry);
          libOf.put(entry, e.getKey());
        }
      }
    }
    if (matches.isEmpty()) {
      System.err.println("[ERROR] No definition named '" + spec + "' in scope. Use -ss to find candidates.");
      return null;
    }
    if (matches.size() > 1) {
      System.err.println("[ERROR] '" + spec + "' is ambiguous. Use one of:");
      List<String> labels = new ArrayList<>();
      for (SymbolIndex.Entry e : matches) {
        labels.add("  " + libOf.get(e).getLibraryName() + "::" + e.modulePath() + ":" + e.longName());
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
    System.out.println("[INFO] Resolved '" + spec + "' -> " + lib.getLibraryName() + "::"
        + mp + ":" + only.longName());
    return new Resolved(ref, moduleLoc, lib.getLibraryName());
  }

  private static @Nullable LocatedReferable walkLongName(ConcreteGroup group, LongName ln) {
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

  private static @Nullable ConcreteGroup findChildGroup(ConcreteGroup group, String name) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null && sub.referable() != null
          && name.equals(sub.referable().textRepresentation())) {
        return sub;
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      if (dyn.referable() != null && name.equals(dyn.referable().textRepresentation())) {
        return dyn;
      }
    }
    return null;
  }

  private static @Nullable LocatedReferable findInternalReferable(ConcreteGroup group, String name) {
    for (LocatedReferable r : group.getInternalReferables()) {
      if (name.equals(r.textRepresentation())) return r;
    }
    return null;
  }

  private static List<SourceLibrary> librariesInScope(LibraryManager manager) {
    List<SourceLibrary> all = new ArrayList<>();
    for (String name : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(name);
      if (lib != null) all.add(lib);
    }
    return all;
  }
}
