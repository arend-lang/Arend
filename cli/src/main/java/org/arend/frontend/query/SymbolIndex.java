package org.arend.frontend.query;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.MetaReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * On-disk + in-memory cache of (name, file:line, signature) triples per library.
 * Layout: per-library file at {@code <binariesDir>/.arend-symbol-index} (or
 * {@code <sourcesDir>/../.arend-symbol-index/<library>.idx} when no binariesDir
 * is configured). Each module has a stored timestamp; when the underlying .ard
 * file's mtime advances, that module's entries are dropped and rebuilt from
 * {@link ArendServer#getRawGroup}.
 */
public final class SymbolIndex {
  static final FileStamp GENERATED_STAMP = new FileStamp(-1L, -1L);

  /** mtime+size snapshot of a source file; staleness compares both. */
  public record FileStamp(long mtime, long size) {}

  public enum Kind { FUNCTION, SFUNC, LEMMA, TYPE, INSTANCE, COCLAUSE, COERCE, LEVEL, AXIOM,
                     DATA, CONSTRUCTOR, CLASS, RECORD, FIELD, META, OTHER }

  public record Entry(
      String shortName,
      String longName,           // module path:def (or just def), as printed
      Kind kind,
      ModulePath modulePath,
      String absoluteFile,       // absolute path to source file, may be empty for generated
      int line,                  // 1-based; 0 when unknown
      int column,                // 1-based; 0 when unknown
      String signature           // single-line, may be empty
  ) {}

  private final String myLibraryName;
  private final Path myCacheFile;
  private final Map<ModulePath, FileStamp> myTimestamps = new LinkedHashMap<>();
  private final Map<ModulePath, List<Entry>> myEntries = new LinkedHashMap<>();
  // Generated bucket — keyed by a synthetic "$generated" path. Package-private so
  // SymbolIndexStore can recognize the sentinel when reading the cache back.
  static final ModulePath GENERATED_BUCKET = new ModulePath("$generated");

  private SymbolIndex(String libraryName, Path cacheFile) {
    myLibraryName = libraryName;
    myCacheFile = cacheFile;
  }

  public Collection<Entry> allEntries() {
    List<Entry> all = new ArrayList<>();
    for (List<Entry> es : myEntries.values()) all.addAll(es);
    return all;
  }

  /** True, when this module isn't cached yet, or its cached mtime/size doesn't match the source. */
  public boolean isStale(SourceLibrary library, ModulePath mp) {
    FileStamp cached = myTimestamps.get(mp);
    if (cached == null) return true;
    FileStamp now = SymbolIndexStore.sourceStamp(library, mp);
    return !cached.equals(now);
  }

  /** Loads the index for the given library or returns an empty one. */
  public static SymbolIndex loadOrCreate(SourceLibrary library) {
    Path cacheFile = SymbolIndexStore.cacheFileFor(library);
    SymbolIndex idx = new SymbolIndex(library.getLibraryName(), cacheFile);
    if (cacheFile != null && Files.isRegularFile(cacheFile)) {
      try {
        SymbolIndexStore.load(cacheFile, idx.myTimestamps, idx.myEntries);
      } catch (IOException e) {
        // corrupt cache: start clean
        idx.myTimestamps.clear();
        idx.myEntries.clear();
      }
    }
    return idx;
  }

  /**
   * Loads-or-creates and {@link #refresh refreshes} the symbol index for every
   * library in scope, keyed by a library. Refreshing also registers each source
   * module on {@code server} (and, when {@code withTests} is set, each test
   * module) so callers can query raw/resolved groups afterward.
   */
  public static Map<SourceLibrary, SymbolIndex> refreshAll(
      @NotNull List<SourceLibrary> libsInScope, @NotNull ArendServer server, boolean withTests) {
    Map<SourceLibrary, SymbolIndex> indexes = new LinkedHashMap<>();
    for (SourceLibrary lib : libsInScope) {
      indexes.put(lib, refreshLibrary(lib, server, withTests));
    }
    return indexes;
  }

  /**
   * Loads-or-creates the index for a single {@code library}, registers on
   * {@code server} each source module (and, when {@code withTests} is set, each
   * test module) whose cached mtime/size is stale -- so its raw/resolved group
   * becomes queryable -- then {@link #refresh refreshes} and {@link #save saves}
   * the index. The on-disk cache serves every unchanged module, so a cold server
   * reparses only what actually changed since the previous run.
   */
  public static @NotNull SymbolIndex refreshLibrary(
      @NotNull SourceLibrary library, @NotNull ArendServer server, boolean withTests) {
    SymbolIndex idx = loadOrCreate(library);
    for (ModulePath mp : library.findModules(false)) {
      if (idx.isStale(library, mp)) {
        server.findModule(mp, library.getLibraryName(), false, false);
      }
    }
    if (withTests) {
      for (ModulePath mp : library.findModules(true)) {
        server.findModule(mp, library.getLibraryName(), true, false);
      }
    }
    idx.refresh(library, server, false);
    idx.save();
    return idx;
  }

  /**
   * Rebuilds entries for every module of {@code library} that has changed
   * (or that isn't yet in the cache) using groups already loaded by {@code server}.
   * Generated modules are always re-collected.
   */
  public void refresh(@NotNull SourceLibrary library, @NotNull ArendServer server, boolean force) {
    String libName = library.getLibraryName();
    Set<ModulePath> seen = new HashSet<>();

    // 1) Source modules. Drive iteration from the library's on-disk module list
    // so we keep cached entries for modules the server hasn't (re-)loaded.
    for (ModulePath mp : library.findModules(false)) {
      seen.add(mp);
      FileStamp now = SymbolIndexStore.sourceStamp(library, mp);
      FileStamp prev = myTimestamps.get(mp);
      if (!force && prev != null && prev.equals(now) && myEntries.containsKey(mp)) continue;

      ModuleLocation moduleLoc = new ModuleLocation(libName, ModuleLocation.LocationKind.SOURCE, mp);
      ConcreteGroup group = server.getRawGroup(moduleLoc);
      if (group == null) continue;

      List<Entry> entries = new ArrayList<>();
      Path absolute = SymbolIndexStore.sourcePath(library, mp);
      String absStr = absolute == null ? "" : absolute.toString();
      collectGroup(group, mp, absStr, entries, new HashSet<>());
      myEntries.put(mp, entries);
      myTimestamps.put(mp, now);
    }

    // 2) Generated modules (metas registered programmatically)
    seen.add(GENERATED_BUCKET);
    List<Entry> generated = new ArrayList<>();
    Set<LocatedReferable> seenRefs = Collections.newSetFromMap(new IdentityHashMap<>());
    for (ModuleLocation moduleLoc : server.getModules()) {
      if (!moduleLoc.getLibraryName().equals(libName)) continue;
      if (moduleLoc.getLocationKind() != ModuleLocation.LocationKind.GENERATED) continue;
      ConcreteGroup group = server.getRawGroup(moduleLoc);
      if (group != null) collectGenerated(group, moduleLoc.getModulePath(), generated, seenRefs);
    }
    // Also harvest from getGeneratedNames(), in case any referable bypassed
    // the module-level registration path.
    ArendLibrary arendLib = server.getLibrary(libName);
    if (arendLib != null) {
      for (Map.Entry<String, LocatedReferable> e : arendLib.getGeneratedNames().entrySet()) {
        LocatedReferable ref = e.getValue();
        if (ref == null || !seenRefs.add(ref)) continue;
        Entry entry = entryFromGenerated(e.getKey(), ref, null);
        if (entry != null) generated.add(entry);
      }
    }
    myEntries.put(GENERATED_BUCKET, generated);
    myTimestamps.put(GENERATED_BUCKET, GENERATED_STAMP);

    // 3) prune entries for modules that no longer exist
    myTimestamps.keySet().retainAll(seen);
    myEntries.keySet().retainAll(seen);
  }

  /** Persists the current state to disk (via {@link SymbolIndexStore}); IO errors are swallowed. */
  public void save() {
    SymbolIndexStore.write(myCacheFile, myLibraryName, myTimestamps, myEntries);
  }

  // ---- group walking ------------------------------------------------------

  private static void collectGroup(ConcreteGroup group, ModulePath mp, String absFile,
                                   List<Entry> out, Set<LocatedReferable> seen) {
    LocatedReferable ref = group.referable();
    if (ref instanceof TCDefReferable tcRef) {
      if (seen.add(tcRef)) addRefEntry(tcRef, group.definition(), mp, absFile, out);
    }
    for (LocatedReferable inner : group.getInternalReferables()) {
      if (!seen.add(inner)) continue;
      Concrete.GeneralDefinition def = findInternalDefinition(group.definition(), inner);
      addRefEntry(inner, def, mp, absFile, out);
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) {
        collectGroup(statement.group(), mp, absFile, out, seen);
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      collectGroup(dyn, mp, absFile, out, seen);
    }
  }

  private static @Nullable Concrete.GeneralDefinition findInternalDefinition(
      @Nullable Concrete.ResolvableDefinition outer, LocatedReferable internal) {
    if (outer instanceof Concrete.DataDefinition data) {
      for (Concrete.ConstructorClause clause : data.getConstructorClauses()) {
        for (Concrete.Constructor c : clause.getConstructors()) {
          if (c.getData() == internal) return c;
        }
      }
    } else if (outer instanceof Concrete.ClassDefinition cls) {
      for (Concrete.ClassElement el : cls.getElements()) {
        if (el instanceof Concrete.ClassField f && f.getData() == internal) return f;
      }
    }
    return null;
  }

  private static void addRefEntry(LocatedReferable ref, @Nullable Concrete.GeneralDefinition def,
                                  ModulePath mp, String absFile, List<Entry> out) {
    int[] pos = SourcePositionUtils.lineColumn(ref);
    String signature = def == null ? "" : safeRender(def);
    String longName = ref.getRefLongName().toString();
    out.add(new Entry(
        ref.textRepresentation(), longName, kindOf(ref, def), mp,
        absFile, pos[0], pos[1], signature
    ));
  }

  private static void collectGenerated(ConcreteGroup group, ModulePath mp,
                                        List<Entry> out, Set<LocatedReferable> seenRefs) {
    LocatedReferable ref = group.referable();
    if (seenRefs.add(ref)) {
      String label = mp.toString().isEmpty() ? "" : mp.toString();
      Entry entry = entryFromGenerated(ref.textRepresentation(), ref, label);
      if (entry != null) out.add(entry);
    }
    for (LocatedReferable inner : group.getInternalReferables()) {
      if (seenRefs.add(inner)) {
        Entry entry = entryFromGenerated(inner.textRepresentation(), inner, mp.toString());
        if (entry != null) out.add(entry);
      }
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) collectGenerated(statement.group(), mp, out, seenRefs);
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      collectGenerated(dyn, mp, out, seenRefs);
    }
  }

  private static @Nullable Entry entryFromGenerated(String shortName, LocatedReferable ref, @Nullable String moduleHint) {
    if (shortName == null || shortName.isEmpty()) return null;
    if (ref == null) return null;
    Kind k = kindOf(ref, null);
    String longName = ref.getRefLongName().toString();
    ModulePath mp = ref.getModulePath();
    String moduleLabel = mp == null ? (moduleHint == null ? "<generated>" : moduleHint) : mp.toString();
    String signature = "<generated " + describeKind(k) + " from " + moduleLabel + ">";
    if (ref instanceof MetaReferable) signature = "\\meta " + shortName + "  " + signature;
    int[] pos = SourcePositionUtils.lineColumn(ref);
    return new Entry(shortName, longName, k, mp == null ? GENERATED_BUCKET : mp, "", pos[0], pos[1], signature);
  }

  private static String describeKind(Kind k) {
    return k == null ? "definition" : k.name().toLowerCase(Locale.ROOT);
  }

  /** Maps a referable and its concrete definition to an index {@link Kind}; shared with {@code -ps}. */
  public static Kind kindOf(LocatedReferable ref, @Nullable Concrete.GeneralDefinition def) {
    // Prefer the fine-grained kind from the concrete definition when available
    // (so we can distinguish a lemma vs. sfunc vs. axiom vs. type vs. func).
    if (def instanceof Concrete.BaseFunctionDefinition baseFunctionDefinition) {
      return switch (baseFunctionDefinition.getKind()) {
        case FUNC -> Kind.FUNCTION;
        case SFUNC -> Kind.SFUNC;
        case LEMMA -> Kind.LEMMA;
        case TYPE -> Kind.TYPE;
        case AXIOM -> Kind.AXIOM;
        case INSTANCE -> Kind.INSTANCE;
        case COERCE -> Kind.COERCE;
        case LEVEL -> Kind.LEVEL;
        case FUNC_COCLAUSE, CLASS_COCLAUSE -> Kind.COCLAUSE;
        case CONS -> Kind.CONSTRUCTOR;
      };
    }
    if (def instanceof Concrete.MetaDefinition) return Kind.META;
    if (def instanceof Concrete.DataDefinition) return Kind.DATA;
    if (def instanceof Concrete.ClassDefinition cdef) return cdef.isRecord() ? Kind.RECORD : Kind.CLASS;
    if (def instanceof Concrete.Constructor) return Kind.CONSTRUCTOR;
    if (def instanceof Concrete.ClassField) return Kind.FIELD;

    if (ref instanceof MetaReferable) return Kind.META;
    if (!(ref instanceof GlobalReferable g)) return Kind.OTHER;
    return switch (g.getKind()) {
      case DATA -> Kind.DATA;
      case FUNCTION -> Kind.FUNCTION;
      case COCLAUSE_FUNCTION -> Kind.COCLAUSE;
      case INSTANCE -> Kind.INSTANCE;
      case CLASS -> Kind.CLASS;
      case RECORD -> Kind.RECORD;
      case CONSTRUCTOR, DEFINED_CONSTRUCTOR -> Kind.CONSTRUCTOR;
      case FIELD -> Kind.FIELD;
      case LEVEL -> Kind.LEVEL;
      case META -> Kind.META;
      case OTHER -> Kind.OTHER;
    };
  }

  private static String safeRender(Concrete.GeneralDefinition def) {
    try {
      return SignaturePrintVisitor.render(def);
    } catch (RuntimeException e) {
      return "";
    }
  }

}
