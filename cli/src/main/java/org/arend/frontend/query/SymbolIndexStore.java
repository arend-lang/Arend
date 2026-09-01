package org.arend.frontend.query;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.SourceLibrary;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

final class SymbolIndexStore {
  private SymbolIndexStore() {}

  // The header carries a format version; bumping it forces a clean rebuild of any
  // older cache on disk. The stamp pairs file size with mtime so identical-mtime
  // overwrites (same-second edits, a `git checkout` of an already-matching file,
  // coarse-mtime filesystems) still invalidate the cached entries.
  private static final String FORMAT_HEADER = "# arend symbol index v5";

  /**
   * The fields of an entry line, in write order. Two of {@link SymbolIndex.Entry}'s components
   * are deliberately absent, because both are properties of the enclosing {@code module} line
   * rather than of the entry: {@code modulePath} and {@code absoluteFile}. Hoisting the file out
   * of the entry line took 29% off the cache; it was the same absolute path repeated on all
   * 10 000-odd entries.
   *
   * <p>This is the single source of truth for the layout -- {@link #writeTo} joins
   * {@link #print} over {@link #FIELDS} and {@link #parseEntry} reads each field back through
   * {@link #of}, so the writer, the reader and the field count cannot drift apart when a
   * field is added. (Adding one does change the format, so bump {@link #FORMAT_HEADER} too.)
   */
  private enum Field {
    SHORT_NAME(SymbolIndex.Entry::shortName),
    LONG_NAME(SymbolIndex.Entry::longName),
    KIND(e -> e.kind().name()),
    LINE(e -> Integer.toString(e.line())),
    COLUMN(e -> Integer.toString(e.column())),
    SIGNATURE(SymbolIndex.Entry::signature);

    private final Function<SymbolIndex.Entry, String> myPrinter;

    Field(Function<SymbolIndex.Entry, String> printer) {
      myPrinter = printer;
    }

    /** This field of {@code e}, escaped for the wire. */
    String print(SymbolIndex.Entry e) {
      return escape(myPrinter.apply(e));
    }

    /** This field's unescaped value out of a split entry line. */
    String of(List<String> parts) {
      return unescape(parts.get(ordinal()));
    }
  }

  private static final Field[] FIELDS = Field.values();

  /** Marks the optional per-module dependency line; see {@link SymbolIndex.ModuleDep}. */
  private static final String DEPS_PREFIX = "deps ";

  // ---- file location ------------------------------------------------------

  /** The cache file backing {@code library}'s index, or {@code null} for a non-file library. */
  static @Nullable Path cacheFileFor(SourceLibrary library) {
    if (!(library instanceof FileSourceLibrary fl)) return null;
    Path bin = fl.getBinaryBasePath();
    if (bin != null) return bin.resolve(".arend-symbol-index");
    Path src = libSourcePath(fl);
    if (src == null) return null;
    Path parent = src.getParent();
    if (parent == null) parent = src;
    return parent.resolve(".arend-symbol-index").resolve(library.getLibraryName() + ".idx");
  }

