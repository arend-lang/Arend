package org.arend.frontend.symbol;

import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.List;

/**
 * Shared JSON emitter for the retrieval commands ({@code -ss}, {@code -ps}).
 *
 * <p>Produces a single object {@code {"results": [ ... ], "count": N}} where each
 * element of {@code results} carries {@code library} (optional), {@code module},
 * {@code name}, {@code kind}, at most one of {@code signature}/{@code expression},
 * and a nested {@code location} object {@code {"file", "line", "col"}} (its
 * {@code file} omitted for generated modules). {@code count} is the total number
 * of matches, which may exceed {@code results.length} when the caller truncated
 * the list (e.g. {@code -ss limit=N}).
 *
 * <p>Keeping one writer means {@code -ss} and {@code -ps} stay byte-for-byte
 * consistent; the hand-rolled emitter avoids pulling a JSON library into the CLI.
 */
public final class ResultJson {
  private ResultJson() {}

  /**
   * One result row. A {@code null} or empty optional field ({@code library},
   * {@code signature}, {@code expression}, {@code file}) is omitted from the
   * emitted object; {@code line}/{@code col} are always present (0 = unknown).
   */
  public record Row(
      @Nullable String library,
      String module,
      String name,
      String kind,
      @Nullable String signature,
      @Nullable String expression,
      @Nullable String file,
      int line,
      int col) {}

  /** Emits {@code {"results": [...], "count": count}} on {@code out}. */
  public static void write(PrintStream out, List<Row> rows, int count) {
    if (rows.isEmpty()) {
      out.println("{\"results\": [], \"count\": " + count + "}");
      return;
    }
    out.println("{");
    out.println("  \"results\": [");
    for (int i = 0; i < rows.size(); i++) {
      StringBuilder sb = new StringBuilder("    ");
      appendRow(sb, rows.get(i));
      if (i < rows.size() - 1) sb.append(',');
      out.println(sb);
    }
    out.println("  ],");
    out.println("  \"count\": " + count);
    out.println("}");
  }

  private static void appendRow(StringBuilder sb, Row r) {
    sb.append('{');
    boolean first = true;
    if (r.library() != null && !r.library().isEmpty()) first = str(sb, first, "library", r.library());
    first = str(sb, first, "module", r.module());
    first = str(sb, first, "name", r.name());
    first = str(sb, first, "kind", r.kind());
    if (r.signature() != null && !r.signature().isEmpty()) first = str(sb, first, "signature", r.signature());
    if (r.expression() != null && !r.expression().isEmpty()) first = str(sb, first, "expression", r.expression());
    if (!first) sb.append(',');
    sb.append("\"location\":{");
    boolean locFirst = true;
    if (r.file() != null && !r.file().isEmpty()) locFirst = str(sb, locFirst, "file", r.file());
    locFirst = num(sb, locFirst, "line", r.line());
    num(sb, locFirst, "col", r.col());
    sb.append("}}");
  }

  private static boolean str(StringBuilder sb, boolean first, String key, String value) {
    if (!first) sb.append(',');
    sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    return false;
  }

  private static boolean num(StringBuilder sb, boolean first, String key, int value) {
    if (!first) sb.append(',');
    sb.append('"').append(key).append("\":").append(value);
    return false;
  }

  /** Minimal RFC-8259 string escaping (backslashes are common in Arend signatures). */
  public static String escape(String s) {
    StringBuilder b = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        case '\b' -> b.append("\\b");
        case '\f' -> b.append("\\f");
        default -> {
          if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
        }
      }
    }
    return b.toString();
  }
}
