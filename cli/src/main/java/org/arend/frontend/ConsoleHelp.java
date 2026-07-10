package org.arend.frontend;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConsoleHelp {
  private ConsoleHelp() {}

  /**
   * The {@code -ss} / {@code --symbol-search} help. Assembled from three parts so
   * the CLI and the REPL share the middle ({@link #SS_BODY}); only the head (the
   * synopsis line) and the tail (OUTPUT + EXAMPLES) differ.
   */
  public static String symbolSearchHelp() {
    return SS_HEAD_CLI + SS_BODY + SS_TAIL_CLI;
  }

  /**
   * The {@code :symbol-search} REPL help: the shared middle with no synopsis line,
   * no {@code --json} note, and {@code :ss} examples.
   */
  public static String symbolSearchReplHelp() {
    return SS_BODY + SS_TAIL_REPL;
  }

  // ---- -ss / :symbol-search help, in three parts (see symbolSearchHelp) -------
  // HEAD (CLI only): the synopsis line. The REPL omits it.
  private static final String SS_HEAD_CLI = """
      arend [LIBRARY ...] -ss <pattern> [pattern | option ...]

      """;

  // BODY: shared verbatim by the CLI and the REPL.
  private static final String SS_BODY = """
      Find definitions by SHORT NAME across the loaded libraries. Prints each match's
      location, full name, kind, and one-line signature. Fast: served from a
      per-library on-disk index that re-parses only changed files.

      PATTERN MODES
        Foo            substring match (default). Characters are literal, so Arend
                       operator names work as-is: *-comm, ^-1, ?-elim, <*, ||.
        glob:Foo       whole-name match; * = any run, ? = one char. With no wildcard
                       it is an EXACT name. Match a literal * or ? with \\* / \\?.
        re:<regex>     Java regex, unanchored (find()); case-sensitive as typed.
        hb:<chars>     humpback: type word starts / prefixes.
                       hb:PAM -> PosetAddMonoid, hb:mon -> Monoid, hb:isP -> isProp.

      SMART CASE (all modes except re:): a lowercase letter matches either case, an
      uppercase letter matches uppercase only. So `monoid` finds `Monoid`, while
      `Monoid` skips a lowercase `monoid`.

      Multiple patterns are OR'd; whitespace inside one argument also separates, so
      `-ss "Monoid Ring"` is the same as `-ss Monoid -ss Ring`.

      OPTIONS  (each a separate -ss argument)
        limit=N            cap matches; 0 = unlimited (default 200)
        kind=k,...         keep only these kinds: func sfunc lemma type axiom instance
                           coclause coerce level data cons class record field meta
        contains=<text>    extra substring filter on the short name; repeatable

      SCOPE is every loaded library (the LIBRARY positionals + their dependencies +
      prelude); to search less, load less. Matches are ordered shortest-name-first
      (exact-length names lead), then alphabetically. A query that misses is split at
      operators into word-parts to suggest near names ("Did you mean?").

      """;

  // TAIL (CLI): OUTPUT with the --json note, and `arend ... -ss` examples.
  private static final String SS_TAIL_CLI = """
      OUTPUT is line-oriented: location, then `library::LongName [KIND]`, then the
      one-line signature. With --json output is `{"results":[...],"count":N}` where
      count is the total match count and results is truncated to `limit`; diagnostics
      go to a log file (default <tmpdir>/arend-symbol-search.log) so stdout stays pure JSON.

      EXAMPLES
        arend arend-lib -ss Monoid                    substring
        arend arend-lib -ss glob:pmap                 exact name
        arend arend-lib -ss 'glob:*-comm'             names ending in -comm
        arend arend-lib -ss hb:PAM                    humpback -> PosetAddMonoid
        arend arend-lib -ss Monoid -ss contains=Add   'Monoid' AND *Add* -> AddMonoid
        arend arend-lib -ss Ring -ss kind=class
        arend arend-lib -ss "pmap transport"          OR two names
        arend arend-lib -ss 're:comm$'                names ending in "comm"
      """;

  // TAIL (REPL): OUTPUT without JSON, and `:ss` examples (no shell quoting).
  private static final String SS_TAIL_REPL = """
      OUTPUT is line-oriented: location, then `library::LongName [KIND]`, then the
      one-line signature.

      EXAMPLES
        :ss Monoid                 substring
        :ss glob:pmap              exact name
        :ss glob:*-comm            names ending in -comm
        :ss hb:PAM                 humpback -> PosetAddMonoid
        :ss Monoid contains=Add    'Monoid' AND *Add* -> AddMonoid
        :ss Ring kind=class
        :ss pmap transport         OR two names
        :ss re:comm$               names ending in "comm"
      """;


  /**
   * The {@code -ps} / {@code --proof-search} help. Like {@link #symbolSearchHelp},
   * assembled from parts so the CLI and the REPL share the middle ({@link #PS_BODY});
   * only the head (synopsis) and the tail (OUTPUT + EXAMPLES) differ.
   */
  public static String proofSearchHelp() {
    return PS_HEAD_CLI + PS_BODY + PS_TAIL_CLI;
  }

  /**
   * The {@code :proof-search} REPL help: the shared middle with no synopsis line,
   * no {@code --json} note, and {@code :ps} examples.
   */
  public static String proofSearchReplHelp() {
    return PS_BODY + PS_TAIL_REPL;
  }

  // ---- -ps / :proof-search help, in parts (see proofSearchHelp) ---------------
  // HEAD (CLI only): the synopsis line. The REPL omits it.
  private static final String PS_HEAD_CLI = """
      arend [LIBRARY ...] -ps <pattern> [print-full] [--json]

      """;

  // BODY: shared verbatim by the CLI and the REPL.
  private static final String PS_BODY = """
      Search every loaded library for definitions whose SIGNATURE (parameters and codomain) contains expressions matching <pattern>.
      Unlike -ss, this runs name resolution on the whole library first.
      That makes it a lot slower than -ss, but it matches by structure rather than name.

      PATTERN GRAMMAR
        expr               A single sub-expression that must appear somewhere in the signature; `_` matches any subexpression.
        expr \\and expr     Conjunction inside one clause: both must match the same parameter (or the same codomain).
        e1 -> e2 -> codom  Position-aware: e1 matches a parameter; e2 matches a later parameter in the pi; codom matches the codomain.
                           With one `->` the left side is 'any parameter' and the right side is the codomain.
                           Patterns may be parenthesised.

      OPTIONS
        print-full         Print each match's full SIGNATURE (the same header/body -ss shows) instead of only the matching slice.
        limit=N            cap matches; 0 = unlimited (default 200)

      """;

  // TAIL (CLI): OUTPUT with the --json note, and `arend ... -ps` examples.
  private static final String PS_TAIL_CLI = """
      OUTPUT
        Plain text: name + location, then either the matching slice (parameters -> codomain)
        or, with print-full, the definition's signature. With --json output is
        `{"results":[...],"count":N}` (the same shape as -ss); each result has a `signature`
        field with print-full, otherwise an `expression` field with the matching slice.
        In --json mode diagnostics go to a log file so stdout stays pure JSON.

      EXAMPLES
        arend -L libs my-lib -ps 'Monoid'
        arend -L libs my-lib -ps 'Group -> _ = _'
        arend -L libs my-lib -ps 'isProp \\and _ -> _'
        arend -L libs my-lib -ps 'Monoid -> _' -ps print-full
        arend -L libs my-lib -ps 'Monoid -> _' -ps limit=0
        arend -L libs my-lib -ps 'Monoid -> _' --json

      See also: https://arend-lang.github.io/documentation/plugin-manual/navigating#proof-search
      """;

  // TAIL (REPL): OUTPUT without JSON, and `:ps` examples (no shell quoting).
  private static final String PS_TAIL_REPL = """
      OUTPUT
        name + location, then either the matching slice (parameters -> codomain)
        or, with print-full, the definition's signature.

      EXAMPLES
        :ps Monoid
        :ps Group -> _ = _
        :ps isProp \\and _ -> _
        :ps Monoid -> _ print-full
        :ps Monoid -> _ limit=0

      See also: https://arend-lang.github.io/documentation/plugin-manual/navigating#proof-search
      """;


  private static final String FIND_USAGES_HELP = """
      arend -fu <MODULE_PATH>:<GROUP_PATH> [option ...]

      Find every textual usage of the named definition that resolves to it after name resolution.
      Same flow as IntelliJ's Find Usages.
      Text-search narrows files first; ArendServer's resolveAll then validates each candidate by referable identity.

      SPEC
        MODULE_PATH    dotted module path,         e.g. Algebra.Monoid
        GROUP_PATH     dotted in-module path,      e.g. Monoid.equals
                       Reaches class fields, constructors, and \\where members.

      EXTRA TOKENS  (each as a separate -fu argument)
        with-tests        also search test sources
        no-line           omit the source line content from output
        aliases=false     don't include the target's alias name in the search
        limit=N           cap printed usages at N (0 = unlimited; default 500)
        only=name|self    Restrict scope.
                          Default: every loaded library; `self` = libraries listed on the command line; comma-separated for multiple.

      ALIAS HANDLING
        Direct uses, the target's own alias, and locally renamed imports (`\\import M (foo \\as bar)` then `bar` in body) are caught.
        Multi-hop renames are followed via fixed-point iteration.
        Implicit references through instance resolution are NOT caught (they have no textual form).

      OUTPUT
        Usages of <library>::<long-name>  [<KIND>]

        <abs-path>:<line>:<col>: <source line, trimmed>
        ...
        Found N usage(s)

      EXAMPLES
        arend -L libs my-lib -fu 'Algebra.Monoid:Monoid.equals'
        arend -L libs my-lib -fu 'Paths:transport' -fu limit=20 -fu no-line
        arend -L libs my-lib -fu only=self -fu 'Foo:bar'
      """;


  private static final String CLASS_HIERARCHY_HELP = """
      arend -ch <CLASS> [option ...]

      Print the inheritance lattice around a class plus every \\instance and \\new construction site.
      Resolves <CLASS> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p
        - '<short-name>'              looked up via the symbol index, restricted to CLASS / RECORD entries.
                                      Multiple matches print the candidates so you can pick.

      EXTRA TOKENS  (each as a separate -ch argument)
        up                only superclass chain
        down              only subclass tree (and constructors of subclasses)
        no-instances      omit the \\instance section
        no-news           omit the \\new section
        with-fields       annotate each tree node with its directly-declared fields
        with-tests        also search test sources
        only=name|self    restrict library scope (default: all loaded libraries)
        format=tree|flat  Output format.
                          `tree` (default) uses pseudographics for a human-friendly view.
                          `flat` emits one tagged relation per line for grep / agentic loops:
                            TARGET      lib::M:C  KIND  abs:line:col
                            EXTENDS     lib::M:C  lib::N:Parent
                            EXTENDED-BY lib::M:C  lib::M:Sub
                            INSTANCE    lib::M:C  abs:line:col  instanceName
                            NEW         lib::M:C  abs:line:col  impl=...  miss=...
        limit=N           cap printed instance / new lines (default 200; 0 = all)

      EXAMPLES
        arend -L libs my-lib -ch 'CMonoid'
        arend -L libs my-lib -ch 'Algebra.Monoid:CMonoid' -ch with-fields
        arend -L libs my-lib -ch 'BaseSet' -ch down
        arend -L libs my-lib -ch 'Monoid' -ch format=flat -ch up

      Implicit instances inferred during typechecking are NOT shown -- they have no source declaration.
      Use -fu on the class itself to see all reference sites instead.
      """;
 
  private static final String SCOPE_HELP = """
      arend -sc <REFERABLE> [<PATTERN>] [option ...]

      Dump the ambient name scope visible at a given referable's position.
      Mainly intended for debugging reference-resolution issues: which names are in scope here, and what do they actually resolve to?

      Resolves <REFERABLE> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p / -ch
        - '<short-name>'              looked up via the symbol index.
                                      Multiple matches print the candidates so you can pick.

      <PATTERN> is optional and uses the same grammar as -ss (literal substring by default; glob:, re:, hb: prefixes available).
      When given, only scope entries whose short name matches are printed.

      EXTRA TOKENS  (each as a separate -sc argument)
        context=static    only static-scope entries (default)
        context=dynamic   only dynamic-scope entries (record/class fields)
        context=all       print STATIC, DYNAMIC, PLEVEL, HLEVEL sections in turn

      OUTPUT FORMAT
        Each in-scope entry is printed as
            SHORT_NAME -> LIBRARY::MODULE_PATH:LONG_NAME [KIND]
        (locally-bound referables that have no global location print as
            SHORT_NAME -> (local <RefType>))

      EXAMPLES
        arend -L libs my-lib -sc 'Algebra.Monoid:Monoid'
        arend -L libs my-lib -sc 'Monoid' 'hb:CM'
        arend -L libs my-lib -sc 'Algebra.Monoid:Monoid' context=all
      """;

  static void printSymbolSearch() {
    printTopicHelp(symbolSearchHelp());
  }

  static void printProofSearch() {
    printTopicHelp(proofSearchHelp());
  }

  static void printFindUsages() {
    printTopicHelp(FIND_USAGES_HELP);
  }

  static void printClassHierarchy() {
    printTopicHelp(CLASS_HIERARCHY_HELP);
  }

  static void printScope() {
    printTopicHelp(SCOPE_HELP);
  }


  private static int detectTerminalWidth() {
    String env = System.getenv("COLUMNS");
    if (env != null) {
      try { int width = Integer.parseInt(env.trim()); if (width >= 40) return Math.min(width, 200); }
      catch (NumberFormatException ignored) {}
    }
    if (System.console() != null) {
      try {
        Process process = new ProcessBuilder("stty", "size").redirectErrorStream(true)
            .redirectInput(new File("/dev/tty")).start();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
          String line = reader.readLine();
          process.waitFor();
          if (line != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
              int width = Integer.parseInt(parts[1]);
              if (width >= 40) return Math.min(width, 200);
            }
          }
        }
      } catch (Exception ignored) {}
    }
    return 100;
  }

  private static void printTopicHelp(String text) {
    int width = detectTerminalWidth();
    for (String line : text.split("\n", -1)) {
      if (line.length() <= width) {
        System.out.println(line);
        continue;
      }
      int leading = 0;
      while (leading < line.length() && line.charAt(leading) == ' ') leading++;
      String indent = " ".repeat(leading);
      String rest = line.substring(leading);
      int budget = Math.max(20, width - leading);
      while (rest.length() > budget) {
        int cut = rest.lastIndexOf(' ', budget);
        if (cut <= 0) cut = budget;
        System.out.println(indent + rest.substring(0, cut));
        rest = rest.substring(cut).stripLeading();
      }
      if (!rest.isEmpty()) System.out.println(indent + rest);
    }
  }

  static void printGrouped(Options cmdOptions, List<String> diagnosticOptions) {
    record Group(String title, List<String> longOpts) {}
    List<Group> groups = List.of(
        new Group("Library and source paths. -L adds a search root for resolving named "
            + "LIBRARY positionals; it does not itself load a library. -s loads a bare source "
            + "directory as a synthetic library named `\\default` -- no arend.yaml needed, and "
            + "can substitute for the LIBRARY positional. -e / -m attach extensions to that "
            + "synthetic library.", List.of(
            "libdir", "sources", "extensions", "extension-main")),
        new Group("Typecheck workflows (load the library and verify it; default workflow when "
            + "no retrieval / REPL flag is given)", List.of(
            "test", "print", "recompile", "double-check", "serialize")),
        new Group("REPL", List.of("interactive")),
        new Group("Information retrieval (queries against the loaded library)", List.of(
            "symbol-search", "proof-search", "find-usages", "class-hierarchy", "scope")),
        new Group("Diagnostics / verbosity", diagnosticOptions),
        new Group("Meta", List.of("help", "version"))
    );

    int width = detectTerminalWidth();
    int indent = computeIndent(cmdOptions, width);

    System.out.println("Workflows (mutually exclusive; first matching flag wins):");
    System.out.println("  arend [LIBRARY] [MODULE[:DEF]]                     Typecheck workflows");
    System.out.println("  arend [LIBRARY] -i [plain|jline]                   REPL");
    System.out.println("  arend [LIBRARY] {-ss|-ps|-fu|-ch|-sc} ...        Information retrieval (no typecheck)");
    System.out.println();
    printWrapped("LIBRARY is a path to a directory containing arend.yaml, the arend.yaml file "
        + "itself, a .zip library, or a library name resolved via -L / the default library root "
        + "(~/.arend/libs). Omitted means ./arend.yaml is loaded when present. MODULE / MODULE:DEF "
        + "positionals narrow the typecheck scope.", 0, width);
    System.out.println();

    Set<String> placed = new HashSet<>();
    for (Group group : groups) {
      printGroup(cmdOptions, group.title(), group.longOpts(), placed, width, indent);
    }

    List<String> leftover = new ArrayList<>();
    for (Option opt : cmdOptions.getOptions()) {
      if (opt.getLongOpt() != null && !placed.contains(opt.getLongOpt())) {
        leftover.add(opt.getLongOpt());
      }
    }
    if (!leftover.isEmpty()) {
      printGroup(cmdOptions, "Other", leftover, placed, width, indent);
    }
  }

  private static int computeIndent(Options cmdOptions, int width) {
    int max = 0;
    for (Option opt : cmdOptions.getOptions()) {
      max = Math.max(max, renderLabel(opt).length());
    }
    int gap = 2;
    int cap = Math.min(width / 2, 50);
    return Math.max(30, Math.min(max + gap, cap));
  }

  private static String renderLabel(Option opt) {
    StringBuilder builder = new StringBuilder("  ");
    if (opt.getOpt() != null) builder.append("-").append(opt.getOpt()).append(", ");
    else builder.append("    ");
    builder.append("--").append(opt.getLongOpt());
    if (opt.hasArg()) {
      String argName = opt.getArgName() == null ? "arg" : opt.getArgName();
      if (opt.hasOptionalArg()) builder.append(" [").append(argName).append("]");
      else builder.append(" <").append(argName).append(">");
    }
    return builder.toString();
  }

  private static void printGroup(Options cmdOptions, String title, List<String> longOpts, Set<String> placed, int width, int indent) {
    printWrapped(title, 0, width);
    for (String longOpt : longOpts) {
      Option opt = cmdOptions.getOption("--" + longOpt);
      if (opt == null) {
        for (Option candidate : cmdOptions.getOptions()) {
          if (longOpt.equals(candidate.getLongOpt())) { opt = candidate; break; }
        }
      }
      if (opt == null) continue;
      placed.add(longOpt);

      StringBuilder label = new StringBuilder(renderLabel(opt));
      String desc = opt.getDescription();
      if (desc == null || desc.isEmpty()) {
        System.out.println(label);
      } else if (label.length() >= indent) {
        System.out.println(label);
        printWrapped(desc, indent, width);
      } else {
        while (label.length() < indent) label.append(' ');
        label.append(wrapFirstLine(desc, indent, width));
        System.out.println(label);
        int firstLen = width - indent;
        if (desc.length() > firstLen) {
          String rest = remainingAfterFirstLine(desc, firstLen);
          if (!rest.isEmpty()) printWrapped(rest, indent, width);
        }
      }
    }
    System.out.println();
  }

  private static String wrapFirstLine(String text, int indent, int wrapAt) {
    int budget = wrapAt - indent;
    if (text.length() <= budget) return text;
    int cut = text.lastIndexOf(' ', budget);
    if (cut <= 0) cut = budget;
    return text.substring(0, cut);
  }

  private static String remainingAfterFirstLine(String text, int budget) {
    if (text.length() <= budget) return "";
    int cut = text.lastIndexOf(' ', budget);
    if (cut <= 0) cut = budget;
    return text.substring(cut).stripLeading();
  }

  private static void printWrapped(String text, int indent, int wrapAt) {
    String pad = " ".repeat(indent);
    int budget = wrapAt - indent;
    String rest = text;
    while (rest.length() > budget) {
      int cut = rest.lastIndexOf(' ', budget);
      if (cut <= 0) cut = budget;
      System.out.println(pad + rest.substring(0, cut));
      rest = rest.substring(cut).stripLeading();
    }
    if (!rest.isEmpty()) System.out.println(pad + rest);
  }
}
