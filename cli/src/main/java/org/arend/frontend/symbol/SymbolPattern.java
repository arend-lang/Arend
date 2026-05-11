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
 * opt-in via prefixes, because {@code ^}, {@code $}, {@code *}, {@code ?}, and
 * many other ASCII punctuation characters are valid inside Arend identifier
 * names ({@code *-comm}, {@code ^-1}, {@code &lt;*}, ...). Without that opt-in,
 * "find a definition whose name contains *-comm" should just work.
 *
 * Grammar:
 *   <text>            literal substring match (default). Rejected if the text
 *                     contains a regex sequence like '.*', '.+', '.?', or
 *                     '(?...', since those almost never appear in real names.
 *   lit:<text>        literal substring, bypassing the regex-look check.
 *   eq:<text>         exact full-name match
 *   glob:<pat>        '*' = any chars, '?' = any one char.
 *                     Use '\*' / '\?' for literal stars / question marks.
 *   re:<java-regex>   raw Java regex, matched with find()
 *   hb:<chars>        humpback / boundary-aware fuzzy match,
 *                     e.g. 'hb:PMA' on 'PosetAddMonoid', 'hb:p-iP' on
 *                     'pi-isProp'. Word boundaries are case transitions and
 *                     any of -_<>+*=^~/'.\!@#$%&|:; characters.
 */
public final class SymbolPattern {
  public enum Mode { LITERAL, LIT, EQ, GLOB, REGEX, HUMPBACK }

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
      case LIT -> mySource.substring(4);
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

    if (pattern.startsWith("lit:")) {
      String body = pattern.substring(4);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty lit: pattern");
      return new SymbolPattern(Pattern.compile(Pattern.quote(body), flags), pattern, Mode.LIT);
    }

    String trigger = looksLikeRegex(pattern);
    if (trigger != null) {
      String globHint = pattern.replace(".*", "*").replace(".+", "*").replace(".?", "?");
      throw new IllegalArgumentException(
          "looks like a regex (" + trigger + ") — try 're:" + pattern + "' (regex)"
              + (globHint.equals(pattern) ? "" : ", 'glob:" + globHint + "' (glob)")
              + ", or 'lit:" + pattern + "' (force literal)");
    }
    return new SymbolPattern(Pattern.compile(Pattern.quote(pattern), flags), pattern, Mode.LITERAL);
  }

  /**
   * Spots regex sequences that almost certainly aren't part of a real Arend
   * identifier, so the user gets a fix-it instead of a silent zero-match.
   * Deliberately narrow: isolated metachars like '*', '+', '?', '^', '$', '|'
   * are common in operator names ({@code *-comm}, {@code BigSum_+}, etc.) and
   * stay literal.
   */
  private static @Nullable String looksLikeRegex(String s) {
    for (int i = 0; i + 1 < s.length(); i++) {
      char c = s.charAt(i);
      char n = s.charAt(i + 1);
      if (c == '.' && (n == '*' || n == '+' || n == '?')) {
        return "contains '." + n + "'";
      }
      if (c == '(' && n == '?') {
        return "contains '(?'";
      }
    }
    return null;
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
   * Word boundary characters used by Arend names: dashes, underscores, math
   * operators, primes, dots. A match starts here, OR after a lower-to-upper
   * case transition.
   */
  private static final String BOUNDARY_CLASS = "[-_<>+*=^~/'.\\\\!@#$%&|:;]";

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
