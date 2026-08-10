package org.arend.frontend.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared ANSI escape codes for the console query tools: {@link #GREEN} highlights
 * matched names and sub-terms, {@link #RESET} returns to the default colour.
 */
public final class MatchHighlighter {
  private MatchHighlighter() {}

  /**
   * Wraps each {@code [start, end)} range of {@code s} in {@link #GREEN}…{@link #RESET}.
   * Ranges are sorted by start and clamped so overlapping ones never double-wrap.
   */
  public static String highlight(String s, List<int[]> ranges) {
    if (ranges.isEmpty() || !colorEnabled()) return s;
    ranges.sort(Comparator.comparingInt(r -> r[0]));
    StringBuilder sb = new StringBuilder(s.length() + 16);
    int cur = 0;
    for (int[] r : ranges) {
      if (r[1] <= cur) continue;               // fully covered by an earlier range
      int start = Math.max(r[0], cur);
      if (start > cur) sb.append(s, cur, start);
      sb.append(GREEN).append(s, start, r[1]).append(RESET);
      cur = r[1];
    }
    sb.append(s, cur, s.length());
    return sb.toString();
  }

  /**
   * Highlights the maximal identifier run around each 1-based column of {@code content}.
   * The source gives only a token's start column (no span), so the run is grown outward from
   * each column; columns not on an identifier char are skipped.
   */
  public static String highlightIdentifiersAt(String content, List<Integer> columns) {
    List<int[]> ranges = new ArrayList<>();
    for (int column : columns) {
      int idx = column - 1;
      if (idx < 0 || idx >= content.length() || !ArendNameCharSet.isIdChar(content.charAt(idx))) continue;
      int start = idx;
      while (start > 0 && ArendNameCharSet.isIdChar(content.charAt(start - 1))) start--;
      int end = idx;
      while (end < content.length() && ArendNameCharSet.isIdChar(content.charAt(end))) end++;
      ranges.add(new int[] {start, end});
    }
    return highlight(content, ranges);
  }

  private static Boolean colorEnabled;

  /**
   * Whether ANSI colour should be emitted. Colour is on only for an interactive terminal
   * ({@code System.console() != null}) with {@code NO_COLOR} unset (per no-color.org: any
   * non-empty value disables it), so redirected / piped output stays plain and scripts /
   * agents never see escape sequences. Cached: the process's terminal-ness does not change.
   * The {@code --json} and {@code -ch format=flat} outputs never call the highlighter, so
   * their machine-readable output is unaffected either way.
   */
  public static boolean colorEnabled() {
    Boolean c = colorEnabled;
    if (c == null) {
      String noColor = System.getenv("NO_COLOR");
      c = (noColor == null || noColor.isEmpty()) && System.console() != null;
      colorEnabled = c;
    }
    return c;
  }

  /** {@link #GREEN} when {@link #colorEnabled()}, else the empty string. */
  public static String green() { return colorEnabled() ? GREEN : ""; }

  /** {@link #RESET} when {@link #colorEnabled()}, else the empty string. */
  public static String reset() { return colorEnabled() ? RESET : ""; }

  public static final String GREEN = "[32m";
  public static final String RESET = "[0m";
}
