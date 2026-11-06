package org.arend.frontend.query.guide;

import org.arend.ext.module.ModulePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One page of the guide: the markdown file {@code <guide>/A/B/C.md}, which describes module
 * {@code A.B.C}. The format it reads is the one the guide is written in:
 *
 * <pre>
 * ### A.B.C
 *
 * Overview paragraphs, {@code ####} section headings ...
 *
 * - **`Name`**: description
 * - **`Name1`**, **`Name2`** (alias **`n`**): description shared by several definitions
 *   - an indented line continues the entry above
 * </pre>
 *
 * An entry's header is its leading run of bold code spans, which may be separated by
 * {@code ,} / {@code /} / {@code and} and interleaved with parenthesised notes; it ends at the
 * first {@code :}. A name written with parameters ({@code SFunc V B}) is its first word. Every
 * line that is not part of an entry is overview text. Directory {@code README.md} pages describe
 * files rather than definitions and are not pages in this sense.
 */
final class GuidePage {
  /**
   * One entry.
   *
   * @param line    1-based line of the entry's first line
   * @param endLine 1-based line of its last continuation line ({@code == line} when it has none)
   * @param names   the names in its header, as written
   * @param text    its description: the header's remainder plus continuation lines
   */
  record Entry(int line, int endLine, List<String> names, String text) {}

  private static final Pattern ENTRY_START = Pattern.compile("^(\\s*)- \\*\\*`");
  private static final Pattern BOLD_CODE = Pattern.compile("\\*\\*`([^`]+)`\\*\\*");
  private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
  private static final Pattern TOKEN_SEPARATORS = Pattern.compile("[\\s(){}\\[\\],;]+");

  final Path file;
  /** The page's path relative to the guide root, with {@code /} separators. */
  final String relativePath;
  final ModulePath modulePath;
  /** The text of the {@code ###} heading on the first line, or null when there is none. */
  final @Nullable String heading;
  final List<Entry> entries;
  final String overview;
  /** The page's lines as read, for edits that rewrite it. */
  final List<String> lines;
  /** The line separator the file uses, so a rewrite keeps it. */
  final String lineSeparator;

  private GuidePage(Path file, String relativePath, ModulePath modulePath, @Nullable String heading,
                    List<Entry> entries, String overview, List<String> lines, String lineSeparator) {
    this.file = file;
    this.relativePath = relativePath;
    this.modulePath = modulePath;
    this.heading = heading;
    this.entries = entries;
    this.overview = overview;
    this.lines = lines;
    this.lineSeparator = lineSeparator;
  }

  /** The page's path relative to the guide root for {@code module}: {@code A/B/C.md}. */
  static String relativePathOf(ModulePath module) {
    return String.join("/", module.toList()) + ".md";
  }

  /** Every page under {@code root}, in path order. Hidden directories and READMEs are skipped. */
  static List<GuidePage> readAll(Path root) throws IOException {
    List<Path> files;
    try (var stream = Files.walk(root)) {
      files = stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md"))
          .filter(p -> !p.getFileName().toString().equals("README.md"))
          .filter(p -> !isHidden(root.relativize(p)))
          .sorted()
          .toList();
    }
    List<GuidePage> pages = new ArrayList<>(files.size());
    for (Path f : files) pages.add(parse(root, f));
    return pages;
  }

  private static boolean isHidden(Path relative) {
    for (Path part : relative) {
      if (part.toString().startsWith(".")) return true;
    }
    return false;
  }

  static GuidePage parse(Path root, Path file) throws IOException {
    String relative = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
    String withoutExt = relative.substring(0, relative.length() - ".md".length());
    ModulePath modulePath = new ModulePath(List.of(withoutExt.split("/")));
    String content = Files.readString(file, StandardCharsets.UTF_8);
    String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
    List<String> lines = content.lines().toList();

    String heading = null;
    List<Entry> entries = new ArrayList<>();
    StringBuilder overview = new StringBuilder();
    // The entry being read, as a mutable builder: first and last line, names, text, indent.
    int curLine = -1, curEnd = -1, curIndent = -1;
    List<String> curNames = null;
    StringBuilder curText = null;

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (i == 0 && line.startsWith("### ")) {
        heading = line.substring(4).trim();
        continue;
      }
      Matcher start = ENTRY_START.matcher(line);
      if (start.find()) {
        if (curNames != null) entries.add(new Entry(curLine, curEnd, curNames, curText.toString().trim()));
        int[] textStart = {0};
        String rest = line.substring(start.end() - 3); // keep the leading **`
        curNames = headerNames(rest, textStart);
        curText = new StringBuilder(rest.substring(textStart[0]));
        curLine = curEnd = i + 1;
        curIndent = start.group(1).length();
        continue;
      }
      if (curNames != null && !line.isBlank() && indentOf(line) > curIndent) {
        curText.append('\n').append(line.trim());
        curEnd = i + 1;
        continue;
      }
      if (curNames != null) {
        entries.add(new Entry(curLine, curEnd, curNames, curText.toString().trim()));
        curNames = null;
        curText = null;
      }
      overview.append(line).append('\n');
    }
    if (curNames != null) entries.add(new Entry(curLine, curEnd, curNames, curText.toString().trim()));
    return new GuidePage(file, relative, modulePath, heading, entries, overview.toString().trim(), lines, lineSeparator);
  }

  private static int indentOf(String line) {
    int n = 0;
    while (n < line.length() && Character.isWhitespace(line.charAt(n))) n++;
    return n;
  }

  /**
   * The names in an entry header. {@code rest} starts at the first {@code **`}; on return
   * {@code textStart[0]} is the index where the description starts.
   */
  static List<String> headerNames(String rest, int[] textStart) {
    List<String> names = new ArrayList<>();
    int i = 0;
    int n = rest.length();
    while (i < n) {
      char c = rest.charAt(i);
      if (rest.startsWith("**`", i)) {
        int end = rest.indexOf("`**", i + 3);
        if (end < 0) break;
        // One bold span may hold several code spans: **`implies` / `-->`**. Split at the
        // backticks, the even parts are inside code spans (names, even one called `and`) and the
        // odd parts are what separates them.
        String[] parts = rest.substring(i + 3, end).split("`", -1);
        for (int k = 0; k < parts.length; k += 2) {
          if (!parts[k].isBlank()) addName(names, parts[k].trim());
        }
        i = end + 3;
      } else if (c == '(') {
        int close = closingParen(rest, i);
        if (close < 0) break;
        Matcher m = BOLD_CODE.matcher(rest.substring(i, close));
        while (m.find()) addName(names, m.group(1));
        i = close + 1;
      } else if (c == ',' || c == '/' || c == '&' || Character.isWhitespace(c)) {
        i++;
      } else if (rest.startsWith("and ", i) || rest.startsWith("or ", i)) {
        i += rest.charAt(i) == 'a' ? 4 : 3;
      } else {
        if (c == ':') i++;
        break;
      }
    }
    textStart[0] = Math.min(i, n);
    return names;
  }

  /** The {@code )} closing the {@code (} at {@code open}, ignoring parentheses inside code spans. */
  private static int closingParen(String s, int open) {
    boolean inCode = false;
    int depth = 0;
    for (int i = open; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '`') inCode = !inCode;
      else if (!inCode && c == '(') depth++;
      else if (!inCode && c == ')' && --depth == 0) return i;
    }
    return -1;
  }

  private static void addName(List<String> names, String written) {
    for (String word : written.trim().split("\\s+")) {
      // `\instance Foo` names Foo; a leading keyword is not part of the name.
      if (word.isEmpty() || word.startsWith("\\")) continue;
      if (!names.contains(word)) names.add(word);
      return;
    }
  }

  private static final String[] SEPARATORS = {", ", " / ", " and ", " or "};

  /** Where the bold span naming {@code name} starts and ends in {@code line}, or null. */
  private static int @Nullable [] boldSpan(String line, String name) {
    for (String open : new String[] {"**`" + name + "`**", "**`" + name + " "}) {
      int start = line.indexOf(open);
      if (start < 0) continue;
      int end = line.indexOf("`**", start + 3);
      if (end >= 0) return new int[] {start, end + 3};
    }
    return null;
  }

  /**
   * An entry's first line with {@code name} dropped from its header, together with one separator,
   * or null when the header cannot lose it cleanly (it is the only name, or not found).
   */
  static @Nullable String withoutHeaderName(String line, String name) {
    int[] span = boldSpan(line, name);
    if (span == null) return null;
    for (String sep : SEPARATORS) {
      if (line.startsWith(sep + "**`", span[1])) return line.substring(0, span[0]) + line.substring(span[1] + sep.length());
    }
    for (String sep : SEPARATORS) {
      if (span[0] >= sep.length() && line.startsWith(sep, span[0] - sep.length())) {
        return line.substring(0, span[0] - sep.length()) + line.substring(span[1]);
      }
    }
    return null;
  }

  /** An entry's first line with the bold name {@code name} renamed to {@code replacement}, or null when not found. */
  static @Nullable String withHeaderNameReplaced(String line, String name, String replacement) {
    int[] span = boldSpan(line, name);
    if (span == null) return null;
    String bold = line.substring(span[0], span[1]);
    return line.substring(0, span[0]) + bold.replaceFirst(java.util.regex.Pattern.quote("`" + name), java.util.regex.Matcher.quoteReplacement("`" + replacement)) + line.substring(span[1]);
  }

  /** Every word inside a code span of {@code text}: the candidates for mentions of definitions. */
  static Set<String> codeTokens(@NotNull String text) {
    Set<String> tokens = new LinkedHashSet<>();
    Matcher m = CODE_SPAN.matcher(text);
    while (m.find()) {
      for (String token : TOKEN_SEPARATORS.split(m.group(1))) {
        if (!token.isEmpty()) tokens.add(token);
      }
    }
    return tokens;
  }
}
