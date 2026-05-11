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
        return 0;
      }
    }

    warnAboutPipes(compiled);
    System.out.println(formatQueryEcho(compiled, options));

    List<SourceLibrary> libsInScope = librariesInScope(requestedLibraries, libraryManager, options);
    if (libsInScope.isEmpty()) {
      System.out.println("No libraries to search.");
      return 0;
    }

    int total = 0;
    int printed = 0;
    boolean truncated = false;
    StringBuilder out = new StringBuilder();

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
        if (!matchesAny(compiled, e.shortName())) continue;
        if (!matchesAllContains(options, e.shortName())) continue;
        total++;
        if (options.limit > 0 && printed >= options.limit) {
          truncated = true;
          continue;
        }
        appendEntry(out, lib.getLibraryName(), e);
        printed++;
      }
    }

    System.out.print(out);
    if (total == 0) {
      System.out.println("No matches.");
    } else {
      System.out.println();
      System.out.println("Found " + total + " match" + (total == 1 ? "" : "es")
          + (truncated ? " (showing " + printed + "; pass `limit=0` for all)" : ""));
    }
    return total;
  }

  /**
   * Build (or refresh) the on-disk symbol index for every library in scope and
   * exit. Replaces the older "no-op {@code -ss zzznevermatch >/dev/null}" idiom.
   */
  public static void reindex(@NotNull List<SourceLibrary> requestedLibraries,
                             @NotNull LibraryManager libraryManager,
                             @NotNull ArendServer server,
                             @Nullable Set<String> onlyLibraries) {
    Options opts = new Options();
    opts.onlyLibraries = onlyLibraries;
    List<SourceLibrary> libsInScope = librariesInScope(requestedLibraries, libraryManager, opts);
    if (libsInScope.isEmpty()) {
      System.out.println("No libraries to index.");
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
      System.out.println(lib.getLibraryName() + ": indexed (" + rebuilt
          + " stale module" + (rebuilt == 1 ? "" : "s") + " re-parsed).");
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
      case LIT -> "literal";
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

  private static void appendEntry(StringBuilder out, String libName, SymbolIndex.Entry e) {
    String header;
    if (e.absoluteFile() == null || e.absoluteFile().isEmpty()) {
      header = "<" + libName + ":" + e.modulePath() + ">";
    } else {
      header = e.absoluteFile() + ":" + (e.line() == 0 ? "?" : e.line()) + ":" + (e.column() == 0 ? "?" : e.column());
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
