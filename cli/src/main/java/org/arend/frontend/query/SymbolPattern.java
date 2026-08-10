package org.arend.frontend.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles a user-supplied symbol-search pattern into a {@link Pattern}. Every
 * non-regex mode matches smart-case: a lowercase pattern char matches either case,
 * an uppercase one matches uppercase only.
 * Grammar:
 *   <text>            literal substring match (default). Rejected when it contains a
 *                     character no Arend name could ({@code '.'}, {@code '('}, ...),
 *                     since that is usually a regex or qualified-name typed by mistake.
 *   glob:<pat>        anchored whole-name match: {@code '*'} = any chars, {@code '?'} =
 *                     any one char (with neither, an exact match). Escape a literal
 *                     star / question mark as {@code '\*'} / {@code '\?'}.
 *   re:<java-regex>   raw Java regex, matched with find(), case-sensitive as typed
 *                     (prepend {@code (?i)} for insensitivity).
 *   hb:<chars>        humpback / boundary-aware fuzzy match: the first char and every
 *                     uppercase letter / digit / operator must land on a word start
 *                     ({@code hb:PAM} matches {@code PosetAddMonoid}), while a lowercase
 *                     letter may also continue the current word ({@code hb:posmon}
 *                     matches {@code PosetMonoid}). See {@link #WORD_START} for how a
 *                     word boundary is defined.
 * A dotted, prefix-less pattern (e.g. {@code Monoid.*-comm}) is instead a by-part
 * qualified-name query; see {@link #compile(String, boolean)} and {@link #matchesLongName}.
 */
public final class SymbolPattern {
  public enum Mode { LITERAL, GLOB, REGEX, HUMPBACK, LONGNAME }

  /** The compiled regex; null exactly for {@link Mode#LONGNAME}, which matches via {@link #mySegments} instead. */
  private final @Nullable Pattern myCompiled;
  private final @NotNull String mySource;
  private final @NotNull Mode myMode;
  /** Per-segment sub-patterns; non-null only for {@link Mode#LONGNAME} (see {@link #matchesLongName}). */
  private final @Nullable List<SymbolPattern> mySegments;

  private SymbolPattern(@NotNull Pattern compiled, @NotNull String source, @NotNull Mode mode) {
    myCompiled = compiled;
    mySource = source;
    myMode = mode;
    mySegments = null;
  }

  private SymbolPattern(@NotNull String source, @NotNull List<SymbolPattern> segments) {
    myCompiled = null;
    mySource = source;
    myMode = Mode.LONGNAME;
    mySegments = segments;
  }

  /** The compiled pattern, present for every mode except {@link Mode#LONGNAME}. */
  private @NotNull Pattern compiled() {
    return Objects.requireNonNull(myCompiled, "compiled regex is absent for a LONGNAME pattern");
  }

  /** The per-segment sub-patterns, present only for a {@link Mode#LONGNAME} pattern. */
  private @NotNull List<SymbolPattern> segments() {
    return Objects.requireNonNull(mySegments, "segments are absent for a non-LONGNAME pattern");
  }

  private @NotNull SymbolPattern lastSegment() {
    return segments().getLast();
  }

  public boolean matches(@NotNull String name) {
    // A LONGNAME pattern has no single compiled regex; matched via matchesLongName.
    // For generic short-name callers (contains filter, highlighting) fall back to
    // its final segment -- the sub-pattern that matches the definition's own name.
    if (myMode == Mode.LONGNAME) return lastSegment().matches(name);
    return compiled().matcher(name).find();
  }

  /**
   * Long-name match ({@link Mode#LONGNAME} only): the final segment pattern must
   * match {@code shortName} (the ordinary {@code -ss} short-name test), and the
   * remaining leading segment patterns must match, in order, a left-to-right
   * subsequence of {@code prefixSegments} (the definition's enclosing module and
   * namespace path). So {@code A.B.C} keeps a definition whose short name matches
   * {@code C} and whose enclosing path has a segment matching {@code A} followed
   * (somewhere later) by one matching {@code B}. Given a definition's genuine
   * full name, this collapses to that single definition.
   */
  public boolean matchesLongName(@NotNull List<String> prefixSegments, @NotNull String shortName) {
    List<SymbolPattern> segments = segments();
    int m = segments.size();
    if (!lastSegment().matches(shortName)) return false;
    int pi = 0;
    for (int i = 0; i < prefixSegments.size() && pi < m - 1; i++) {
      if (segments.get(pi).matches(prefixSegments.get(i))) pi++;
    }
    return pi == m - 1;
  }

  /** Where a {@link Mode#LONGNAME} pattern matched, for highlighting (see {@link #longNameHighlights}). */
  public record LongNameHighlights(@NotNull List<int[]> shortNameRanges,
                                   @NotNull List<List<int[]>> prefixRanges) {}

  /**
   * The highlight companion to {@link #matchesLongName}: runs the identical
   * greedy match but records WHERE each segment pattern landed. Returns the char
   * ranges the final segment matched within {@code shortName}, plus a list
   * aligned 1:1 with {@code prefixSegments} giving the ranges each leading
   * segment pattern matched in the segment it was assigned to (an empty list for
   * every unassigned segment). Returns {@code null} exactly when
   * {@link #matchesLongName} would return {@code false}.
   */
  public @Nullable LongNameHighlights longNameHighlights(@NotNull List<String> prefixSegments,
                                                         @NotNull String shortName) {
    List<SymbolPattern> segs = segments();
    int m = segs.size();
    List<int[]> shortRanges = lastSegment().highlightRanges(shortName);
    if (shortRanges.isEmpty()) return null;   // final segment did not match the short name
    List<List<int[]>> prefixRanges = new ArrayList<>(prefixSegments.size());
    for (int i = 0; i < prefixSegments.size(); i++) prefixRanges.add(List.of());
    int pi = 0;
    for (int i = 0; i < prefixSegments.size() && pi < m - 1; i++) {
      List<int[]> rr = segs.get(pi).highlightRanges(prefixSegments.get(i));
      if (!rr.isEmpty()) {
        prefixRanges.set(i, rr);
        pi++;
      }
    }
    return pi == m - 1 ? new LongNameHighlights(shortRanges, prefixRanges) : null;
  }

  /**
   * Character ranges within {@code name} to highlight for a match, as
   * {@code [start, end)} pairs (empty if the pattern does not match). For a
   * humpback pattern the individual matched word-start characters are returned
   * (via the per-char capture groups added in {@link #buildHumpbackRegex}); for
   * every other mode the single overall match span is returned -- which is the
   * matched substring for literal/regex and the whole name for anchored glob.
   */
  public @NotNull List<int[]> highlightRanges(@NotNull String name) {
    // For a LONGNAME pattern only the short-name portion is highlightable, via
    // the final segment (the leading segments match the enclosing path, not the
    // printed short name).
    if (myMode == Mode.LONGNAME) return lastSegment().highlightRanges(name);
    Matcher m = compiled().matcher(name);
    if (!m.find()) return List.of();
    List<int[]> ranges = new ArrayList<>();
    if (myMode == Mode.HUMPBACK) {
      for (int g = 1; g <= m.groupCount(); g++) {
        int s = m.start(g), e = m.end(g);
        if (s >= 0 && e > s) ranges.add(new int[]{s, e});
      }
    }
    if (ranges.isEmpty()) ranges.add(new int[]{m.start(), m.end()});
    return ranges;
  }

  public @NotNull String source() {
    return mySource;
  }

  public @NotNull Mode mode() {
    return myMode;
  }

  /** The user-facing payload, with mode prefix stripped (so 're:foo' → 'foo'). */
  public @NotNull String body() {
    return switch (myMode) {
      case REGEX, HUMPBACK -> mySource.substring(3);
      case GLOB -> mySource.substring(5);
      case LITERAL, LONGNAME -> mySource;
    };
  }

  public static @NotNull SymbolPattern compile(@NotNull String pattern) {
    return compile(pattern, false);
  }

  /**
   * @param longNameMode when true a prefix-less dotted pattern that
   *   {@link #looksLikeQualifiedName looks like a qualified name} is compiled as
   *   a {@link Mode#LONGNAME} query (see {@link #matchesLongName}) instead of
   *   being rejected. Only {@code -ss} enables this; {@code glob:} / {@code re:}
   *   / {@code hb:} are recognized first and so keep their existing behavior.
   */
  public static @NotNull SymbolPattern compile(@NotNull String pattern, boolean longNameMode) {
    if (pattern.isEmpty()) {
      throw new IllegalArgumentException("Pattern is empty");
    }

    // Every non-regex mode is compiled case-insensitively; individual uppercase
    // letters are then pinned back to the exact case via #quoteSmartCase. That is
    // what makes matching smart-case -- a lowercase pattern letter matches
    // either case, an uppercase one matches uppercase only. re: is the sole
    // exception: it is matched exactly as typed (prepend (?i) for insensitivity).
    int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    if (pattern.startsWith("re:")) {
      String body = pattern.substring(3);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty re: pattern");
      try {
        return new SymbolPattern(Pattern.compile(body), pattern, Mode.REGEX);
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("Invalid regex: " + e.getMessage(), e);
      }
    }

    if (pattern.startsWith("hb:")) {
      String body = pattern.substring(3);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty hb: pattern");
      return new SymbolPattern(Pattern.compile(buildHumpbackRegex(body), flags), pattern, Mode.HUMPBACK);
    }

    if (pattern.startsWith("glob:")) {
      String body = pattern.substring(5);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty glob: pattern");
      return new SymbolPattern(Pattern.compile("^" + buildGlobRegex(body) + "$", flags), pattern, Mode.GLOB);
    }

    // A wildcard-free glob: is already an exact (anchored) match, so there is no
    // separate eq: mode; intercept an eq: prefix and redirect the user to glob:.
    if (pattern.startsWith("eq:")) {
      String body = pattern.substring(3);
      throw new IllegalArgumentException(
          "eq: mode was removed — use 'glob:" + body + "' for an exact (anchored) match"
              + " (glob: with no '*'/'?' matches the whole name; escape a literal star/question mark as \\*/\\?).");
    }

    // Long-name search: a dotted, prefix-less pattern (e.g. `Monoid.*-comm`) is a
    // by-part qualified-name query rather than an error. Reached only after the
    // mode prefixes above, so glob:/re:/hb: are never intercepted here.
    if (longNameMode && looksLikeQualifiedName(pattern)) {
      return compileLongName(pattern);
    }

    String trigger = firstNonIdentChar(pattern);
    if (trigger != null) {
      String globHint = pattern.replace(".*", "*").replace(".+", "*").replace(".?", "?");
      throw new IllegalArgumentException(
          trigger + " — try 're:" + pattern + "' (regex)"
              + (globHint.equals(pattern) ? "" : " or 'glob:" + globHint + "' (glob)"));
    }
    return compileLiteral(pattern);
  }

  /** A smart-case literal (default-mode) substring pattern. */
  private static SymbolPattern compileLiteral(@NotNull String pattern) {
    int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    return new SymbolPattern(Pattern.compile(smartCaseLiteral(pattern), flags), pattern, Mode.LITERAL);
  }

  /**
   * Builds a {@link Mode#LONGNAME} pattern from a dotted qualified name. Each
   * dot-separated segment becomes an ordinary literal sub-pattern; the caller
   * has verified via {@link #looksLikeQualifiedName} that every segment is a
   * non-empty run of Arend identifier characters, so no segment carries a mode
   * prefix or fails to compile.
   */
  private static SymbolPattern compileLongName(@NotNull String pattern) {
    List<SymbolPattern> segments = new ArrayList<>();
    for (String part : pattern.split("\\.")) segments.add(compileLiteral(part));
    return new SymbolPattern(pattern, segments);
  }

  /**
   * Catches plain patterns that can never match any Arend short name because
   * they contain a character outside the {@code ID} rule in {@code Arend.g4}.
   * That covers both regex mistakes ({@code .*}, {@code (?:}) and
   * qualified-name mistakes ({@code Module.Foo}, {@code foo(bar)}) -- silent
   * zero-match is a worse user experience than a fix-it.
   * Returns a human-readable trigger string (e.g. "contains '.*' (regex
   * sequence)"), or {@code null} if every character could appear in some
   * Arend identifier.
   */
  private static @Nullable String firstNonIdentChar(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (ArendNameCharSet.isIdChar(c)) continue;
      // Special-case the two most common shapes so the message points at
      // the sequence rather than just the leading character.
      if (c == '.' && i + 1 < s.length()) {
        char n = s.charAt(i + 1);
        if (n == '*' || n == '+' || n == '?') {
          return "contains '." + n + "' (regex sequence, not a name char)";
        }
      }
      if (c == '(' && i + 1 < s.length() && s.charAt(i + 1) == '?') {
        return "contains '(?' (regex sequence, not a name char)";
      }
      return "contains '" + c + "', which is not an Arend identifier character";
    }
    return null;
  }

  /**
   * True when {@code s} looks like a dotted qualified Arend name --
   * one-or-more identifier segments joined by {@code .} (e.g. {@code
   * RatField.finv_*}, {@code Algebra.Monoid.comm}). Callers that already
   * rejected {@code s} as a plain pattern can use this to distinguish the
   * "qualified name typed where short-name was expected" case from generic
   * regex / typo mistakes, and surface a more targeted hint.
   */
  public static boolean looksLikeQualifiedName(@NotNull String s) {
    if (s.indexOf('.') < 0) return false;
    int segStart = 0;
    for (int i = 0; i <= s.length(); i++) {
      if (i == s.length() || s.charAt(i) == '.') {
        if (i == segStart) return false;
        segStart = i + 1;
      } else if (!ArendNameCharSet.isIdChar(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Quotes a single pattern character for smart-case matching: an uppercase ASCII letter
   * matches uppercase only (wrapped in {@code (?-i:...)} to defeat the mode's global
   * {@link Pattern#CASE_INSENSITIVE} flag), every other character honors the flag. Shared
   * by literal, {@code glob:}, and {@code hb:} so smart-case is identical across them.
   */
  private static String quoteSmartCase(char c) {
    String q = Pattern.quote(String.valueOf(c));
    return (c >= 'A' && c <= 'Z') ? "(?-i:" + q + ")" : q;
  }

  /** A smart-case (see {@link #quoteSmartCase}) literal match of the whole string. */
  private static String smartCaseLiteral(String s) {
    StringBuilder sb = new StringBuilder(s.length() * 4);
    for (int i = 0; i < s.length(); i++) sb.append(quoteSmartCase(s.charAt(i)));
    return sb.toString();
  }

  /**
   * Translates a glob ({@code *}, {@code ?}, {@code \*} / {@code \?} for
   * literals) to a regex. Every other character is matched literally, smart-case
   * (see {@link #quoteSmartCase}).
   */
  private static String buildGlobRegex(String pattern) {
    StringBuilder sb = new StringBuilder(pattern.length() + 8);
    int i = 0;
    while (i < pattern.length()) {
      char c = pattern.charAt(i);
      if (c == '\\' && i + 1 < pattern.length()) {
        sb.append(quoteSmartCase(pattern.charAt(i + 1)));
        i += 2;
        continue;
      }
      if (c == '*') sb.append(".*");
      else if (c == '?') sb.append('.');
      else sb.append(quoteSmartCase(c));
      i++;
    }
    return sb.toString();
  }

  private static final String NON_PLAIN_START = "(?-i:(?=[^a-z']))";

  private static final String WORD_START =
      "(?:^|(?<=" + ArendNameCharSet.SEP_CHAR_CLASS + ")|(?<=-)(?<!_-)(?!_)|" + NON_PLAIN_START + ")";

  private static boolean isPlainChar(char c) {
    return (c >= 'a' && c <= 'z') || c == '\'';
  }

  private static String buildHumpbackRegex(String pattern) {
    StringBuilder sb = new StringBuilder(pattern.length() * 24);
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      // Wrap each char in a capturing group so its matched position can be recovered
      // for highlighting (#highlightRanges); WORD_START and the skip prefix add no
      // groups, so group g captures pattern char g-1. Emitted smart-case (#quoteSmartCase).
      String ch = "(" + quoteSmartCase(c) + ")";
      if (i == 0) {
        // The first char always anchors at a word start -- this is what keeps hb:
        // distinct from a substring search: 'hb:onoid' does NOT match 'Monoid'.
        sb.append(WORD_START).append(ch);
      } else if (isPlainChar(c)) {
        // A plain char may either skip ahead to the next word start (the optional
        // .*?WORD_START prefix) or continue the current word contiguously (prefix
        // omitted). So 'hb:ide' matches 'ide' and 'hb:posmon' matches 'PosetMonoid'.
        sb.append("(?:.*?").append(WORD_START).append(")?").append(ch);
      } else {
        // A non-plain char (uppercase, digit, operator) must land on a word start,
        // reached by skipping intervening chars; it is a word start itself, so a
        // contiguous option would be redundant and would blunt 'hb:PAM'.
        sb.append(".*?").append(WORD_START).append(ch);
      }
    }
    return sb.toString();
  }
}