  /** The absolute, normalized source file backing module {@code mp} in {@code library}, or {@code null}. */
  static @Nullable Path sourcePath(SourceLibrary library, ModulePath mp) {
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

  /** An mtime+size stamp of module {@code mp}'s source file (zeros when it can't be read). */
  static SymbolIndex.FileStamp sourceStamp(SourceLibrary library, ModulePath mp) {
    Path file = sourcePath(library, mp);
    if (file == null) return new SymbolIndex.FileStamp(0L, 0L);
    try {
      long mtime = Files.getLastModifiedTime(file).toMillis();
      long size = Files.size(file);
      return new SymbolIndex.FileStamp(mtime, size);
    } catch (IOException e) {
      return new SymbolIndex.FileStamp(0L, 0L);
    }
  }

  // ---- cache format -------------------------------------------------------

  /** Persists {@code timestamps}/{@code files}/{@code entries} to {@code cacheFile}, swallowing IO errors (the index is best-effort). */
  static void write(@Nullable Path cacheFile, String libraryName,
                    Map<ModulePath, SymbolIndex.FileStamp> timestamps,
                    Map<ModulePath, String> files,
                    Map<ModulePath, List<SymbolIndex.ModuleDep>> deps,
                    Map<ModulePath, List<SymbolIndex.Entry>> entries) {
    if (cacheFile == null) return;
    try {
      Path parent = cacheFile.getParent();
      if (parent != null) Files.createDirectories(parent);
      try (BufferedWriter w = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
        writeTo(w, libraryName, timestamps, files, deps, entries);
      }
    } catch (IOException e) {
      // index is best-effort; don't fail the whole CLI
    }
  }

  private static void writeTo(BufferedWriter w, String libraryName,
                              Map<ModulePath, SymbolIndex.FileStamp> timestamps,
                              Map<ModulePath, String> files,
                              Map<ModulePath, List<SymbolIndex.ModuleDep>> deps,
                              Map<ModulePath, List<SymbolIndex.Entry>> entries) throws IOException {
    w.write(FORMAT_HEADER); w.newLine();
    w.write("library: " + libraryName); w.newLine();
    for (Map.Entry<ModulePath, SymbolIndex.FileStamp> ts : timestamps.entrySet()) {
      ModulePath mp = ts.getKey();
      // The generated bucket is not persisted. SymbolIndex.refresh rebuilds it unconditionally
      // on every run, so caching it buys nothing -- and it would be lossy: an entry's module
      // path is read back from its module line, which for the bucket is the synthetic
      // "$generated" rather than the module the generated referable really belongs to.
      if (SymbolIndex.GENERATED_BUCKET.equals(mp)) continue;
      SymbolIndex.FileStamp st = ts.getValue();
      List<SymbolIndex.Entry> es = entries.getOrDefault(mp, Collections.emptyList());
      // module <path> <mtime> <size> <absolute source file>; the file is escaped and last, and
      // is read back by splitting off the three leading tokens -- a path may contain a space.
      w.write("module " + mp + " " + st.mtime() + " " + st.size()
          + " " + escape(files.getOrDefault(mp, ""))); w.newLine();
      // An optional `deps` line, indented like an entry but distinguishable from one: an entry
      // line always splits into exactly FIELDS.length pipe-separated fields, a deps line has no
      // unescaped pipe at all. Omitted when there are none, which is the common case.
      List<SymbolIndex.ModuleDep> ds = deps.getOrDefault(mp, Collections.emptyList());
      if (!ds.isEmpty()) {
        StringJoiner dl = new StringJoiner(" ", "  " + DEPS_PREFIX, "");
        for (SymbolIndex.ModuleDep d : ds) {
          dl.add(d.module() + ":" + d.stamp().mtime() + ":" + d.stamp().size());
        }
        w.write(dl.toString()); w.newLine();
      }
      for (SymbolIndex.Entry e : es) {
        // Field.print() escapes `\`, `|` and newlines, so a name containing a pipe
        // (`||`, `res|val1`) cannot be mistaken for a field boundary, and a container's
        // multi-line signature still occupies exactly one physical line; Field.of() restores it.
        StringJoiner line = new StringJoiner("|", "  ", "");
        for (Field f : FIELDS) line.add(f.print(e));
        w.write(line.toString());
        w.newLine();
      }
    }
  }

  /** Loads {@code cacheFile} into {@code timestamps}/{@code files}/{@code entries}; throws {@link IOException} on a bad/foreign format. */
  static void load(Path cacheFile, Map<ModulePath, SymbolIndex.FileStamp> timestamps,
                   Map<ModulePath, String> files,
                   Map<ModulePath, List<SymbolIndex.ModuleDep>> deps,
                   Map<ModulePath, List<SymbolIndex.Entry>> entries) throws IOException {
    try (BufferedReader r = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
      String header = r.readLine();
      if (header == null || !header.equals(FORMAT_HEADER)) throw new IOException("bad header");
      String libLine = r.readLine();
      if (libLine == null || !libLine.startsWith("library: ")) throw new IOException("no library");
      ModulePath current = null;
      String currentFile = "";
      List<SymbolIndex.Entry> currentEntries = null;
      String line;
      while ((line = r.readLine()) != null) {
        if (line.startsWith("module ")) {
          // module <path> <mtime> <size> <file>. Split the three leading tokens off from the
          // left: a module path is a dotted identifier and mtime/size are decimal, so none of
          // them can contain a space -- while the trailing file path can.
          String rest = line.substring("module ".length());
          int firstSpace = rest.indexOf(' ');
          int secondSpace = firstSpace < 0 ? -1 : rest.indexOf(' ', firstSpace + 1);
          if (secondSpace < 0) throw new IOException("bad module line: " + line);
          int thirdSpace = rest.indexOf(' ', secondSpace + 1);
          String mpStr = rest.substring(0, firstSpace);
          long mtime = Long.parseLong(rest.substring(firstSpace + 1, secondSpace));
          long size = Long.parseLong(thirdSpace < 0 ? rest.substring(secondSpace + 1)
                                                   : rest.substring(secondSpace + 1, thirdSpace));
          currentFile = thirdSpace < 0 ? "" : unescape(rest.substring(thirdSpace + 1));
          current = mpStr.equals(SymbolIndex.GENERATED_BUCKET.toString())
                  ? SymbolIndex.GENERATED_BUCKET : ModulePath.fromString(mpStr);
          currentEntries = new ArrayList<>();
          timestamps.put(current, new SymbolIndex.FileStamp(mtime, size));
          files.put(current, currentFile);
          entries.put(current, currentEntries);
        } else if (current != null && line.startsWith("  " + DEPS_PREFIX)) {
          deps.put(current, parseDeps(line.substring(2 + DEPS_PREFIX.length())));
        } else if (current != null && line.startsWith("  ")) {
          currentEntries.add(parseEntry(line.substring(2), current, currentFile));
        }
      }
    }
  }

  /**
   * One entry line, or {@link IOException} when it does not fit {@link #FIELDS}.
   *
   * <p>Failing the whole load rather than skipping the line is deliberate. A line that does not
   * parse means the file was written by code whose entry layout differs from this one's -- adding
   * or dropping a field changes the arity without changing the header -- or is corrupt. Skipping
   * would leave the cache looking fresh (the module stamps still match the sources) but missing
   * definitions, so {@code -ss} would quietly find nothing; throwing sends
   * {@code SymbolIndex.loadOrCreate} down its clean-rebuild path instead. It also makes the
   * escaping bug this class's tests cover loud rather than silent: before the escape-aware split,
   * every definition whose name contains a {@code |} produced a wrong-arity line.
   */
  /** A {@code deps} line: space-separated {@code <modulePath>:<mtime>:<size>} triples. */
  private static List<SymbolIndex.ModuleDep> parseDeps(String line) throws IOException {
    List<SymbolIndex.ModuleDep> out = new ArrayList<>();
    for (String token : line.split(" ")) {
      if (token.isEmpty()) continue;
      int size = token.lastIndexOf(':');
      int mtime = size < 0 ? -1 : token.lastIndexOf(':', size - 1);
      if (mtime < 0) throw new IOException("bad deps entry: " + token);
      try {
        out.add(new SymbolIndex.ModuleDep(
            ModulePath.fromString(token.substring(0, mtime)),
            new SymbolIndex.FileStamp(Long.parseLong(token.substring(mtime + 1, size)),
                                      Long.parseLong(token.substring(size + 1)))));
      } catch (RuntimeException e) {
        throw new IOException("bad deps entry: " + token, e);
      }
    }
    return out;
  }

  private static SymbolIndex.Entry parseEntry(String line, ModulePath mp, String file) throws IOException {
    List<String> parts = splitUnescaped(line);
    if (parts.size() != FIELDS.length) {
      throw new IOException("entry line has " + parts.size() + " fields, expected " + FIELDS.length + ": " + line);
    }
    try {
      return new SymbolIndex.Entry(
          Field.SHORT_NAME.of(parts),
          Field.LONG_NAME.of(parts),
          SymbolIndex.Kind.valueOf(Field.KIND.of(parts)),
          mp,
          file,
          Integer.parseInt(Field.LINE.of(parts)),
          Integer.parseInt(Field.COLUMN.of(parts)),
          Field.SIGNATURE.of(parts));
    } catch (RuntimeException e) {
      throw new IOException("bad entry line: " + line, e);
    }
  }

  /**
   * Splits an entry line on the {@code |} delimiters {@link #escape} left unescaped.
   *
   * <p>A backslash always escapes the character after it, so scanning left to right and
   * consuming pairs finds exactly the field boundaries. {@code String.split("\\|")} cannot:
   * it has no notion of the escape, so it cuts a name like {@code ||-search} into empty
   * pieces, so the field count comes out wrong and {@link #parseEntry} rejects the whole file.
   * Escape sequences are left in place here for {@link #unescape} to resolve per field.
   */
  private static List<String> splitUnescaped(String line) {
    List<String> parts = new ArrayList<>(FIELDS.length);
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '\\' && i + 1 < line.length()) {
        current.append(c).append(line.charAt(++i));
      } else if (c == '|') {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    parts.add(current.toString());
    return parts;
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
