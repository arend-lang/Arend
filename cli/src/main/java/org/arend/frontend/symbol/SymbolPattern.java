package org.arend.frontend.symbol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles user-supplied symbol-search patterns into a {@link Pattern}.
 *
 * Default mode is a pure literal substring match with smart case: a lowercase
 * pattern character matches either case, an uppercase pattern character matches
 * uppercase only (identical to the humpback rule, and applied to every non-regex
 * mode). There is no global case toggle -- for full case control, including
 * forcing a lowercase-exact match, drop to {@code re:}, which matches exactly as
 * typed. All anchoring, wildcard, and regex behaviours are
 * opt-in via prefixes, because every Arend identifier character (per the
 * {@code ID} rule in {@code Arend.g4}) must match literally:
 * {@code ~ ! @ # $ % ^ & * - + = < > ? / | : [ ]}, ASCII letters and
 * underscore, Unicode math operators (U+2200..U+22FF, U+2A00..U+2AFF), and --
 * after the first character -- digits and {@code '}. Without that opt-in,
 * "find a definition whose name contains *-comm" should just work.
 *
 * Conversely, anything outside that alphabet ({@code .}, parentheses, braces,
 * comma, semicolon, quote, backslash, backtick, whitespace) cannot appear in
 * any Arend short name, so a plain pattern containing such a character can
 * only be a mistake -- we reject it with a fix-it instead of silently
 * matching nothing.
 *
 * Grammar:
 *   <text>            literal substring match (default). Rejected if it
 *                     contains a character that is NOT an Arend identifier
 *                     character (most commonly '.', '(', ')', '{', '}',
 *                     covering both regex sequences like '.*' / '(?...' and
 *                     qualified-name mistakes like 'Module.Foo').
 *   glob:<pat>        anchored whole-name match. '*' = any chars, '?' = any one
 *                     char. With no '*'/'?' this is an EXACT match; to match a
 *                     literal '*'/'?' (both are valid Arend identifier chars)
 *                     escape it as '\*' / '\?' -- '\' never occurs in an Arend
 *                     name, so there is no ambiguity. (Replaces the old eq:.)
 *   re:<java-regex>   raw Java regex, matched with find(); case-sensitive
 *                     exactly as typed (prepend {@code (?i)} for insensitivity)
 *   hb:<chars>        humpback / boundary-aware fuzzy match. The first pattern
 *                     char, and every UPPERCASE letter / digit / operator, must
 *                     land on a char that STARTS a word, e.g. 'hb:PAM' on
 *                     'PosetAddMonoid' (Poset·Add·Monoid) or 'hb:piP' on
 *                     'pi-isProp' (pi·isProp — supply the word-start letters,
 *                     not the '-' separator). A lowercase pattern letter is more
 *                     lenient: it matches a word start OR continues the current
 *                     word contiguously, so you can type whole word prefixes --
 *                     'hb:ide' matches 'ide', 'hb:posmon' matches 'PosetMonoid'.
 *                     (Boundary-anchoring the first char is what keeps hb:
 *                     distinct from a plain substring: 'hb:onoid' will not match
 *                     'Monoid'.) A word starts at every non-plain
 *                     character -- an uppercase letter, a digit, or an
 *                     operator/symbol -- so a capital run splits ('HLevels' =
 *                     H·Levels) and an embedded digit splits ('log1p' =
 *                     log·1p), and also after any separator char. A '-' glued
 *                     to a '_' does not split off a following lowercase word
 *                     ('abs_-left' = abs·-left), but a non-plain char always
 *                     starts a word, so 'HLevel_-1' = HLevel·-·1 and 'hb:HL-2s'
 *                     matches 'HLevels_-2-sigma'. Boundary detection is
 *                     always case-sensitive (uppercase is case-based). Smart
 *                     case on the pattern letters (the same rule as every other
 *                     non-regex mode): an uppercase letter matches uppercase
 *                     only, a lowercase letter matches either case -- so
 *                     'hb:PAM' needs capital P·A·M while 'hb:pam' also matches
 *                     a lowercase 'p_a_m' name.
 */
public final class SymbolPattern {
  public enum Mode { LITERAL, GLOB, REGEX, HUMPBACK, LONGNAME }

  private final Pattern myCompiled;
  private final String mySource;
  private final Mode myMode;
  /** Per-segment sub-patterns; non-null only for {@link Mode#LONGNAME} (see {@link #matchesLongName}). */
  private final List<SymbolPattern> mySegments;

  private SymbolPattern(Pattern compiled, String source, Mode mode) {
    myCompiled = compiled;
    mySource = source;
    myMode = mode;
    mySegments = null;
  }

  private SymbolPattern(String source, List<SymbolPattern> segments) {
    myCompiled = null;
    mySource = source;
    myMode = Mode.LONGNAME;
    mySegments = segments;
  }

  private SymbolPattern lastSegment() {
    return mySegments.get(mySegments.size() - 1);
  }

  public boolean matches(@NotNull String name) {
    // A LONGNAME pattern has no single compiled regex; matched via matchesLongName.
    // For generic short-name callers (contains filter, highlighting) fall back to
    // its final segment -- the sub-pattern that matches the definition's own name.
    if (myMode == Mode.LONGNAME) return lastSegment().matches(name);
    return myCompiled.matcher(name).find();
  }

  /**
   * Long-name match ({@link Mode#LONGNAME} only): the final segment pattern must
   * match {@code shortName} (the ordinary {@code -ss} short-name test), and the
   * remaining leading segment patterns must match, in order, a left-to-right
   * subsequence of {@code prefixSegments} (the definition's enclosing module +
   * namespace path). So {@code A.B.C} keeps a definition whose short name matches
   * {@code C} and whose enclosing path has a segment matching {@code A} followed
   * (somewhere later) by one matching {@code B}. Given a definition's genuine
   * full name this collapses to that single definition.
   */
  public boolean matchesLongName(@NotNull List<String> prefixSegments, @NotNull String shortName) {
    int m = mySegments.size();
    if (!lastSegment().matches(shortName)) return false;
    int pi = 0;   // next leading segment pattern to place, over mySegments[0 .. m-2]
    for (int i = 0; i < prefixSegments.size() && pi < m - 1; i++) {
      if (mySegments.get(pi).matches(prefixSegments.get(i))) pi++;
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
    int m = mySegments.size();
    List<int[]> shortRanges = lastSegment().highlightRanges(shortName);
    if (shortRanges.isEmpty()) return null;   // final segment did not match the short name
    List<List<int[]>> prefixRanges = new ArrayList<>(prefixSegments.size());
    for (int i = 0; i < prefixSegments.size(); i++) prefixRanges.add(List.of());
    int pi = 0;
    for (int i = 0; i < prefixSegments.size() && pi < m - 1; i++) {
      List<int[]> rr = mySegments.get(pi).highlightRanges(prefixSegments.get(i));
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
   * matched substring for literal/regex and the whole name for anchored glob:.
   */
  public @NotNull List<int[]> highlightRanges(@NotNull String name) {
    // For a LONGNAME pattern only the short-name portion is highlightable, via
    // the final segment (the leading segments match the enclosing path, not the
    // printed short name).
    if (myMode == Mode.LONGNAME) return lastSegment().highlightRanges(name);
    Matcher m = myCompiled.matcher(name);
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

  public @NotNull String compiledRegex() {
    return myCompiled == null ? "" : myCompiled.pattern();
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
   *   / {@code hb:} are recognised first and so keep their existing behaviour.
   */
  public static @NotNull SymbolPattern compile(@NotNull String pattern, boolean longNameMode) {
    if (pattern.isEmpty()) {
      throw new IllegalArgumentException("Pattern is empty");
    }

    // Every non-regex mode is compiled case-insensitively; individual uppercase
    // letters are then pinned back to exact case via #quoteSmartCase. That is
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

    // eq: was retired: an anchored, wildcard-free glob: is the same exact match.
    // Point the user there rather than silently searching for the literal "eq:...".
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
   *
   * Returns a human-readable trigger string (e.g. "contains '.*' (regex
   * sequence)"), or {@code null} if every character could appear in some
   * Arend identifier.
   */
  private static @Nullable String firstNonIdentChar(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (isArendIdChar(c)) continue;
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
      } else if (!isArendIdChar(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Mirrors {@code Arend.g4}'s {@code ID} rule: every character a short name
   * can contain, including the continuation-only digits and apostrophe (we
   * don't enforce first-vs-rest here because substring patterns don't care).
   */
  private static boolean isArendIdChar(char c) {
    if (c >= 'a' && c <= 'z') return true;
    if (c >= 'A' && c <= 'Z') return true;
    if (c >= '0' && c <= '9') return true;
    if (c == '_' || c == '\'') return true;
    if (c >= 0x2200 && c <= 0x22FF) return true;
    if (c >= 0x2A00 && c <= 0x2AFF) return true;
    return switch (c) {
      case '~', '!', '@', '#', '$', '%', '^', '&', '*',
           '-', '+', '=', '<', '>', '?', '/', '|', ':',
           '[', ']' -> true;
      default -> false;
    };
  }

  /**
   * Quotes a single pattern character for smart-case matching: an uppercase
   * ASCII letter is wrapped in {@code (?-i:...)} so the mode's global
   * {@link Pattern#CASE_INSENSITIVE} flag cannot fold it down to lowercase
   * (uppercase matches uppercase only); every other character -- a lowercase
   * letter, digit, or operator -- is quoted plainly and so honours the flag
   * (lowercase matches either case, and case-less chars are unaffected). Shared
   * by literal, {@code glob:}, and {@code hb:} so smart case is identical
   * across every non-regex mode.
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

  /**
   * Separator characters that break a word for humpback matching, EXCLUDING
   * {@code -}. Every non-letter Arend identifier char (operators and
   * underscore) drawn from the same alphabet as {@link #isArendIdChar}, minus
   * {@code -} -- which needs the context-sensitive treatment in
   * {@link #WORD_START} and so is handled by its own clause there. Apostrophe
   * is deliberately omitted too -- it is a continuation char used as a
   * primed-variant suffix ({@code f'}, {@code x''}), not a word break.
   */
  private static final String SEP_SIMPLE = "[~!@#$%\\^&*+=<>?/|:\\[\\]_]";

  /**
   * Start-of-word by "non-plain" character: EVERY character that is not a
   * lowercase letter or apostrophe -- an uppercase letter, a digit, or an
   * operator/symbol char -- begins its own word. So a capital run splits
   * ({@code HLevels} = H·Levels, {@code IOError} = I·O·Error) and an embedded
   * digit splits ({@code log1p} = log·1p, {@code expm1} = exp·m·1). Matched
   * zero-width via a lookahead at that char. Wrapped in {@code (?-i:...)} so
   * the {@code a-z} negation stays case-sensitive even when the whole pattern
   * is compiled with {@link Pattern#CASE_INSENSITIVE}; without the guard case
   * folding turns {@code [^a-z']} into "not a letter", which drops uppercase
   * out of the class and lets every lowercase position start a word, so {@code
   * hb:PAM} degrades into a plain {@code p..a..m} subsequence search. The
   * pattern LETTERS still fold with the global flag (see
   * {@link #buildHumpbackRegex}); only boundary detection is pinned to real
   * case. Apostrophe is excluded because it is a primed-variant suffix
   * ({@code f'}, {@code x''}), a continuation, not a word start. A non-plain
   * char starts a word UNCONDITIONALLY -- even glued right after a
   * {@code _}-demoted {@code -} -- so the digit in {@code HLevel_-1} /
   * {@code HLevels_-2-sigma} is its own word ({@code hb:H1} and {@code hb:HL-2s}
   * both match). The {@code _}-vs-{@code -} adjacency only affects a following
   * lowercase LETTER -- see {@link #WORD_START}.
   */
  private static final String NONPLAIN_START = "(?-i:(?=[^a-z']))";

  /**
   * Zero-width assertion that the current position STARTS a word: at the
   * string start, right after a {@link #SEP_SIMPLE} separator, right after a
   * {@code -} that is a real separator, or at a {@link #NONPLAIN_START
   * non-plain character}. The {@code -} exception encodes arend-lib's
   * convention that {@code _} is the token separator and a {@code -} adjacent
   * to it glues onto the neighbouring token rather than separating a following
   * lowercase word: {@code abs_-left} is abs·-left (the {@code left} does NOT
   * split off), {@code o-_Equiv} is o-·Equiv. The {@code (?<!_-)(?!_)} guard
   * lives ONLY on this {@code -} clause, which is what makes a following
   * lowercase letter a word start; a {@code -} not adjacent to {@code _} (e.g.
   * {@code 2-pi}, {@code abs-comm}) still separates. This guard does NOT apply
   * to {@link #NONPLAIN_START}: an uppercase letter, digit, or symbol starts a
   * word wherever it appears, so {@code HLevel_-1} is HLevel·-·1 and
   * {@code hb:H1} / {@code hb:HL-2s} match.
   */
  private static final String WORD_START =
      "(?:^|(?<=" + SEP_SIMPLE + ")|(?<=-)(?<!_-)(?!_)|" + NONPLAIN_START + ")";

  /**
   * A "plain" character is one that does NOT start a word: a lowercase ASCII
   * letter or an apostrophe. This is exactly the complement of the
   * {@code [^a-z']} class used by {@link #NONPLAIN_START}. Only plain pattern
   * chars are allowed to continue a word contiguously in
   * {@link #buildHumpbackRegex}; every other char (uppercase letter, digit,
   * operator) is itself a word start and stays boundary-anchored.
   */
  private static boolean isPlainChar(char c) {
    return (c >= 'a' && c <= 'z') || c == '\'';
  }

  private static String buildHumpbackRegex(String pattern) {
    StringBuilder sb = new StringBuilder(pattern.length() * 24);
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      // Each char is wrapped in a capturing group so the exact matched positions
      // can be recovered for highlighting (see #highlightRanges). The char itself
      // is emitted smart-case (see #quoteSmartCase): an uppercase letter matches
      // uppercase only, a lowercase letter matches either case. WORD_START and
      // the skip prefix contribute no capturing groups, so group g captures
      // pattern char g-1 exactly.
      String ch = "(" + quoteSmartCase(c) + ")";
      if (i == 0) {
        // The first char always anchors at a word start -- this is what keeps
        // hb: distinct from a plain substring search: 'hb:onoid' does NOT match
        // 'Monoid' (o is mid-word), whereas literal 'onoid' does.
        sb.append(WORD_START).append(ch);
      } else if (isPlainChar(c)) {
        // A plain pattern char may EITHER skip to the next word start (the
        // optional .*?WORD_START prefix) OR continue the current word
        // contiguously (prefix omitted -- it matches the position right after
        // the previous char). So 'hb:ide' matches 'ide' (i at a word start, then
        // d,e continuing) and 'hb:posmon' matches 'PosetMonoid' (the 'pos' and
        // 'mon' word prefixes). The skip branch is exactly the old strict rule,
        // so every pre-change match still matches -- this only ADDS matches.
        sb.append("(?:.*?").append(WORD_START).append(")?").append(ch);
      } else {
        // A non-plain pattern char (uppercase, digit, operator) must still land
        // on a word start, reached by skipping intervening chars. Such chars are
        // word starts themselves, so a contiguous option would be redundant;
        // keeping them strict preserves the precision of 'hb:PAM'.
        sb.append(".*?").append(WORD_START).append(ch);
      }
    }
    return sb.toString();
  }
}
