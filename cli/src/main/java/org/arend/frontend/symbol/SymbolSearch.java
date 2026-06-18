package org.arend.frontend.symbol;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.server.ArendServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Drives a single {@code -ss} invocation: refresh the symbol index for every
 * library in scope, match it against the user pattern, and emit results.
 */
public final class SymbolSearch {
  public static final class Options {
    public boolean caseSensitive = false;
    public boolean noCache = false;
    public int limit = 200;
    public final EnumSet<SymbolIndex.Kind> kinds = EnumSet.allOf(SymbolIndex.Kind.class);
    /** {@code null} = all loaded libraries; otherwise the explicit allow-list. */
    public @Nullable Set<String> onlyLibraries = null;
    /** Extra AND substring filters applied after the pattern matches. */
    public final List<String> containsFilters = new ArrayList<>();
  }

  /**
   * @param requestedLibraries the libraries explicitly named on the CLI; used as
   *                           the seed for the search scope (and to compute
   *                           {@code only=self}).
   */
  public static int run(@NotNull List<String> patterns,
                        @NotNull Options options,
                        @NotNull List<SourceLibrary> requestedLibraries,
                        @NotNull LibraryManager libraryManager,
                        @NotNull ArendServer server,
                        @NotNull ErrorReporter errorReporter) {
    if (patterns.isEmpty()) {
      System.err.println("[ERROR] -ss requires at least one pattern");
      return 0;
    }
    List<SymbolPattern> compiled = new ArrayList<>(patterns.size());
    for (String p : patterns) {
      try {
        compiled.add(SymbolPattern.compile(p, options.caseSensitive));
      } catch (IllegalArgumentException e) {
        System.err.println("[ERROR] Bad -ss pattern '" + p + "': " + e.getMessage());
        if (SymbolPattern.looksLikeQualifiedName(p)) {
          int firstDot = p.indexOf('.');
          int lastDot = p.lastIndexOf('.');
          String first = p.substring(0, firstDot);
          String last = p.substring(lastDot + 1);
          System.err.println(
              "        note: -ss matches SHORT names only; long names like '"
                  + p + "' are printed in the output.");
          System.err.println(
              "        try:  -ss '" + last + "'        # find by short name");
          System.err.println(
              "              -ch '" + first + "'        # browse the class hierarchy of '" + first + "'");
        }
        return 0;
      }
    }

    warnAboutPipes(compiled);
    warnAboutLeadingApostrophe(compiled);
    System.out.println(formatQueryEcho(compiled, options));

    List<SourceLibrary> libsInScope = librariesInScope(requestedLibraries, libraryManager, options);
    if (libsInScope.isEmpty()) {
      System.out.println("No libraries to search.");
      return 0;
    }

    // Decompose each LITERAL pattern into "word parts" (alphanumeric runs split
    // on operator chars). When the main search finds nothing we surface a "did
    // you mean" list of names matching any of these parts, so a query like
    // 'natCoef_fromRat' that misses can still nudge the user toward 'natCoef'
    // and 'fromRat' hits without a second run. Only literal patterns are
    // decomposed; explicit eq:/re:/glob:/hb: stays exact.
    LinkedHashSet<String> suggestParts = collectWordParts(compiled);
    List<SymbolPattern> suggestPatterns = new ArrayList<>();
    for (String part : suggestParts) {
      try {
        suggestPatterns.add(SymbolPattern.compile(part, options.caseSensitive));
      } catch (IllegalArgumentException ignored) {
        // a part may itself contain non-ident chars (e.g. apostrophe-only
        // fragments after a weird split) -- just skip those.
      }
    }

    List<Hit> hits = new ArrayList<>();
    List<Hit> suggestions = new ArrayList<>();
    for (SourceLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      // Trigger raw parsing only for modules whose cached mtime is stale
      // (or that aren't cached yet). Across-process, the server starts cold,
      // so without this we'd parse every .ard file every run -- the on-disk
      // index buys us a real speedup only when we skip those parses too.
      for (ModulePath mp : lib.findModules(false)) {
        if (options.noCache || idx.isStale(lib, mp)) {
          server.findModule(mp, lib.getLibraryName(), false, false);
        }
      }
      idx.refresh(lib, server, options.noCache);
      idx.save();

      for (SymbolIndex.Entry e : idx.allEntries()) {
        if (!options.kinds.contains(e.kind())) continue;
        if (!matchesAllContains(options, e.shortName())) continue;
        if (matchesAny(compiled, e.shortName())) {
          hits.add(new Hit(lib.getLibraryName(), e));
        } else if (!suggestPatterns.isEmpty() && matchesAny(suggestPatterns, e.shortName())) {
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
    int printed = 0;
    boolean truncated = false;
    StringBuilder out = new StringBuilder();
    for (Hit h : hits) {
      if (options.limit > 0 && printed >= options.limit) {
        truncated = true;
        break;
      }
      appendEntry(out, h.libName, h.entry, libraryManager);
      printed++;
    }

    System.out.print(out);
    if (total == 0) {
      System.out.println("No matches.");
      printSuggestions(suggestions, suggestParts);
    } else {
      System.out.println();
      System.out.println("Found " + total + " match" + (total == 1 ? "" : "es")
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

  private static void printSuggestions(List<Hit> suggestions, LinkedHashSet<String> parts) {
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
      System.out.println("  " + h.libName + "::" + h.entry.longName()
          + "  [" + h.entry.kind().name() + "]");
    }
    if (suggestions.size() > cap) {
      System.out.println("  ... and " + (suggestions.size() - cap) + " more "
          + "(re-run with a single word-part to see all).");
    }
  }

  private record Hit(String libName, SymbolIndex.Entry entry) {}

  /**
   * Build (or refresh) the on-disk symbol index for every library in scope and
   * exit. Replaces the older "no-op {@code -ss zzznevermatch >/dev/null}" idiom.
   */
  public static void reindex(@NotNull List<SourceLibrary> requestedLibraries,
                             @NotNull LibraryManager libraryManager,
                             @NotNull ArendServer server,
                             @Nullable Set<String> onlyLibraries) {
    reindex(requestedLibraries, libraryManager, server, onlyLibraries, null);
  }

  /**
   * Variant that routes informational output through an {@link org.arend.frontend.cli.ai.AiOutputRouter}.
   * The router parameter is nullable for callers that still write directly to stdout.
   */
  public static void reindex(@NotNull List<SourceLibrary> requestedLibraries,
                             @NotNull LibraryManager libraryManager,
                             @NotNull ArendServer server,
                             @Nullable Set<String> onlyLibraries,
                             @Nullable org.arend.frontend.cli.ai.AiOutputRouter router) {
    Options opts = new Options();
    opts.onlyLibraries = onlyLibraries;
    List<SourceLibrary> libsInScope = librariesInScope(requestedLibraries, libraryManager, opts);
    if (libsInScope.isEmpty()) {
      info(router, "No libraries to index.");
      return;
    }
    for (SourceLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      int rebuilt = 0;
      for (ModulePath mp : lib.findModules(false)) {
        if (idx.isStale(lib, mp)) {
          server.findModule(mp, lib.getLibraryName(), false, false);
          rebuilt++;
        }
      }
      idx.refresh(lib, server, false);
      idx.save();
      info(router, lib.getLibraryName() + ": indexed (" + rebuilt
          + " stale module" + (rebuilt == 1 ? "" : "s") + " re-parsed).");
    }
  }

  private static void info(@Nullable org.arend.frontend.cli.ai.AiOutputRouter router, String line) {
    if (router != null) router.info(line);
    else System.out.println(line);
  }

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
      case EQ -> "exact";
      case GLOB -> "glob";
      case REGEX -> "regex";
      case HUMPBACK -> "humpback";
    };
    String body = "'" + p.body() + "'";
    return switch (p.mode()) {
      case GLOB, HUMPBACK -> tag + " " + body + " → /" + p.compiledRegex() + "/";
      default -> tag + " " + body;
    };
  }

  private static String describeFilters(Options opts) {
    StringJoiner sj = new StringJoiner(", ");
    if (opts.caseSensitive) sj.add("case-sensitive");
    if (opts.noCache) sj.add("no-cache");
    if (opts.limit != 200) sj.add("limit=" + opts.limit);
    for (String c : opts.containsFilters) sj.add("contains='" + c + "'");
    if (opts.kinds.size() != SymbolIndex.Kind.values().length) {
      StringJoiner ks = new StringJoiner(",");
      for (SymbolIndex.Kind k : opts.kinds) ks.add(k.name().toLowerCase(Locale.ROOT));
      sj.add("kind=" + ks);
    }
    if (opts.onlyLibraries != null) {
      StringJoiner ls = new StringJoiner(",");
      for (String s : opts.onlyLibraries) ls.add(s);
      sj.add("only=" + ls);
    }
    return sj.toString();
  }

  private static boolean matchesAny(List<SymbolPattern> patterns, String shortName) {
    for (SymbolPattern p : patterns) if (p.matches(shortName)) return true;
    return false;
  }

  private static boolean matchesAllContains(Options opts, String shortName) {
    if (opts.containsFilters.isEmpty()) return true;
    String hay = opts.caseSensitive ? shortName : shortName.toLowerCase(Locale.ROOT);
    for (String needle : opts.containsFilters) {
      String n = opts.caseSensitive ? needle : needle.toLowerCase(Locale.ROOT);
      if (!hay.contains(n)) return false;
    }
    return true;
  }

  private static void appendEntry(StringBuilder out, String libName, SymbolIndex.Entry e, LibraryManager libraryManager) {
    String header;
    if (e.absoluteFile() == null || e.absoluteFile().isEmpty()) {
      header = "<" + libName + ":" + e.modulePath() + ">";
    } else {
      header = PathDisplay.shorten(e.absoluteFile(), libraryManager)
          + ":" + (e.line() == 0 ? "?" : e.line()) + ":" + (e.column() == 0 ? "?" : e.column());
    }
    out.append(header).append('\n');
    out.append(libName).append("::").append(e.longName()).append("  [").append(e.kind().name()).append("]\n");
    if (!e.signature().isEmpty()) {
      out.append("  ").append(e.signature()).append('\n');
    }
    out.append('\n');
  }

  /**
   * Default scope = every library currently registered with the manager (so
   * dependencies and prelude come along too). {@code only=...} narrows it.
   */
  private static List<SourceLibrary> librariesInScope(List<SourceLibrary> requested,
                                                      LibraryManager manager,
                                                      Options opts) {
    List<SourceLibrary> all = new ArrayList<>();
    for (String name : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(name);
      if (lib != null) all.add(lib);
    }
    if (opts.onlyLibraries == null) return all;

    Set<String> allow = new HashSet<>();
    for (String s : opts.onlyLibraries) {
      if ("self".equalsIgnoreCase(s)) {
        for (SourceLibrary l : requested) allow.add(l.getLibraryName());
      } else {
        allow.add(s);
      }
    }
    List<SourceLibrary> filtered = new ArrayList<>();
    for (SourceLibrary lib : all) if (allow.contains(lib.getLibraryName())) filtered.add(lib);
    return filtered;
  }

  /**
   * Parses sub-tokens passed alongside {@code -ss}, e.g.
   * {@code -ss Monoid case-sensitive limit=50 only=arend-lib kind=class,instance}.
   * Any token that doesn't look like an option is treated as a pattern; multiple
   * patterns are OR-ed at match time.
   */
  public static @Nullable Parsed parseArgs(String[] args, ErrorReporter errorReporter) {
    Options opts = new Options();
    List<String> patterns = new ArrayList<>();
    for (String arg : args) {
      if (arg.equals("case-sensitive")) opts.caseSensitive = true;
      else if (arg.equals("no-cache")) opts.noCache = true;
      else if (arg.startsWith("limit=")) {
        try { opts.limit = Integer.parseInt(arg.substring("limit=".length())); }
        catch (NumberFormatException e) {
          System.err.println("[ERROR] Bad -ss limit: " + arg);
          return null;
        }
      } else if (arg.startsWith("contains=")) {
        String v = arg.substring("contains=".length());
        if (!v.isEmpty()) opts.containsFilters.add(v);
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
      } else if (arg.startsWith("only=")) {
        if (opts.onlyLibraries == null) opts.onlyLibraries = new HashSet<>();
        for (String s : arg.substring("only=".length()).split(",")) {
          if (!s.isEmpty()) opts.onlyLibraries.add(s.trim());
        }
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
