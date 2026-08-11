package org.arend.frontend.query.symbolsearch;

import org.arend.frontend.ConsoleHelp;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.*;
import org.arend.repl.Repl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

/**
 * Drives a single {@code -ss} invocation: refresh the symbol index for every
 * library in scope, match it against the user pattern, and emit results.
 */
public final class SymbolSearchTool extends ConsoleQueryTool {
  /** Singleton shared by the CLI (its {@code -ss} flag) and the REPL (its {@code :ss} command). */
  public static final SymbolSearchTool INSTANCE = new SymbolSearchTool();
  private SymbolSearchTool() {}

  @Override public String shortName() { return "ss"; }

  @Override public String longName() { return "symbol-search"; }

  @Override public String argName() { return "name-pattern"; }

  @Override public String cliDescription() {
    return "search by short name (uses an mtime-cached on-disk index). Pass `-ss --help` for the full grammar.";
  }
  @Override public void printHelp() { ConsoleHelp.printSymbolSearch(); }

  // ---- REPL command (:ss / :symbol-search) --------------------------------

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Search registered libraries for definitions by short name (`:? ss` for the full grammar)";
  }

  @Override
  public @Nls @NotNull String help(@NotNull Repl api) {
    return ConsoleHelp.symbolSearchReplHelp();
  }

  @Override protected String replLabel() { return "Symbol search"; }

  public static final class Options extends QueryOptions {
    public final EnumSet<SymbolIndex.Kind> kinds = EnumSet.allOf(SymbolIndex.Kind.class);
    /** True once an explicit {@code kind=} filter has narrowed {@link #kinds}: the first
     *  {@code kind=} replaces the all-kinds default, later ones union onto it. */
    public boolean kindFilterSet = false;
  }

  /**
   * Soft-warn for every plain-mode pattern starting with {@code '}: no Arend short name can
   * (the lexer treats {@code '} as continuation-only), so it is almost always shell-quoting
   * bleed-through. Substring search still works, so we warn rather than reject.
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
   * Echoes how each pattern was interpreted so a misparse (e.g., literal
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
    return tag + " '" + p.body() + "'";
  }

  private static String describeFilters(Options opts) {
    StringJoiner sj = new StringJoiner(", ");
    if (opts.limit != 200) sj.add("limit=" + opts.limit);
    if (opts.kinds.size() != SymbolIndex.Kind.values().length) {
      StringJoiner ks = new StringJoiner(",");
      for (SymbolIndex.Kind k : opts.kinds) ks.add(k.name().toLowerCase(Locale.ROOT));
      sj.add("kind=" + ks);
    }
    return sj.toString();
  }

  /**
   * Parses sub-tokens passed alongside {@code -ss}, e.g.
   * {@code -ss Monoid limit=50 kind=class,instance}.
   * Any token that doesn't look like an option is treated as a pattern; multiple
   * patterns are OR-ed at match time.
   */
  @Override
  public @Nullable SymbolSearchTool.ToolRunner parseArgs(String[] args) {
    Options opts = new Options();
    List<String> patterns = new ArrayList<>();
    boolean ok = new QueryArgs.Tokens()
        // Intercept the unsupported `case-sensitive` token: matching is smart-case,
        // so warn rather than silently treat it as a literal search pattern.
        .flag("case-sensitive", () -> System.err.println(
            "[WARN] 'case-sensitive' is no longer a -ss option — matching is smart-case"
                + " (lowercase matches either case, an uppercase letter is exact); use re: for full case control. Ignoring."))
        .limit(opts)
        .param("kind", v -> {
          EnumSet<SymbolIndex.Kind> ks = EnumSet.noneOf(SymbolIndex.Kind.class);
          for (String name : v.split(",")) {
            SymbolIndex.Kind k = parseKind(name.trim());
            if (k == null) throw new QueryArgs.ArgError("Unknown kind: " + name);
            ks.add(k);
          }
          if (!ks.isEmpty()) {
            // The first kind= replaces the all-kinds default; repeated kind= args union
            // onto it (so `kind=class kind=record` keeps BOTH, matching the OR-of-patterns
            // model) instead of intersecting the disjoint sets down to nothing.
            if (!opts.kindFilterSet) { opts.kinds.clear(); opts.kindFilterSet = true; }
            opts.kinds.addAll(ks);
          }
        })
        // Restrict to the requested (top-level) libraries — skip dependencies.
        .self(opts)
        // Whitespace inside one -ss arg splits into multiple OR'd patterns, so
        // `-ss "A B C"` is equivalent to `-ss A -ss B -ss C` — matching the common
        // expectation that quoted spaces mean OR, not a literal space in the name.
        .positional(arg -> {
          for (String tok : arg.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            if (tok.equals("-ss")) {
              throw new QueryArgs.ArgError("'-ss' inside quoted argument '" + arg
                  + "' — pass each pattern as a separate -ss flag");
            }
            patterns.add(tok);
          }
        })
        .parse("-ss", args);
    if (!ok) return null;
    if (patterns.isEmpty()) {
      System.err.println("[ERROR] -ss requires at least one pattern");
      return null;
    }
    return new ToolRunner(patterns, opts);
  }

  public record ToolRunner(List<String> patterns, Options options) implements ConsoleToolRunner {
    @Override public int run(QueryContext ctx) {
      ctx.applyTo(options);
      try {
        LibraryManager libraryManager = ctx.libraryManager();
        PrintStream out = ctx.out();
        List<SymbolPattern> compiled = new ArrayList<>(patterns.size());
        for (String p : patterns) {
          try {
            // longNameMode=true: a dotted pattern like `Monoid.*-comm` is a long-name
            // query (matched by part), not an error -- see SymbolPattern.matchesLongName.
            compiled.add(SymbolPattern.compile(p, true));
          } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] Bad -ss pattern '" + p + "': " + e.getMessage());
            if (options.json) ResultJson.write(out, List.of(), 0);
            return 1;
          }
        }

        warnAboutPipes(compiled);
        warnAboutLeadingApostrophe(compiled);
        // In JSON mode the query echo is diagnostic, not a result -- keep it off stdout.
        (options.json ? System.err : System.out).println(formatQueryEcho(compiled, options));

        List<SourceLibrary> libsInScope = ctx.searchLibraries(options.self);
        if (libsInScope.isEmpty()) {
          if (options.json) ResultJson.write(out, List.of(), 0);
          else System.err.println("[ERROR] No libraries to search.");
          return 1;
        }

        SymbolSearchEngine.Result result =
            SymbolSearchEngine.find(compiled, options.kinds, libsInScope, ctx.server());

        // Show the "library::" prefix only when more than one non-prelude library is in
        // scope; mirror the JSON output, which omits its `library` field there.
        boolean showLibrary = QualifiedName.containsMultipleNonPreludeLibraries(libsInScope);
        SymbolSearchPrinter printer = options.json
            ? new JsonSymbolSearchPrinter()
            : new TextSymbolSearchPrinter(compiled);
        printer.print(out, result, options, showLibrary, libraryManager);
        return 0;
      } catch (RuntimeException e) {
        System.err.println("[ERROR] -ss internal error: " + e);
        if (options.json) ResultJson.write(ctx.out(), List.of(), 0);
        return 1;
      }
    }
  }

  /** Renders a completed {@code -ss} search; implemented by {@link TextSymbolSearchPrinter} and {@link JsonSymbolSearchPrinter}. */
  interface SymbolSearchPrinter {
    void print(PrintStream out, SymbolSearchEngine.Result result, Options options,
               boolean showLibrary, LibraryManager libraryManager);
  }

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
