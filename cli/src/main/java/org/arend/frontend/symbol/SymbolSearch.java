package org.arend.frontend.symbol;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.server.ArendServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

/**
 * Drives a single {@code -ss} invocation: refresh the symbol index for every
 * library in scope, match it against the user pattern, and emit results.
 */
public final class SymbolSearch {
  // Same green as -ps uses to highlight matched sub-terms (ConsoleMain.ANSI_*).
  private static final String ANSI_GREEN = "\u001B[32m";
  private static final String ANSI_RESET = "\u001B[0m";

  public static final class Options {
    public int limit = 200;
    public final EnumSet<SymbolIndex.Kind> kinds = EnumSet.allOf(SymbolIndex.Kind.class);
    /** Extra AND substring filters (each a smart-case literal) applied after the pattern matches. */
    public final List<SymbolPattern> containsFilters = new ArrayList<>();
    /** Emit results as a JSON array instead of the human-readable listing. */
    public boolean json = false;
    /**
     * Library names to drop from the search scope. Set programmatically, not from a
     * user token: the REPL uses it to exclude its synthetic {@code Repl} library,
     * which mirrors the real libraries and would otherwise duplicate every hit.
     */
    public final Set<String> excludeLibraries = new HashSet<>();
  }

  public static int run(@NotNull List<String> patterns,
                        @NotNull Options options,
                        @NotNull LibraryManager libraryManager,
                        @NotNull ArendServer server,
                        @NotNull ErrorReporter errorReporter,
                        @NotNull PrintStream out) {
    if (patterns.isEmpty()) {
      System.err.println("[ERROR] -ss requires at least one pattern");
      return 0;
    }
    List<SymbolPattern> compiled = new ArrayList<>(patterns.size());
    for (String p : patterns) {
      try {
        // longNameMode=true: a dotted pattern like `Monoid.*-comm` is a long-name
        // query (matched by part), not an error -- see SymbolPattern.matchesLongName.
        compiled.add(SymbolPattern.compile(p, true));
      } catch (IllegalArgumentException e) {
        System.err.println("[ERROR] Bad -ss pattern '" + p + "': " + e.getMessage());
        return 0;
      }
    }

    warnAboutPipes(compiled);
    warnAboutLeadingApostrophe(compiled);
    // In JSON mode the query echo is diagnostic, not a result -- keep it off stdout.
    (options.json ? System.err : System.out).println(formatQueryEcho(compiled, options));

    List<SourceLibrary> libsInScope = librariesInScope(libraryManager, options);
    if (libsInScope.isEmpty()) {
      if (options.json) ResultJson.write(out, List.of(), 0);
      else System.out.println("No libraries to search.");
      return 0;
    }

    // Decompose each LITERAL pattern into "word parts" (alphanumeric runs split
    // on operator chars). When the main search finds nothing we surface a "did
    // you mean" list of names matching any of these parts, so a query like
    // 'natCoef_fromRat' that misses can still nudge the user toward 'natCoef'
    // and 'fromRat' hits without a second run. Only literal patterns are
    // decomposed; explicit re:/glob:/hb: stays exact.
    LinkedHashSet<String> suggestParts = collectWordParts(compiled);
    List<SymbolPattern> suggestPatterns = new ArrayList<>();
    for (String part : suggestParts) {
      try {
        suggestPatterns.add(SymbolPattern.compile(part));
      } catch (IllegalArgumentException ignored) {
        // a part may itself contain non-ident chars (e.g. apostrophe-only
        // fragments after a weird split) -- just skip those.
      }
    }

    List<Hit> hits = new ArrayList<>();
    List<Hit> suggestions = new ArrayList<>();
    for (SourceLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      // Re-parse only the modules whose cached mtime/size no longer matches the
      // source (or that aren't cached yet); the on-disk index serves the rest.
      // Across-process the server starts cold, so without this we'd parse every
      // .ard file every run. A format-version change invalidates the whole cache
      // (see SymbolIndex.FORMAT_HEADER), forcing a clean rebuild.
      for (ModulePath mp : lib.findModules(false)) {
        if (idx.isStale(lib, mp)) {
          server.findModule(mp, lib.getLibraryName(), false, false);
        }
      }
      idx.refresh(lib, server, false);
      idx.save();

      for (SymbolIndex.Entry e : idx.allEntries()) {
        if (!options.kinds.contains(e.kind())) continue;
        if (!matchesAllContains(options, e.shortName())) continue;
        if (matchesAny(compiled, e)) {
          hits.add(new Hit(lib.getLibraryName(), e));
        } else if (!suggestPatterns.isEmpty() && matchesAny(suggestPatterns, e)) {
          suggestions.add(new Hit(lib.getLibraryName(), e));
        }
      }
    }

    // Rank: shortest short-name first, so an exact-length name (which is the
    // best possible substring match for any literal query) always surfaces
    // above longer names that merely contain the query. Tie-break
    // alphabetically (case-insensitive) for determinism, then by library
    // name so identical short names group together.
    hits.sort(Comparator
        .<Hit>comparingInt(h -> h.entry.shortName().length())
        .thenComparing(h -> h.entry.shortName(), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(h -> h.libName));

    int total = hits.size();

    if (options.json) {
      writeJson(out, hits, libsInScope, libraryManager, options.limit, total);
      return total;
    }

    // Show the "library::" prefix only when more than one non-prelude library is
    // in scope; mirror the JSON output, which omits its `library` field there.
    boolean showLibrary = QualifiedName.showLibrary(libsInScope);

    int printed = 0;
    boolean truncated = false;
    StringBuilder sb = new StringBuilder();
    for (Hit h : hits) {
      if (options.limit > 0 && printed >= options.limit) {
        truncated = true;
        break;
      }
      appendEntry(sb, showLibrary, h.libName, h.entry, libraryManager, compiled);
      printed++;
    }

    out.print(sb);
    if (total == 0) {
      out.println("No matches.");
      printSuggestions(suggestions, suggestParts, showLibrary);
    } else {
      out.println();
      out.println("Found " + total + " match" + (total == 1 ? "" : "es")
          + (truncated ? " (showing " + printed + "; pass `limit=0` for all)" : ""));
    }
    return total;
  }

  /**
   * Splits each {@code LITERAL} pattern's body on non-alphanumeric Arend ID
   * chars (operators, underscore) and keeps the surviving alphanumeric runs of
   * length >= 3. Drops a part equal to the whole pattern (no decomposition
   * happened, so it would just re-run the failed search). Apostrophe is
   * stripped along with other separators -- a trailing {@code '} on a primed
   * variant is a name suffix, not a meaningful sub-token to search for.
   */
  private static LinkedHashSet<String> collectWordParts(List<SymbolPattern> patterns) {
    LinkedHashSet<String> parts = new LinkedHashSet<>();
    Set<String> originals = new HashSet<>();
    for (SymbolPattern p : patterns) {
      if (p.mode() != SymbolPattern.Mode.LITERAL) continue;
      String body = p.body();
      originals.add(body);
      for (String part : body.split("[^a-zA-Z0-9]+")) {
        if (part.length() >= 3) parts.add(part);
      }
    }
    parts.removeAll(originals);
    return parts;
  }

  private static void printSuggestions(List<Hit> suggestions, LinkedHashSet<String> parts, boolean showLibrary) {
    if (suggestions.isEmpty()) return;
    suggestions.sort(Comparator
        .<Hit>comparingInt(h -> h.entry.shortName().length())
        .thenComparing(h -> h.entry.shortName(), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(h -> h.libName));
    int cap = 8;
    int show = Math.min(suggestions.size(), cap);
    System.out.println();
    StringJoiner partList = new StringJoiner("', '", "'", "'");
    for (String p : parts) partList.add(p);
    System.out.println("Did you mean? (names containing word-parts of your query: " + partList + ")");
    for (int i = 0; i < show; i++) {
      Hit h = suggestions.get(i);
      System.out.println("  " + qualifiedName(showLibrary, h.libName, h.entry, h.entry.longName())
          + "  [" + h.entry.kind().name() + "]");
    }
    if (suggestions.size() > cap) {
      System.out.println("  ... and " + (suggestions.size() - cap) + " more "
          + "(re-run with a single word-part to see all).");
    }
  }

  private record Hit(String libName, SymbolIndex.Entry entry) {}

  /**
   * Soft-warn for every plain-mode pattern starting with {@code '}. The Arend
   * lexer treats {@code '} as a CONTINUATION-only character (no Arend short
   * name can start with it), so a leading apostrophe is almost always shell-
   * quoting bleed-through — e.g. {@code -ss "iabs_-' '-iabs"} where the user
   * meant two patterns but got the apostrophe glued onto the second one. The
   * substring search still works (it can match the middle of {@code foo'X}),
   * so we don't reject — just point out the most likely mistake.
   */
  private static void warnAboutLeadingApostrophe(List<SymbolPattern> patterns) {
    for (SymbolPattern p : patterns) {
      if (p.mode() == SymbolPattern.Mode.LITERAL && p.source().startsWith("'")) {
        System.err.println("[WARN] pattern '" + p.source()
            + "' starts with `'` — valid as a substring (matches inside foo'X),");
        System.err.println("       but no Arend short name can start with `'`. Likely shell-quoting"
            + " bleed-through;");
        System.err.println("       check the query echo line and re-quote if needed.");
      }
    }
  }

  /**
   * Soft-warn once if any plain-mode pattern contains '|'. '|' is a legitimate
   * Arend identifier character, so we cannot treat it as OR, but the user may
   * have expected grep-style alternation — call it out.
   */
  private static void warnAboutPipes(List<SymbolPattern> patterns) {
    for (SymbolPattern p : patterns) {
      if (p.mode() == SymbolPattern.Mode.LITERAL && p.source().indexOf('|') >= 0) {
        System.err.println("[WARN] '|' in pattern '" + p.source() + "' is matched literally, not as OR.");
        System.err.println("       For OR, use whitespace or multiple -ss flags: -ss \"A B C\" or -ss A -ss B -ss C.");
        return;
      }
    }
  }

  /**
   * Echoes how each pattern was interpreted so a misparse (e.g. literal
   * substring with spaces vs. OR'd tokens) shows up at a glance. Compact form
   * for short queries with no extra filters; multi-line block otherwise.
   */
  static String formatQueryEcho(List<SymbolPattern> patterns, Options opts) {
    String filters = describeFilters(opts);
    boolean compact = patterns.size() <= 3 && filters.isEmpty();
    StringBuilder sb = new StringBuilder();
    if (compact) {
      sb.append(patterns.size() == 1 ? "Searching for: " : "Searching for (OR): ");
      for (int i = 0; i < patterns.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append(describePattern(patterns.get(i)));
      }
    } else {
      sb.append("Searching for (OR):");
      for (SymbolPattern p : patterns) sb.append("\n  ").append(describePattern(p));
      if (!filters.isEmpty()) sb.append("\nFilters: ").append(filters);
    }
    return sb.toString();
  }

  private static String describePattern(SymbolPattern p) {
    String tag = switch (p.mode()) {
      case LITERAL -> "literal";
      case GLOB -> "glob";
      case REGEX -> "regex";
      case HUMPBACK -> "humpback";
      case LONGNAME -> "long-name";
    };
    String body = "'" + p.body() + "'";
    return switch (p.mode()) {
      case GLOB, HUMPBACK -> tag + " " + body + " → /" + p.compiledRegex() + "/";
      default -> tag + " " + body;
    };
  }

  private static String describeFilters(Options opts) {
    StringJoiner sj = new StringJoiner(", ");
    if (opts.limit != 200) sj.add("limit=" + opts.limit);
    for (SymbolPattern c : opts.containsFilters) sj.add("contains='" + c.body() + "'");
    if (opts.kinds.size() != SymbolIndex.Kind.values().length) {
      StringJoiner ks = new StringJoiner(",");
      for (SymbolIndex.Kind k : opts.kinds) ks.add(k.name().toLowerCase(Locale.ROOT));
      sj.add("kind=" + ks);
    }
    return sj.toString();
  }

  private static boolean matchesAny(List<SymbolPattern> patterns, SymbolIndex.Entry e) {
    List<String> prefix = null;   // lazily built; only long-name patterns need it
    for (SymbolPattern p : patterns) {
      if (p.mode() == SymbolPattern.Mode.LONGNAME) {
        if (prefix == null) prefix = prefixSegments(e);
        if (p.matchesLongName(prefix, e.shortName())) return true;
      } else if (p.matches(e.shortName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The enclosing module + namespace path of an entry, as a segment list: the
   * module-path segments followed by the in-module long-name segments, minus the
   * final segment (the definition's own short name). This is what the leading
   * segments of a {@link SymbolPattern.Mode#LONGNAME} query match against.
   */
  private static List<String> prefixSegments(SymbolIndex.Entry e) {
    List<String> segs = new ArrayList<>(e.modulePath().toList());
    for (String s : e.longName().split("\\.")) if (!s.isEmpty()) segs.add(s);
    if (!segs.isEmpty()) segs.remove(segs.size() - 1);   // drop the short name
    return segs;
  }

  private static boolean matchesAllContains(Options opts, String shortName) {
    for (SymbolPattern needle : opts.containsFilters) {
      if (!needle.matches(shortName)) return false;
    }
    return true;
  }

  /** Adapts a {@link SymbolIndex.Entry} to the shared {@link QualifiedName#format} label. */
  private static String qualifiedName(boolean showLibrary, String libName,
                                      SymbolIndex.Entry e, String renderedLongName) {
    return QualifiedName.format(showLibrary, libName, e.modulePath().toString(), renderedLongName);
  }

  private static void appendEntry(StringBuilder out, boolean showLibrary, String libName, SymbolIndex.Entry e,
                                  LibraryManager libraryManager, List<SymbolPattern> patterns) {
    String header;
    if (e.absoluteFile() == null || e.absoluteFile().isEmpty()) {
      header = "<" + libName + ":" + e.modulePath() + ">";
    } else {
      header = PathDisplay.shorten(e.absoluteFile(), libraryManager)
          + ":" + (e.line() == 0 ? "?" : e.line()) + ":" + (e.column() == 0 ? "?" : e.column());
    }
    out.append(header).append('\n');
    out.append(renderQualifiedName(showLibrary, libName, e, patterns))
        .append("  [").append(e.kind().name()).append("]\n");
    if (!e.signature().isEmpty()) {
      // A container signature (\class/\record/\data) is multi-line; indent every
      // line by two spaces so the field/constructor lines nest under the header.
      out.append("  ").append(e.signature().replace("\n", "\n  ")).append('\n');
    }
    out.append('\n');
  }

  /**
   * Renders {@code [library::]module:LongName} with every query-matched part
   * wrapped in ANSI green (the colour {@code -ps} uses for matched sub-terms).
   *
   * <p>For ordinary patterns only the matched span of the SHORT name (the
   * trailing segment of the long name) lights up. For a
   * {@link SymbolPattern.Mode#LONGNAME} pattern EVERY matched segment lights up
   * -- the short name plus the enclosing module / namespace segments its leading
   * parts matched -- so the whole matched long name is highlighted. Ranges from
   * all OR-ed patterns are merged. Only reached on the non-JSON path.
   */
  private static String renderQualifiedName(boolean showLibrary, String libName,
                                            SymbolIndex.Entry e, List<SymbolPattern> patterns) {
    String moduleStr = e.modulePath().toString();
    String longNameStr = e.longName();
    String shortName = e.shortName();

    List<int[]> moduleRanges = new ArrayList<>();
    List<int[]> longNameRanges = new ArrayList<>();

    // Segment offsets are needed only when a LONGNAME pattern is present.
    List<String> moduleSegs = null;
    int[] moduleOffsets = null, longOffsets = null;

    for (SymbolPattern p : patterns) {
      if (p.mode() == SymbolPattern.Mode.LONGNAME) {
        if (moduleSegs == null) {
          moduleSegs = e.modulePath().toList();
          moduleOffsets = segmentOffsets(moduleSegs);
          longOffsets = segmentOffsets(splitDot(longNameStr));
        }
        List<String> prefix = prefixSegments(e);
        SymbolPattern.LongNameHighlights hl = p.longNameHighlights(prefix, shortName);
        if (hl == null) continue;   // this OR-ed pattern is not the one that matched
        // Final segment -> the short name (trailing segment of the long name).
        int shortOff = shortNameOffset(longNameStr, shortName);
        if (shortOff >= 0) {
          for (int[] r : hl.shortNameRanges()) longNameRanges.add(new int[]{shortOff + r[0], shortOff + r[1]});
        }
        // Leading segments -> the module path or the long name's leading segments, by index.
        int msz = moduleSegs.size();
        List<List<int[]>> prefixRanges = hl.prefixRanges();
        for (int i = 0; i < prefixRanges.size(); i++) {
          List<int[]> rr = prefixRanges.get(i);
          if (rr.isEmpty()) continue;
          if (i < msz) {
            for (int[] r : rr) moduleRanges.add(new int[]{moduleOffsets[i] + r[0], moduleOffsets[i] + r[1]});
          } else {
            int j = i - msz;   // index into the long name's leading segments
            for (int[] r : rr) longNameRanges.add(new int[]{longOffsets[j] + r[0], longOffsets[j] + r[1]});
          }
        }
      } else {
        int off = shortNameOffset(longNameStr, shortName);
        if (off < 0) continue;
        for (int[] r : p.highlightRanges(shortName)) longNameRanges.add(new int[]{off + r[0], off + r[1]});
      }
    }

    return QualifiedName.format(showLibrary, libName,
        applyHighlights(moduleStr, moduleRanges), applyHighlights(longNameStr, longNameRanges));
  }

  /** Position of the trailing short name within its long name (-1 if absent). */
  private static int shortNameOffset(String longName, String shortName) {
    return longName.endsWith(shortName) ? longName.length() - shortName.length()
                                        : longName.lastIndexOf(shortName);
  }

  /** Splits a dot-joined name into segments; empty string -> empty list. */
  private static List<String> splitDot(String s) {
    List<String> segs = new ArrayList<>();
    if (!s.isEmpty()) Collections.addAll(segs, s.split("\\."));
    return segs;
  }

  /** Char offset of each segment within {@code segs} joined by '.'. */
  private static int[] segmentOffsets(List<String> segs) {
    int[] offs = new int[segs.size()];
    int pos = 0;
    for (int i = 0; i < segs.size(); i++) {
      offs[i] = pos;
      pos += segs.get(i).length() + 1;   // + 1 for the '.' separator
    }
    return offs;
  }

  /** Wraps each {@code [start,end)} range of {@code s} in ANSI green; ranges sorted, overlaps clamped. */
  private static String applyHighlights(String s, List<int[]> ranges) {
    if (ranges.isEmpty()) return s;
    ranges.sort(Comparator.comparingInt(r -> r[0]));
    StringBuilder sb = new StringBuilder(s.length() + 16);
    int cur = 0;
    for (int[] r : ranges) {
      if (r[1] <= cur) continue;               // fully covered by an earlier range
      int start = Math.max(r[0], cur);
      if (start > cur) sb.append(s, cur, start);
      sb.append(ANSI_GREEN).append(s, start, r[1]).append(ANSI_RESET);
      cur = r[1];
    }
    sb.append(s, cur, s.length());
    return sb.toString();
  }

  /**
   * Emits the ranked hits via {@link ResultJson}: {@code {"results": [...],
   * "count": N}}. Each result has fields {@code library} (omitted when only one
   * non-prelude library is in scope), {@code module}, {@code name} (the long
   * name), {@code kind}, {@code signature} (omitted when empty) and a
   * {@code location} object {@code {"file", "line", "col"}} (its {@code file}
   * omitted for generated modules). {@code results} honours {@code limit};
   * {@code count} is the total number of matches before truncation.
   */
  private static void writeJson(PrintStream out, List<Hit> hits, List<SourceLibrary> libsInScope,
                                LibraryManager libraryManager, int limit, int total) {
    boolean omitLibrary = !QualifiedName.showLibrary(libsInScope);
    int shown = (limit > 0) ? Math.min(hits.size(), limit) : hits.size();
    List<ResultJson.Row> rows = new ArrayList<>(shown);
    for (int i = 0; i < shown; i++) {
      Hit h = hits.get(i);
      SymbolIndex.Entry e = h.entry;
      String file = (e.absoluteFile() == null || e.absoluteFile().isEmpty())
          ? null : PathDisplay.shorten(e.absoluteFile(), libraryManager);
      rows.add(new ResultJson.Row(
          omitLibrary ? null : h.libName,
          e.modulePath().toString(),
          e.longName(),
          e.kind().name(),
          e.signature().isEmpty() ? null : e.signature(),
          null,            // -ss reports a full signature, never an "expression" slice
          file,
          e.line(),
          e.column()));
    }
    ResultJson.write(out, rows, total);
  }

  /**
   * Search scope = every library registered with the manager, minus any listed in
   * {@link Options#excludeLibraries}. That set is normally the requested libraries
   * plus their transitive dependencies and prelude, so scope is controlled by
   * choosing what to load; the exclude-list only removes synthetic libraries (the
   * REPL's {@code Repl} mirror).
   */
  private static List<SourceLibrary> librariesInScope(LibraryManager manager, Options opts) {
    List<SourceLibrary> all = new ArrayList<>();
    for (String name : manager.getLibraries()) {
      if (opts.excludeLibraries.contains(name)) continue;
      SourceLibrary lib = manager.getLibrary(name);
      if (lib != null) all.add(lib);
    }
    return all;
  }

  /**
   * Parses sub-tokens passed alongside {@code -ss}, e.g.
   * {@code -ss Monoid limit=50 kind=class,instance}.
   * Any token that doesn't look like an option is treated as a pattern; multiple
   * patterns are OR-ed at match time.
   */
  public static @Nullable Parsed parseArgs(String[] args, ErrorReporter errorReporter) {
    Options opts = new Options();
    List<String> patterns = new ArrayList<>();
    for (String arg : args) {
      if (arg.equals("case-sensitive")) {
        // Retired: matching is now always smart-case (lowercase matches either
        // case, uppercase is exact). Warn instead of silently searching for the
        // literal token "case-sensitive", and point at re: for full case control.
        System.err.println("[WARN] 'case-sensitive' is no longer a -ss option — matching is smart-case"
            + " (lowercase matches either case, an uppercase letter is exact); use re: for full case control. Ignoring.");
      }
      else if (arg.startsWith("limit=")) {
        try { opts.limit = Integer.parseInt(arg.substring("limit=".length())); }
        catch (NumberFormatException e) {
          System.err.println("[ERROR] Bad -ss limit: " + arg);
          return null;
        }
      } else if (arg.startsWith("contains=")) {
        String v = arg.substring("contains=".length());
        if (!v.isEmpty()) {
          try {
            opts.containsFilters.add(SymbolPattern.compile(v));
          } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] Bad -ss contains filter '" + v + "': " + e.getMessage());
            return null;
          }
        }
      } else if (arg.startsWith("kind=")) {
        EnumSet<SymbolIndex.Kind> ks = EnumSet.noneOf(SymbolIndex.Kind.class);
        for (String name : arg.substring("kind=".length()).split(",")) {
          SymbolIndex.Kind k = parseKind(name.trim());
          if (k == null) {
            System.err.println("[ERROR] Unknown -ss kind: " + name);
            return null;
          }
          ks.add(k);
        }
        if (!ks.isEmpty()) opts.kinds.retainAll(ks);
      } else {
        // Whitespace inside one -ss arg splits into multiple OR'd patterns,
        // so `-ss "A B C"` is equivalent to `-ss A -ss B -ss C`. Catches the
        // common "I thought quoted spaces meant OR" mistake that used to
        // match the literal string with spaces and return zero hits.
        for (String tok : arg.trim().split("\\s+")) {
          if (tok.isEmpty()) continue;
          if (tok.equals("-ss")) {
            System.err.println("[ERROR] '-ss' inside quoted argument '" + arg
                + "' — pass each pattern as a separate -ss flag");
            return null;
          }
          patterns.add(tok);
        }
      }
    }
    if (patterns.isEmpty()) {
      System.err.println("[ERROR] -ss requires at least one pattern");
      return null;
    }
    return new Parsed(patterns, opts);
  }

  public record Parsed(List<String> patterns, Options options) {}

  private static @Nullable SymbolIndex.Kind parseKind(String s) {
    return switch (s.toLowerCase(Locale.ROOT)) {
      case "func", "function" -> SymbolIndex.Kind.FUNCTION;
      case "sfunc" -> SymbolIndex.Kind.SFUNC;
      case "lemma" -> SymbolIndex.Kind.LEMMA;
      case "type" -> SymbolIndex.Kind.TYPE;
      case "instance" -> SymbolIndex.Kind.INSTANCE;
      case "coclause" -> SymbolIndex.Kind.COCLAUSE;
      case "coerce" -> SymbolIndex.Kind.COERCE;
      case "level" -> SymbolIndex.Kind.LEVEL;
      case "axiom" -> SymbolIndex.Kind.AXIOM;
      case "data" -> SymbolIndex.Kind.DATA;
      case "cons", "constructor" -> SymbolIndex.Kind.CONSTRUCTOR;
      case "class" -> SymbolIndex.Kind.CLASS;
      case "record" -> SymbolIndex.Kind.RECORD;
      case "field" -> SymbolIndex.Kind.FIELD;
      case "meta" -> SymbolIndex.Kind.META;
      case "other" -> SymbolIndex.Kind.OTHER;
      default -> null;
    };
  }
}
