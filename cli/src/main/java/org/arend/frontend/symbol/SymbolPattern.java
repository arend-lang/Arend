package org.arend.frontend.symbol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles user-supplied symbol-search patterns into a {@link Pattern}.
 *
 * Default mode is a pure literal substring match (case-insensitive unless the
 * caller asks otherwise). All anchoring, wildcard, and regex behaviours are
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
 *   eq:<text>         exact short-name match (anchored on both ends)
 *   glob:<pat>        '*' = any chars, '?' = any one char.
 *                     Use '\*' / '\?' for literal stars / question marks.
 *   re:<java-regex>   raw Java regex, matched with find()
 *   hb:<chars>        humpback / boundary-aware fuzzy match,
 *                     e.g. 'hb:PMA' on 'PosetAddMonoid', 'hb:p-iP' on
 *                     'pi-isProp'. Word boundaries are case transitions and
 *                     any non-letter Arend identifier character (the
 *                     operator-class chars and '_').
 */
public final class SymbolPattern {
  public enum Mode { LITERAL, EQ, GLOB, REGEX, HUMPBACK }

  private final Pattern myCompiled;
  private final String mySource;
  private final Mode myMode;

  private SymbolPattern(Pattern compiled, String source, Mode mode) {
    myCompiled = compiled;
    mySource = source;
    myMode = mode;
  }

  public boolean matches(@NotNull String name) {
    return myCompiled.matcher(name).find();
  }

  public @NotNull String source() {
    return mySource;
  }

  public @NotNull Mode mode() {
    return myMode;
  }

  public @NotNull String compiledRegex() {
    return myCompiled.pattern();
  }

  /** The user-facing payload, with mode prefix stripped (so 're:foo' → 'foo'). */
  public @NotNull String body() {
    return switch (myMode) {
      case REGEX, EQ, HUMPBACK -> mySource.substring(3);
      case GLOB -> mySource.substring(5);
      case LITERAL -> mySource;
    };
  }

  public static @NotNull SymbolPattern compile(@NotNull String pattern, boolean caseSensitive) {
    if (pattern.isEmpty()) {
      throw new IllegalArgumentException("Pattern is empty");
    }

    int flags = caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    if (pattern.startsWith("re:")) {
      String body = pattern.substring(3);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty re: pattern");
      try {
        return new SymbolPattern(Pattern.compile(body, flags), pattern, Mode.REGEX);
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

    if (pattern.startsWith("eq:")) {
      String body = pattern.substring(3);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty eq: pattern");
      return new SymbolPattern(Pattern.compile("^" + Pattern.quote(body) + "$", flags), pattern, Mode.EQ);
    }

    String trigger = firstNonIdentChar(pattern);
    if (trigger != null) {
      String globHint = pattern.replace(".*", "*").replace(".+", "*").replace(".?", "?");
      throw new IllegalArgumentException(
          trigger + " — try 're:" + pattern + "' (regex)"
              + (globHint.equals(pattern) ? "" : " or 'glob:" + globHint + "' (glob)"));
    }
    return new SymbolPattern(Pattern.compile(Pattern.quote(pattern), flags), pattern, Mode.LITERAL);
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
   * Translates a glob ({@code *}, {@code ?}, {@code \*} / {@code \?} for
   * literals) to a regex. Every other character is matched literally.
   */
  private static String buildGlobRegex(String pattern) {
    StringBuilder sb = new StringBuilder(pattern.length() + 8);
    int i = 0;
    while (i < pattern.length()) {
      char c = pattern.charAt(i);
      if (c == '\\' && i + 1 < pattern.length()) {
        sb.append(Pattern.quote(String.valueOf(pattern.charAt(i + 1))));
        i += 2;
        continue;
      }
      if (c == '*') sb.append(".*");
      else if (c == '?') sb.append('.');
      else sb.append(Pattern.quote(String.valueOf(c)));
      i++;
    }
    return sb.toString();
  }

  /**
   * Word-boundary characters for humpback fuzzy matching: every non-letter
   * Arend identifier character (operators and underscore), drawn from the
   * same alphabet as {@link #isArendIdChar}. Apostrophe is deliberately
   * omitted -- it is a continuation char used as a primed-variant suffix
   * ({@code f'}, {@code x''}), not a word break. A match starts at a
   * boundary char OR after a lower-to-upper case transition.
   */
  private static final String BOUNDARY_CLASS = "[~!@#$%\\^&*\\-+=<>?/|:\\[\\]_]";

  private static String buildHumpbackRegex(String pattern) {
    StringBuilder sb = new StringBuilder(pattern.length() * 8);
    sb.append("(?:^|(?<=").append(BOUNDARY_CLASS).append(")|(?<=[a-z])(?=[A-Z]))");
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      sb.append(Pattern.quote(String.valueOf(c)));
      if (i < pattern.length() - 1) {
        // any chars, then either a boundary or a case transition before the next pattern char
        sb.append(".*?(?:").append(BOUNDARY_CLASS).append("|(?<=[a-z])(?=[A-Z]))");
      }
    }
    return sb.toString();
  }
}
