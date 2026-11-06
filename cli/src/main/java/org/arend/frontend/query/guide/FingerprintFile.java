package org.arend.frontend.query.guide;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * The fingerprints recorded for one guide page, in {@code <guide>/.fingerprints/A/B/C.tsv} next to
 * nothing the guide's readers see: the markdown stays free of hashes, so neither a person nor an
 * embedding index has to read past them.
 *
 * <p>One tab-separated row per documented definition, in page order:
 * <pre>
 * name  kind  sig  def  text  mentions  signature
 * </pre>
 * {@code name} is the definition's long name in the module (or {@code (overview)} for the page's
 * prose), {@code sig}/{@code def} its fingerprints ({@code -} when absent), {@code text} the
 * fingerprint of the entry's description as it read when stamped, {@code mentions} a
 * space-separated list of {@code hhhhhhhh:LongName} -- the fingerprint of each definition of the
 * module the description mentions; the hash has a fixed width, so the name after the colon may
 * itself contain colons -- and {@code signature} the statement itself as it read when stamped, so
 * that whoever reviews a changed statement sees what it was, not only what it is.
 *
 * <p>Version 1 files have no {@code signature} column; they are read with an empty one.
 */
final class FingerprintFile {
  static final String DIRECTORY = ".fingerprints";
  static final String OVERVIEW = "(overview)";
  private static final String MAGIC = "# arend -gc fingerprints v2";

  /**
   * One row. {@code mentions} maps a mentioned definition's long name to its mention hash;
   * {@code signature} is null for rows without one (the overview, version 1 files).
   */
  record Row(String name, String kind, @Nullable String sig, @Nullable String def, String text,
             Map<String, String> mentions, @Nullable String signature) {}

  private FingerprintFile() {}

  static Path pathFor(Path guideRoot, GuidePage page) {
    return pathFor(guideRoot, page.relativePath);
  }

  static Path pathFor(Path guideRoot, String pageRelativePath) {
    String rel = pageRelativePath.substring(0, pageRelativePath.length() - ".md".length()) + ".tsv";
    return guideRoot.resolve(DIRECTORY).resolve(rel);
  }

  /** The rows of {@code file} by name, in file order; empty when the file does not exist. */
  static @NotNull LinkedHashMap<String, Row> read(Path file) throws IOException {
    LinkedHashMap<String, Row> rows = new LinkedHashMap<>();
    if (!Files.isRegularFile(file)) return rows;
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] cols = line.split("\t", -1);
      if (cols.length < 6) continue;
      Map<String, String> mentions = new LinkedHashMap<>();
      for (String m : cols[5].split(" ")) {
        int w = Fingerprints.MENTION_HASH_LENGTH;
        if (m.length() > w + 1 && m.charAt(w) == ':') mentions.put(m.substring(w + 1), m.substring(0, w));
      }
      String signature = cols.length > 6 ? dash(cols[6]) : null;
      rows.put(cols[0], new Row(cols[0], cols[1], dash(cols[2]), dash(cols[3]), cols[4], mentions, signature));
    }
    return rows;
  }

  /** Writes {@code rows} atomically, so an interrupted run never leaves a half-written file. */
  static void write(Path file, String moduleName, Collection<Row> rows) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(MAGIC).append(" for module ").append(moduleName)
        .append(". Written by `arend -gc stamp`; do not edit.\n");
    sb.append("# name\tkind\tsig\tdef\ttext\tmentions\tsignature\n");
    for (Row r : rows) {
      sb.append(r.name()).append('\t').append(r.kind()).append('\t')
          .append(orDash(r.sig())).append('\t').append(orDash(r.def())).append('\t')
          .append(r.text()).append('\t');
      boolean first = true;
      for (Map.Entry<String, String> m : r.mentions().entrySet()) {
        if (!first) sb.append(' ');
        sb.append(m.getValue()).append(':').append(m.getKey());
        first = false;
      }
      // The signature is normalized to one line; a tab would split the row, so none survives.
      sb.append('\t').append(r.signature() == null || r.signature().isEmpty() ? "-" : r.signature().replace('\t', ' '));
      sb.append('\n');
    }
    Files.createDirectories(file.getParent());
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /** Moves the fingerprints of a page that moved, if it has any. */
  static void move(Path from, Path to) throws IOException {
    if (!Files.isRegularFile(from)) return;
    Files.createDirectories(to.getParent());
    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
  }

  private static @Nullable String dash(String s) {
    return s.equals("-") ? null : s;
  }

  private static String orDash(@Nullable String s) {
    return s == null ? "-" : s;
  }
}
