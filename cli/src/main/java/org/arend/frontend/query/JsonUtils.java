package org.arend.frontend.query;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Leaf JSON primitives shared by every query tool's emitter -- string escaping,
 * quoted-string literals, string arrays, and the {@code {"file","line","col"}}
 * location object. The document <em>shape</em> stays per-tool ({@link ResultJson}'s
 * flat {@code results}, {@code -sc}'s {@code entries}, {@code -ch}'s tree); only
 * these leaves are centralized so all three families escape and render identically.
 */
public final class JsonUtils {

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

  /** A quoted, escaped JSON string literal. */
  public static String str(String s) {
    return "\"" + escape(s) + "\"";
  }

  /** A JSON array of quoted strings; {@code "[]"} when empty. */
  public static String strArray(Collection<String> items) {
    if (items.isEmpty()) return "[]";
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (String s : items) {
      if (!first) sb.append(',');
      sb.append(str(s));
      first = false;
    }
    return sb.append(']').toString();
  }

  /**
   * A location object {@code {"file"?,"line","col"}}; {@code file} is omitted when
   * {@code null} or empty, {@code line}/{@code col} are always present (0 = unknown).
   */
  public static String location(@Nullable String file, int line, int col) {
    StringBuilder sb = new StringBuilder("{");
    if (file != null && !file.isEmpty()) sb.append("\"file\":").append(str(file)).append(',');
    return sb.append("\"line\":").append(line).append(",\"col\":").append(col).append('}').toString();
  }
}
