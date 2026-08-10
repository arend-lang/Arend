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

/**
 * The on-disk half of {@link SymbolIndex}: where a library's index cache lives, how source
 * files are stamped for staleness, and the line-oriented cache format (read/write + escaping).
 * {@link SymbolIndex} owns the in-memory model and group-walking; this class owns everything
 * that touches the filesystem and the serialized layout, so the format can change in one place.
 */
final class SymbolIndexStore {
  private SymbolIndexStore() {}

  // The header carries a format version; bumping it forces a clean rebuild of any
  // older cache on disk. The stamp pairs file size with mtime so identical-mtime
  // overwrites (same-second edits, a `git checkout` of an already-matching file,
  // coarse-mtime filesystems) still invalidate the cached entries.
  private static final String FORMAT_HEADER = "# arend symbol index v4";

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

  /** Persists {@code timestamps}/{@code entries} to {@code cacheFile}, swallowing IO errors (the index is best-effort). */
  static void write(@Nullable Path cacheFile, String libraryName,
                    Map<ModulePath, SymbolIndex.FileStamp> timestamps,
                    Map<ModulePath, List<SymbolIndex.Entry>> entries) {
    if (cacheFile == null) return;
    try {
      Path parent = cacheFile.getParent();
      if (parent != null) Files.createDirectories(parent);
      try (BufferedWriter w = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
        writeTo(w, libraryName, timestamps, entries);
      }
    } catch (IOException e) {
      // index is best-effort; don't fail the whole CLI
    }
  }

  private static void writeTo(BufferedWriter w, String libraryName,
                              Map<ModulePath, SymbolIndex.FileStamp> timestamps,
                              Map<ModulePath, List<SymbolIndex.Entry>> entries) throws IOException {
    w.write(FORMAT_HEADER); w.newLine();
    w.write("library: " + libraryName); w.newLine();
    for (Map.Entry<ModulePath, SymbolIndex.FileStamp> ts : timestamps.entrySet()) {
      ModulePath mp = ts.getKey();
      SymbolIndex.FileStamp st = ts.getValue();
      List<SymbolIndex.Entry> es = entries.getOrDefault(mp, Collections.emptyList());
      w.write("module " + mp + " " + st.mtime() + " " + st.size()); w.newLine();
      for (SymbolIndex.Entry e : es) {
        // escape() turns any newline in a multi-line container signature into a
        // literal `\n`, so the entry stays on one physical line; unescape() on
        // read restores it.
        w.write("  " + escape(e.shortName()) + "|" + escape(e.longName()) + "|" + e.kind().name() + "|"
            + (e.absoluteFile() == null ? "" : e.absoluteFile()) + "|"
            + e.line() + "|" + e.column() + "|" + escape(e.signature()));
        w.newLine();
      }
    }
  }

  /** Loads {@code cacheFile} into {@code timestamps}/{@code entries}; throws {@link IOException} on a bad/foreign format. */
  static void load(Path cacheFile, Map<ModulePath, SymbolIndex.FileStamp> timestamps,
                   Map<ModulePath, List<SymbolIndex.Entry>> entries) throws IOException {
    try (BufferedReader r = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
      String header = r.readLine();
      if (header == null || !header.equals(FORMAT_HEADER)) throw new IOException("bad header");
      String libLine = r.readLine();
      if (libLine == null || !libLine.startsWith("library: ")) throw new IOException("no library");
      ModulePath current = null;
      List<SymbolIndex.Entry> currentEntries = null;
      String line;
      while ((line = r.readLine()) != null) {
        if (line.startsWith("module ")) {
          // module <path> <mtime> <size>
          String rest = line.substring("module ".length());
          int secondSpace = rest.lastIndexOf(' ');
          int firstSpace = rest.lastIndexOf(' ', secondSpace - 1);
          if (firstSpace < 0 || secondSpace < 0) throw new IOException("bad module line: " + line);
          long size = Long.parseLong(rest.substring(secondSpace + 1));
          long mtime = Long.parseLong(rest.substring(firstSpace + 1, secondSpace));
          String mpStr = rest.substring(0, firstSpace);
          current = mpStr.equals(SymbolIndex.GENERATED_BUCKET.toString())
              ? SymbolIndex.GENERATED_BUCKET : ModulePath.fromString(mpStr);
          currentEntries = new ArrayList<>();
          timestamps.put(current, new SymbolIndex.FileStamp(mtime, size));
          entries.put(current, currentEntries);
        } else if (current != null && line.startsWith("  ")) {
          SymbolIndex.Entry e = parseEntry(line.substring(2), current);
          if (e != null) currentEntries.add(e);
        }
      }
    }
  }

  private static @Nullable SymbolIndex.Entry parseEntry(String line, ModulePath mp) {
    String[] parts = line.split("\\|", 7);
    if (parts.length < 7) return null;
    try {
      SymbolIndex.Kind k = SymbolIndex.Kind.valueOf(parts[2]);
      int ln = Integer.parseInt(parts[4]);
      int col = Integer.parseInt(parts[5]);
      return new SymbolIndex.Entry(unescape(parts[0]), unescape(parts[1]), k, mp, parts[3], ln, col, unescape(parts[6]));
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
