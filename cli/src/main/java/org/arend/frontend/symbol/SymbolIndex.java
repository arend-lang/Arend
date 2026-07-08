package org.arend.frontend.symbol;

import org.arend.error.SourcePosition;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.DataContainer;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.MetaReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * On-disk + in-memory cache of (name, file:line, signature) triples per library.
 *
 * Layout: per-library file at {@code <binariesDir>/.arend-symbol-index} (or
 * {@code <sourcesDir>/../.arend-symbol-index/<library>.idx} when no binariesDir
 * is configured). Each module has a stored timestamp; when the underlying .ard
 * file's mtime advances, that module's entries are dropped and rebuilt from
 * {@link ArendServer#getRawGroup}.
 */
public final class SymbolIndex {
  // v2 adds a file-size field next to the mtime so identical-mtime overwrites (rare
  // but real: same-second edits, `git checkout` of a file that already matched, file
  // systems with low mtime resolution) invalidate the cached entries. v3 lets a
  // signature span multiple lines (containers now store their full body -- fields /
  // constructors), stored with `\n` escaped so each entry stays on one physical line.
  // Bumping the header version forces a clean rebuild of any older cache on disk.
  private static final String FORMAT_HEADER = "# arend symbol index v3";
  static final FileStamp GENERATED_STAMP = new FileStamp(-1L, -1L);

  /** mtime+size snapshot of a source file; staleness compares both. */
  public record FileStamp(long mtime, long size) {}

  public enum Kind { FUNCTION, SFUNC, LEMMA, TYPE, INSTANCE, COCLAUSE, COERCE, LEVEL, AXIOM,
                     DATA, CONSTRUCTOR, CLASS, RECORD, FIELD, META, OTHER }

  public record Entry(
      String shortName,
      String longName,           // module path:rest (or just rest), as printed
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
  // Generated bucket — keyed by a synthetic "$generated" path.
  private static final ModulePath GENERATED_BUCKET = new ModulePath("$generated");

  private SymbolIndex(String libraryName, Path cacheFile) {
    myLibraryName = libraryName;
    myCacheFile = cacheFile;
  }

  public String libraryName() { return myLibraryName; }

  public Collection<Entry> allEntries() {
    List<Entry> all = new ArrayList<>();
    for (List<Entry> es : myEntries.values()) all.addAll(es);
    return all;
  }

  /** True when this module isn't cached yet, or its cached mtime/size doesn't match the source. */
  public boolean isStale(SourceLibrary library, ModulePath mp) {
    FileStamp cached = myTimestamps.get(mp);
    if (cached == null) return true;
    FileStamp now = sourceStamp(library, mp);
    return !cached.equals(now);
  }

  /** Loads the index for the given library, or returns an empty one. */
  public static SymbolIndex loadOrCreate(SourceLibrary library) {
    Path cacheFile = cacheFileFor(library);
    SymbolIndex idx = new SymbolIndex(library.getLibraryName(), cacheFile);
    if (cacheFile != null && Files.isRegularFile(cacheFile)) {
      try {
        idx.readFrom(cacheFile);
      } catch (IOException e) {
        // corrupt cache: start clean
        idx.myTimestamps.clear();
        idx.myEntries.clear();
      }
    }
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
      FileStamp now = sourceStamp(library, mp);
      FileStamp prev = myTimestamps.get(mp);
      if (!force && prev != null && prev.equals(now) && myEntries.containsKey(mp)) continue;

      ModuleLocation moduleLoc = new ModuleLocation(libName, ModuleLocation.LocationKind.SOURCE, mp);
      ConcreteGroup group = server.getRawGroup(moduleLoc);
      if (group == null) continue;

      List<Entry> entries = new ArrayList<>();
      Path absolute = sourcePath(library, mp);
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

  /** Persists the current state to {@link #myCacheFile}, swallowing IO errors. */
  public void save() {
    if (myCacheFile == null) return;
    try {
      Path parent = myCacheFile.getParent();
      if (parent != null) Files.createDirectories(parent);
      try (BufferedWriter w = Files.newBufferedWriter(myCacheFile, StandardCharsets.UTF_8)) {
        writeTo(w);
      }
    } catch (IOException e) {
      // index is best-effort; don't fail the whole CLI
    }
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
    int[] pos = positionOf(ref);
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
    if (ref != null && seenRefs.add(ref)) {
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
    if (!(ref instanceof GlobalReferable)) return null;
    Kind k = kindOf(ref, null);
    String longName = ref.getRefLongName().toString();
    ModulePath mp = ref.getModulePath();
    String moduleLabel = mp == null ? (moduleHint == null ? "<generated>" : moduleHint) : mp.toString();
    String signature = "<generated " + describeKind(k) + " from " + moduleLabel + ">";
    if (ref instanceof MetaReferable) signature = "\\meta " + shortName + "  " + signature;
    int[] pos = positionOf(ref);
    return new Entry(shortName, longName, k, mp == null ? GENERATED_BUCKET : mp, "", pos[0], pos[1], signature);
  }

  private static String describeKind(Kind k) {
    return k == null ? "definition" : k.name().toLowerCase(Locale.ROOT);
  }

  private static int[] positionOf(LocatedReferable ref) {
    Object data = ref instanceof DataContainer dc ? dc.getData() : null;
    if (data instanceof SourcePosition sp) return new int[] { sp.line, sp.column };
    return new int[] { 0, 0 };
  }

  private static Kind kindOf(LocatedReferable ref, @Nullable Concrete.GeneralDefinition def) {
    // Prefer the fine-grained kind from the concrete definition when available
    // (so we can distinguish lemma vs sfunc vs axiom vs type vs func).
    if (def instanceof Concrete.BaseFunctionDefinition fdef) {
      return switch (fdef.getKind()) {
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

  // ---- file helpers -------------------------------------------------------

  private static FileStamp sourceStamp(SourceLibrary library, ModulePath mp) {
    Path file = sourcePath(library, mp);
    if (file == null) return new FileStamp(0L, 0L);
    try {
      long mtime = Files.getLastModifiedTime(file).toMillis();
      long size = Files.size(file);
      return new FileStamp(mtime, size);
    } catch (IOException e) {
      return new FileStamp(0L, 0L);
    }
  }

  private static @Nullable Path sourcePath(SourceLibrary library, ModulePath mp) {
    if (!(library instanceof FileSourceLibrary fl)) return null;
    Path src = libSourcePath(fl);
    if (src == null) return null;
    try {
      return FileUtils.sourceFile(src, mp).toAbsolutePath().normalize();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static @Nullable Path libSourcePath(FileSourceLibrary lib) {
    return lib.getSourceBasePath();
  }

  private static @Nullable Path cacheFileFor(SourceLibrary library) {
    if (!(library instanceof FileSourceLibrary fl)) return null;
    Path bin = fl.getBinaryBasePath();
    if (bin != null) return bin.resolve(".arend-symbol-index");
    Path src = libSourcePath(fl);
    if (src == null) return null;
    Path parent = src.getParent();
    if (parent == null) parent = src;
    return parent.resolve(".arend-symbol-index").resolve(library.getLibraryName() + ".idx");
  }

  // ---- format -------------------------------------------------------------

  private void writeTo(BufferedWriter w) throws IOException {
    w.write(FORMAT_HEADER); w.newLine();
    w.write("library: " + myLibraryName); w.newLine();
    for (Map.Entry<ModulePath, FileStamp> ts : myTimestamps.entrySet()) {
      ModulePath mp = ts.getKey();
      FileStamp st = ts.getValue();
      List<Entry> entries = myEntries.getOrDefault(mp, Collections.emptyList());
      w.write("module " + mp + " " + st.mtime() + " " + st.size()); w.newLine();
      for (Entry e : entries) {
        // escape() turns any newline in a multi-line container signature into a
        // literal `\n`, so the entry stays on one physical line; unescape() on
        // read restores it.
        w.write("  " + escape(e.shortName) + "|" + escape(e.longName) + "|" + e.kind.name() + "|"
            + (e.absoluteFile == null ? "" : e.absoluteFile) + "|"
            + e.line + "|" + e.column + "|" + escape(e.signature));
        w.newLine();
      }
    }
  }

  private void readFrom(Path file) throws IOException {
    try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String header = r.readLine();
      if (header == null || !header.equals(FORMAT_HEADER)) throw new IOException("bad header");
      String libLine = r.readLine();
      if (libLine == null || !libLine.startsWith("library: ")) throw new IOException("no library");
      ModulePath current = null;
      List<Entry> currentEntries = null;
      String line;
      while ((line = r.readLine()) != null) {
        if (line.startsWith("module ")) {
          // module <path> <mtime> <size>
          String rest = line.substring("module ".length());
          int sp2 = rest.lastIndexOf(' ');
          int sp1 = rest.lastIndexOf(' ', sp2 - 1);
          if (sp1 < 0 || sp2 < 0) throw new IOException("bad module line: " + line);
          long size = Long.parseLong(rest.substring(sp2 + 1));
          long mtime = Long.parseLong(rest.substring(sp1 + 1, sp2));
          String mpStr = rest.substring(0, sp1);
          current = mpStr.equals(GENERATED_BUCKET.toString()) ? GENERATED_BUCKET : ModulePath.fromString(mpStr);
          currentEntries = new ArrayList<>();
          myTimestamps.put(current, new FileStamp(mtime, size));
          myEntries.put(current, currentEntries);
        } else if (current != null && line.startsWith("  ")) {
          Entry e = parseEntry(line.substring(2), current);
          if (e != null) currentEntries.add(e);
        }
      }
    }
  }

  private static @Nullable Entry parseEntry(String line, ModulePath mp) {
    String[] parts = line.split("\\|", 7);
    if (parts.length < 7) return null;
    try {
      Kind k = Kind.valueOf(parts[2]);
      int ln = Integer.parseInt(parts[4]);
      int col = Integer.parseInt(parts[5]);
      return new Entry(unescape(parts[0]), unescape(parts[1]), k, mp, parts[3], ln, col, unescape(parts[6]));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n").replace("\r", "\\r");
  }

  private static String unescape(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char n = s.charAt(++i);
        out.append(switch (n) {
          case '\\' -> '\\';
          case '|' -> '|';
          case 'n' -> '\n';
          case 'r' -> '\r';
          default -> n;
        });
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
