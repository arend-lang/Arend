package org.arend.frontend.symbol;

import org.jetbrains.annotations.NotNull;

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
 *   <text>            literal substring match (default)
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
  private final Pattern myCompiled;
  private final String mySource;

  private SymbolPattern(Pattern compiled, String source) {
    myCompiled = compiled;
    mySource = source;
  }

  public boolean matches(@NotNull String name) {
    return myCompiled.matcher(name).find();
  }

  public @NotNull String source() {
    return mySource;
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
        return new SymbolPattern(Pattern.compile(body, flags), pattern);
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("Invalid regex: " + e.getMessage(), e);
      }
    }

    if (pattern.startsWith("hb:")) {
      String body = pattern.substring(3);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty hb: pattern");
      return new SymbolPattern(Pattern.compile(buildHumpbackRegex(body), flags), pattern);
    }

    if (pattern.startsWith("glob:")) {
      String body = pattern.substring(5);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty glob: pattern");
      return new SymbolPattern(Pattern.compile("^" + buildGlobRegex(body) + "$", flags), pattern);
    }

    if (pattern.startsWith("eq:")) {
      String body = pattern.substring(3);
      if (body.isEmpty()) throw new IllegalArgumentException("Empty eq: pattern");
      return new SymbolPattern(Pattern.compile("^" + Pattern.quote(body) + "$", flags), pattern);
    }

    return new SymbolPattern(Pattern.compile(Pattern.quote(pattern), flags), pattern);
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
