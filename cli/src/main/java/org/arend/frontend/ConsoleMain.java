package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.commands.TypecheckPipeline;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.prelude.Prelude;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConsoleMain {
  private static boolean containsHelpToken(String[] values) {
    if (values == null) return false;
    for (String v : values) if ("help".equalsIgnoreCase(v)) return true;
    return false;
  }

  private final static String SYMBOL_SEARCH_HELP = """
      arend -ss <pattern> [option ...]

      Search every loaded library for definitions whose SHORT NAME matches <pattern>.
      Each .ard file is loaded at most once; results live in a per-library on-disk index at <library>/<binariesDir>/.arend-symbol-index.
      Subsequent runs with no source changes are near-instant.

      PATTERN
        Foo                Literal substring match against the SHORT name (case-insensitive by default).
                           Every Arend identifier character is matched as plain text; nothing is a regex metacharacter here.
                           So '*-comm', '^-1', '<*', '||', '+>+', '[*]', '?-elim' all work as literal substrings.

                           Per Arend.g4, an identifier consists of:
                             operators   ~ ! @ # $ % ^ & * - + = < > ? / | : [ ]
                             letters     a-z  A-Z  _
                             Unicode     U+2200..U+22FF, U+2A00..U+2AFF
                                         (math operators: ∀ ∃ ∈ ⊂ ⊆ ∧ ∨ ≤ ⊕ …)
                             cont. only  0-9  '         (not first character)

                           Plain patterns reject non-identifier chars: '.', '(', ')', '{', '}', ',', ';', '"', backtick, whitespace.
                           Such a pattern is rejected with a fix-it pointing at `re:` or `glob:`.
                           Most commonly that's a regex sequence (`.*`, `.+`, `.?`, `(?...`) or a qualified-name mistake (`Module.Foo`).
                           Pass just `Foo` and read the long name from the output.

                           A '|' in a plain pattern matches literally (since '|' IS a valid identifier char).
                           It emits a soft warning anyway, in case OR was intended.
        eq:<text>          Exact short-name match (anchored).
                           E.g. `eq:pmap` matches `pmap` but not `pmap2`, `pmap_<*-comm`, etc.
        glob:<pat>         `*` = any chars, `?` = any one char.
                           Use `\\*` / `\\?` for literal stars / question marks (so `glob:abs\\_\\*` matches `abs_*`, `abs_*q`, and so on).
        re:<java-regex>    raw java.util.regex pattern, matched with find()
        hb:<chars>         Humpback / camel-and-dash boundary fuzzy match.
                           E.g. `hb:PMA` matches `PosetAddMonoid`; `hb:p-iP` matches `pi-isProp`.
                           Use `case-sensitive` for strict camel.

      MULTIPLE PATTERNS
        Pass several patterns to OR them: a name matches if it satisfies any.
          arend -ss Cauchy -ss Mertens
        Whitespace inside a SINGLE -ss argument is also a separator, so:
          arend -ss "Cauchy Mertens"           # same as -ss Cauchy -ss Mertens
        Prefixed tokens mix freely:
          arend -ss "glob:abs_* hb:cAss" -ss re:^foo.*bar$

      QUERY ECHO
        Each run prints the parsed query before searching so you can see how each token was interpreted ('literal', 'exact', 'glob', 'regex', 'humpback').
        Glob / humpback also show the compiled regex.

      SHELL QUOTING
        Apostrophe `'` is a valid Arend continuation char — it appears in names like `-'`, `iabs_-_suc'`, and primed-variant suffixes.
        It is also the most common shell quote delimiter, so passing names that contain it needs care:
          arend -ss "iabs_-'"            # outer double quotes: '\\'' is literal
          arend -ss 'iabs_-'\\'''        # outer singles, '\\''  splices an apostrophe
          arend -ss iabs_-\\'            # no outer quotes; backslash-escape
        Inside double quotes a bare `'` is NOT a delimiter — it stays in the string.
        So `-ss "a' 'b"` parses as TWO patterns, `a'` and `'b`, with the apostrophe on the wrong side of the space.
        A leading `'` in a pattern triggers a soft warning: no Arend short name can start with `'` (it is continuation-only).

      EXTRA TOKENS  (each passed as a separate -ss argument)
        case-sensitive     match name case exactly (default: case-insensitive)
        no-cache           bypass the on-disk index, re-parse everything
        limit=N            cap printed matches at N (0 = unlimited; default 200)
        contains=<text>    Extra AND substring filter on the short name; can be repeated.
                           Replaces post-pipe `| grep`.
        kind=k1,k2,...     Keep only these kinds.
                           Recognised: func, sfunc, lemma, type, axiom, instance, coclause, coerce, level.
                           Plus: data, cons|constructor, class, record, field, meta, other.
        only=name|self     Restrict scope.
                           Default: every library currently registered (requested libs + transitive deps + prelude).
                           `self` means the libraries listed on the command line.
                           A literal name (e.g. `arend-lib`) picks that single library.
                           Multiple values can be comma-separated (e.g. `only=arend-lib,liba`).

      OUTPUT
        Line-oriented; safe to post-filter with `| head`, `| tail`, `| grep` if that is what your fingers reach for.
        `limit=N` and `contains=` do the same job inside the tool.

        <abs-path>:<line>:<col>           or  <library:module> for generated
        <library>::<long-name>  [<KIND>]
          <signature on a single line>

        Results are ordered by SHORT-NAME LENGTH (ascending).
        An exact-length name appears before any longer name that just contains the query.
        E.g. for `-ss fac`: `face` before `factor` before `factors` before `leftFactor` before `completion-factor`.
        Ties are broken alphabetically.
        For a strict exact match use `eq:<name>`.

        On zero matches, a plain-mode pattern is decomposed at operator-chars / underscores (alphanumeric runs of length >= 3).
        The index is re-scanned for those parts.
        So a miss on `natCoef_fromRat` still surfaces names containing `natCoef` or `fromRat` as a "Did you mean?" list.
        Single-word queries with no decomposition emit a plain "No matches." — there is nothing to suggest.

      EXAMPLES
        arend -L libs my-lib -ss 'Monoid'                   (substring)
        arend -L libs my-lib -ss '*-comm'                   (literal '*-comm')
        arend -L libs my-lib -ss 'BigSum_1 BigSum_+ BigSum-ext'  (OR via spaces)
        arend -L libs my-lib -ss 'BigSum_1 BigSum_+ BigSum-ext'  (OR via spaces)
        arend -L libs my-lib -ss Cauchy -ss Mertens         (OR via multi-flag)
        arend -L libs my-lib -ss 'eq:pmap' -ss kind=func,lemma
        arend -L libs my-lib -ss 'glob:abs_*' -ss limit=20
        arend -L libs my-lib -ss 're:^abs.*\\+.*$'
        arend -L libs my-lib -ss 'hb:isProp' -ss case-sensitive
        arend -L libs my-lib -ss only=self -ss 'shared-name'
        arend -L libs my-lib -ss Monoid -ss contains=comm   (Monoid* AND *comm*)
      """;

  private final static String AI_HELP = """
      arend -ai [MODULE | MODULE:DEF]

      Agent-oriented all-in-one mode. Runs in sequence:

        1. Name resolution (suggest-only)
           For each unresolved short name, prints a "Candidates for 'X' at ..." block sourced from the binary symbol index.
           Each candidate shows its library::module:longName, the qualified name to splice in, and any required import.
           The CLI never rewrites your sources -- pick the right candidate and paste it in yourself.
           (Auto-rewrite was retired: position/length mistakes mangled identifiers when several refs failed on the same line.)
        2. Typecheck
           Standard typecheck pass on the in-scope modules.
        3. Signature mirror
           Writes signature-only views of every typechecked module to <library>/.sig/<module>.ard.
           Function/lemma/instance bodies and class-field implementations are replaced with `{?}`.
           Data constructors, field declarations, namespace commands, and \\where structure are preserved.
           Definitions with typecheck errors become `-- skipped: <name> (typecheck errors)`.
           The .sig pool therefore only contains verified declarations.
        4. Symbol index refresh
           Rebuilds the on-disk symbol index used by -ss / -fu / -ch / -sc.

      GRANULARITY (positional args)
        no args        every requested library, end-to-end
        MODULE         that module + its transitive raw-import closure
        MODULE:DEF     same plus an existence check for DEF

      EXAMPLES
        arend -L libs my-lib -ai
        arend -L libs my-lib -ai Algebra.Group
        arend -L libs my-lib -ai Algebra.Group:comm-Group
        arend -L libs my-lib -ai -r           # full recompile + AI mode
      """;

  private final static String PROOF_SEARCH_HELP = """
      arend -ps <pattern> [print-full]

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
        print-full         Print the entire definition with matching subterms highlighted, instead of only the matching slice.

      EXAMPLES
        arend -L libs my-lib -ps 'Monoid'
        arend -L libs my-lib -ps 'Group -> _ = _'
        arend -L libs my-lib -ps 'isProp \\and _ -> _'
        arend -L libs my-lib -ps 'Monoid -> _' -ps print-full

      See also: https://arend-lang.github.io/documentation/plugin-manual/navigating#proof-search
      """;

  private final static String FIND_USAGES_HELP = """
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

  private final static String CLASS_HIERARCHY_HELP = """
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
  private final static String SCOPE_HELP = """
      arend -sc <REFERABLE> [<PATTERN>] [option ...]

      Dump the ambient name scope visible at a given referable's position.
      Mainly intended for debugging reference-resolution issues: which names are in scope here, and what do they actually resolve to?

      Resolves <REFERABLE> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p / -ch
        - '<short-name>'              looked up via the symbol index.
                                      Multiple matches print the candidates so you can pick.

      <PATTERN> is optional and uses the same grammar as -ss (literal substring by default; eq:, glob:, re:, hb: prefixes available).
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

  /** Exposed so the daemon worker can re-parse per-request CLI args on the warm context. */
  public static CommandLine parseArgs(String[] args) {
    try {
      Options cmdOptions = new Options();
      cmdOptions.addOption("h", "help", false, "print this message");
      cmdOptions.addOption(Option.builder("L").longOpt("libdir").hasArg().argName("dir").desc("directory containing libraries").build());
      cmdOptions.addOption(Option.builder("s").longOpt("sources").hasArg().argName("dir").desc("project source directory").build());
      cmdOptions.addOption(Option.builder("e").longOpt("extensions").hasArg().argName("dir").desc("language extensions directory").build());
      cmdOptions.addOption(Option.builder("m").longOpt("extension-main").hasArg().argName("class").desc("main extension class").build());
      cmdOptions.addOption(Option.builder("c").longOpt("double-check").desc("double check correctness of the result").build());
      cmdOptions.addOption(Option.builder("i").longOpt("interactive").hasArg().optionalArg(true).argName("plain|jline").desc("start an interactive REPL").build());
      cmdOptions.addOption(Option.builder("p").longOpt("print").hasArg().argName("MODULE[:DEF]").desc("after the typecheck, print the elaborated/typechecked form of MODULE (or a single DEF inside it): implicits filled in, eliminators desugared.").build());
      cmdOptions.addOption(Option.builder("ps").longOpt("proof-search").hasArgs().argName("sig-pattern")
          .desc("search by signature shape (parameters/codomain). Pass `-ps help` for the full grammar.").build());
      cmdOptions.addOption(Option.builder("ss").longOpt("symbol-search").hasArgs().argName("name-pattern")
          .desc("search by short name (uses an mtime-cached on-disk index). Pass `-ss help` for the full grammar.").build());
      cmdOptions.addOption(Option.builder("fu").longOpt("find-usages").hasArgs().argName("MODULE:DEF")
          .desc("find every usage of a definition. Pass `-fu help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("ch").longOpt("class-hierarchy").hasArgs().argName("MODULE:CLASS|name")
          .desc("print super/sub-class trees plus \\new and \\instance sites. Pass `-ch help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("sc").longOpt("scope").hasArgs().argName("MODULE:PATH|name")
          .desc("dump the ambient scope at a referable's position; debug aid for reference-resolution issues. Pass `-sc help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("ai").longOpt("ai-pipeline").desc("agent-oriented typecheck: quiet by default (verbose output goes to a per-invocation log), maintains the <library>/.sig/ signature mirror. Pass `-ai help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("d").longOpt("daemon").desc("start a daemon for the given library. Requires either a LIBRARY positional anchored to an arend.yaml (or a name resolved via -L; defaults to ./arend.yaml when omitted), or a `-s <dir>` synthetic library (in which case the daemon is keyed to <dir> and its state lives in <dir>/.arend/). The daemon does the normal load+typecheck+persist+ai-finalize once, then idles serving future client requests.").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-stop").desc("stop the daemon serving the given library (single positional library reference).").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-ping").desc("ping the daemon serving the given library; reports IDLE or BUSY.").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-status").desc("dump status").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-refresh").desc("re-run the bootstrap pipeline (-ai) on the daemon's warm context so source edits are picked up.").build());
      cmdOptions.addOption(Option.builder().longOpt("no-daemon").desc("force in-process execution even if a daemon serves the requested library.").build());
      cmdOptions.addOption(Option.builder().longOpt("no-quiet").desc("with -ai: disable the per-invocation log split; emit verbose narration to stdout/stderr like a pre-quiet run. No effect outside -ai.").build());
      cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
      cmdOptions.addOption(Option.builder().longOpt("slow-warn").hasArg().argName("ms").desc("emit a [WARN] line on stderr when typechecking of an individual definition exceeds this many milliseconds (default 5000; pass 0 to disable)").build());
      cmdOptions.addOption(null, "no-serialize", false, "do not persist typechecked modules as .arc binary caches after typechecking; serialization is on by default.");
      cmdOptions.addOption("t", "test", false, "run tests");
      cmdOptions.addOption("v", "version", false, "print language version");
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_TIMES).desc("after typechecking, print every definition's typecheck duration, sorted descending").build());
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_SIZES).desc("after typechecking, print every definition's typechecked core-term size (number of subterms), sorted descending").build());
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_MODULES).desc("after typechecking, print the module import-DAG in topological order via Tarjan SCC. Single modules: `[Module]`; import cycles: `[M1, M2, ...]`.").build());
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_MODULES_WITH_INSTANCES).desc("like --show-modules, but restricted to modules that define at least one \\instance — useful for spotting cycles among instance-providing modules").build());
      CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

      if (cmdLine.hasOption("h")) {
        printGroupedHelp(cmdOptions);
        return null;
      }

      if (cmdLine.hasOption("v")) {
        System.out.println("Arend " + Prelude.VERSION);
        return null;
      }

      // -ai takes no argument, so its `help` token shows up as a positional.
      if (cmdLine.hasOption("ai") && cmdLine.getArgList().contains("help")) {
        printTopicHelp(AI_HELP);
        return null;
      }

      if (cmdLine.hasOption("ss") && containsHelpToken(cmdLine.getOptionValues("ss"))) {
        printTopicHelp(SYMBOL_SEARCH_HELP);
        return null;
      }

      if (cmdLine.hasOption("ps") && containsHelpToken(cmdLine.getOptionValues("ps"))) {
        printTopicHelp(PROOF_SEARCH_HELP);
        return null;
      }

      if (cmdLine.hasOption("fu") && containsHelpToken(cmdLine.getOptionValues("fu"))) {
        printTopicHelp(FIND_USAGES_HELP);
        return null;
      }

      if (cmdLine.hasOption("ch") && containsHelpToken(cmdLine.getOptionValues("ch"))) {
        printTopicHelp(CLASS_HIERARCHY_HELP);
        return null;
      }

      if (cmdLine.hasOption("sc") && containsHelpToken(cmdLine.getOptionValues("sc"))) {
        printTopicHelp(SCOPE_HELP);
        return null;
      }

      return cmdLine;
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      return null;
    }
  }

  /**
   * Print a pre-formatted topic-help string respecting the current terminal width.
   * Each input line is printed verbatim if it fits; lines that exceed the width are
   * soft-wrapped, with continuation lines indented to the original line's leading
   * whitespace so definition-list / code-block alignment is preserved.
   */
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

  /**
   * Print {@code --help} as semantic groups instead of one alphabetised list. Each group
   * shows the option's short/long name + arg-name placeholder, followed by its declared
   * description (wrapped). Anything not assigned to an explicit group is dropped to a
   * trailing {@code Diagnostics} block so undeclared options can't silently disappear.
   */
  /**
   * Detect the terminal width so help wrapping can use the full window. Tries, in order,
   * the {@code COLUMNS} env var, {@code stty size} when stdout is a TTY, and finally
   * falls back to 100. Capped at 200 because very wide terminals (multi-monitor setups)
   * produce help lines so long they're hard to read at all.
   */
  private static int detectTerminalWidth() {
    String env = System.getenv("COLUMNS");
    if (env != null) {
      try { int w = Integer.parseInt(env.trim()); if (w >= 40) return Math.min(w, 200); }
      catch (NumberFormatException ignored) {}
    }
    if (System.console() != null) {
      try {
        Process p = new ProcessBuilder("stty", "size").redirectErrorStream(true)
            .redirectInput(new java.io.File("/dev/tty")).start();
        try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
          String line = br.readLine();
          p.waitFor();
          if (line != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
              int w = Integer.parseInt(parts[1]);
              if (w >= 40) return Math.min(w, 200);
            }
          }
        }
      } catch (Exception ignored) {}
    }
    return 100;
  }

  private static void printGroupedHelp(Options cmdOptions) {
    record Group(String title, List<String> longOpts) {}
    List<Group> groups = List.of(
        new Group("Library and source paths. -L adds a search root for resolving named "
            + "LIBRARY positionals; it does not itself load a library. -s loads a bare source "
            + "directory as a synthetic library named `\\default` — no arend.yaml needed, and "
            + "can substitute for the LIBRARY positional. -e / -m attach extensions to that "
            + "synthetic library.", List.of(
            "libdir", "sources", "extensions", "extension-main")),
        new Group("Typecheck workflows (load the library and verify it; default workflow when "
            + "no retrieval / REPL / daemon flag is given)", List.of(
            "ai-pipeline", "test", "print", "recompile", "double-check", "no-serialize")),
        new Group("REPL", List.of("interactive")),
        new Group("Daemon control (the daemon is a long-lived JVM that holds a warm "
            + "ArendServer; subsequent CLI calls auto-route to it unless --no-daemon is given)", List.of(
            "daemon", "daemon-status", "daemon-ping", "daemon-refresh", "daemon-stop", "no-daemon")),
        new Group("Information retrieval (queries against the loaded library)", List.of(
            "symbol-search", "proof-search", "find-usages", "class-hierarchy", "scope")),
        new Group("Diagnostics / verbosity", List.of(
            "no-quiet", "slow-warn",
            TypecheckPipeline.SHOW_TIMES, TypecheckPipeline.SHOW_SIZES,
            TypecheckPipeline.SHOW_MODULES, TypecheckPipeline.SHOW_MODULES_WITH_INSTANCES)),
        new Group("Meta", List.of("help", "version"))
    );

    int width = detectTerminalWidth();
    // Compute the description-column start: the longest option label + 2 spaces of padding,
    // capped so very long labels don't squeeze the description below readability. Anything
    // beyond the cap wraps the description onto the next line as usual.
    int indent = computeIndent(cmdOptions, width);

    System.out.println("Workflows (mutually exclusive; first matching flag wins):");
    System.out.println("  arend [LIBRARY] [MODULE[:DEF]]                     Typecheck workflows");
    System.out.println("  arend [LIBRARY] -i [plain|jline]                   REPL");
    System.out.println("  arend [LIBRARY] -d|--daemon-{stop,status,refresh}  Daemon control");
    System.out.println("  arend [LIBRARY] {-ss|-ps|-fu|-ch|-sc} ...          Information retrieval (no typecheck)");
    System.out.println();
    printWrapped("LIBRARY is a path to a directory containing arend.yaml, the arend.yaml file "
        + "itself, a .zip library, or a library name resolved via -L / the default library root "
        + "(~/.arend/libs). Omitted → defaults to ./arend.yaml. MODULE / MODULE:DEF positionals "
        + "narrow the typecheck scope.", 0, width);
    System.out.println();

    Set<String> placed = new HashSet<>();
    for (Group g : groups) {
      printGroup(cmdOptions, g.title(), g.longOpts(), placed, width, indent);
    }

    // Anything declared but not bucketed: surface it so the help stays exhaustive.
    List<String> leftover = new ArrayList<>();
    for (Option opt : cmdOptions.getOptions()) {
      if (opt.getLongOpt() != null && !placed.contains(opt.getLongOpt())) {
        leftover.add(opt.getLongOpt());
      }
    }
    if (!leftover.isEmpty()) {
      printGroup(cmdOptions, "Other", leftover, placed, width, indent);
    }

    printWrapped("Daemon-served commands silently lock these to the daemon's bootstrap values "
        + "(the per-request value is parsed but never applied; use --no-daemon to override): "
        + "-L, -s, -e, -m, -c, -r, --no-serialize, --slow-warn.", 0, width);
    printWrapped("These are rejected outright inside a daemon-served command: -i, -d, "
        + "--daemon-stop, --daemon-ping, --daemon-status, --daemon-refresh.", 0, width);
  }

  /**
   * Width of the label column = longest rendered "  -x, --long <arg>" string in the option set,
   * plus a 2-space gap before the description. Capped relative to the terminal width so we
   * never push the description into a sliver. Floor at 30 so short-label groups don't look
   * weirdly tight.
   */
  private static int computeIndent(Options cmdOptions, int width) {
    int max = 0;
    for (Option opt : cmdOptions.getOptions()) {
      max = Math.max(max, renderLabel(opt).length());
    }
    int gap = 2;
    int cap = Math.min(width / 2, 50); // never let labels eat more than ~half the line
    return Math.max(30, Math.min(max + gap, cap));
  }

  private static String renderLabel(Option opt) {
    StringBuilder sb = new StringBuilder("  ");
    if (opt.getOpt() != null) sb.append("-").append(opt.getOpt()).append(", ");
    else sb.append("    ");
    sb.append("--").append(opt.getLongOpt());
    if (opt.hasArg()) {
      String argName = opt.getArgName() == null ? "arg" : opt.getArgName();
      if (opt.hasOptionalArg()) sb.append(" [").append(argName).append("]");
      else sb.append(" <").append(argName).append(">");
    }
    return sb.toString();
  }

  private static void printGroup(Options cmdOptions, String title, List<String> longOpts, Set<String> placed, int width, int indent) {
    // Wrap the title block itself: group headers can be long-form (e.g. the Daemon
    // explanation), so they shouldn't run off the right edge either.
    printWrapped(title, 0, width);
    for (String longOpt : longOpts) {
      Option opt = cmdOptions.getOption("--" + longOpt);
      if (opt == null) {
        // Lookup by long name without prefix:
        for (Option o : cmdOptions.getOptions()) {
          if (longOpt.equals(o.getLongOpt())) { opt = o; break; }
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

  /** Greedy word-wrap: returns the longest prefix of {@code text} that fits in the line. */
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

  /**
   * Daemon-bootstrap variant of {@link #run}: parse + setup + dispatch + return the
   * warm {@link CommandContext} (or null on failure). The daemon hands the returned
   * ctx to its {@link org.arend.frontend.cli.daemon.server.DaemonServer} so subsequent
   * per-request CLI commands can dispatch against an already-loaded ArendServer.
   *
   * <p>Typecheck errors (or any other diagnostic-level failure inside {@link
   * org.arend.frontend.cli.Dispatch#execute}) do <em>not</em> abort startup: the warm
   * ctx is still useful — clients can refresh, re-typecheck, or query symbols against
   * the partially-loaded state. Only fatal infrastructure failures (argv parse error,
   * CliSetup.bootstrap or loadRequestedLibraries returning false, or an unhandled
   * exception during dispatch) cause this method to return null.
   */
  public CommandContext runDaemonBootstrap(String[] args) {
    CommandLine cmdLine = parseArgs(args);
    if (cmdLine == null) return null;
    CommandContext ctx = new CommandContext();
    ctx.bootstrapArgs = args.clone();
    if (!CliSetup.bootstrap(ctx, cmdLine)) return null;
    if (!CliSetup.loadRequestedLibraries(ctx, cmdLine)) return null;
    // Library load succeeded; from here on, typecheck errors are non-fatal for daemon
    // startup. Dispatch.execute may flip ctx.exitWithError or return non-zero, but
    // either way we still want to serve the warm context to clients.
    try {
      org.arend.frontend.cli.Dispatch.execute(ctx, cmdLine);
    } catch (Throwable t) {
      System.err.println("[DAEMON] unhandled exception during bootstrap typecheck:");
      t.printStackTrace();
      return null;
    }
    // Clear the diagnostic-level error flag so it doesn't leak into the first client
    // request's exit code. Per-request state is also reset by CliDispatcher.
    ctx.exitWithError = false;
    return ctx;
  }

  private boolean run(String[] args) {
    CommandLine cmdLine = parseArgs(args);
    if (cmdLine == null) return false;

    CommandContext ctx = new CommandContext();
    if (!CliSetup.bootstrap(ctx, cmdLine)) return false;
    if (ctx.exitWithError) return false;

    // Daemon control: doesn't load libraries in-process; the child JVM does.
    // Library reference is always a single positional arg.
    boolean daemonStart   = cmdLine.hasOption("d");
    boolean daemonStop    = cmdLine.hasOption("daemon-stop");
    boolean daemonPing    = cmdLine.hasOption("daemon-ping");
    boolean daemonStatus  = cmdLine.hasOption("daemon-status");
    boolean daemonRefresh = cmdLine.hasOption("daemon-refresh");
    if (daemonStart || daemonStop || daemonPing || daemonStatus || daemonRefresh) {
      int chosen = (daemonStart ? 1 : 0) + (daemonStop ? 1 : 0) + (daemonPing ? 1 : 0)
          + (daemonStatus ? 1 : 0) + (daemonRefresh ? 1 : 0);
      if (chosen > 1) {
        System.err.println("[ERROR] only one of -d / --daemon-stop / --daemon-ping / --daemon-status / --daemon-refresh may be given");
        return false;
      }
      java.util.List<String> positional = cmdLine.getArgList();
      if (positional.size() > 1) {
        System.err.println("[ERROR] daemon mode requires at most one positional library reference");
        return false;
      }
      // Synthetic-library daemon: -s <dir> with no positional. The daemon is keyed to
      // <dir> and its inner pipeline is driven by -s/-e/-m rather than an arend.yaml.
      // Positional wins if both are given (warn the user that -s is ignored).
      String sourceDirStr = cmdLine.getOptionValue("s");
      Path syntheticSrcDir = null;
      if (sourceDirStr != null && positional.isEmpty()) {
        syntheticSrcDir = Paths.get(sourceDirStr).toAbsolutePath();
      } else if (sourceDirStr != null) {
        System.err.println("[WARN] daemon mode: positional LIBRARY given, -s " + sourceDirStr
            + " is ignored for daemon-identity resolution (still forwarded to the inner pipeline)");
      }
      // Control ops (stop/ping/status/refresh) with no -s and no positional: try cwd's
      // arend.yaml first; if absent, see if a synthetic daemon was previously started here
      // (a `<cwd>/.arend/daemon.lock` is the marker) and target that instead.
      if (syntheticSrcDir == null && positional.isEmpty() && !daemonStart) {
        Path cwd = Paths.get(".").toAbsolutePath();
        if (!java.nio.file.Files.isRegularFile(cwd.resolve("arend.yaml"))) {
          org.arend.frontend.cli.daemon.DaemonPaths synth =
              org.arend.frontend.cli.daemon.DaemonPaths.resolveSynthetic(cwd);
          if (java.nio.file.Files.exists(synth.lockFile)) syntheticSrcDir = cwd;
        }
      }
      // No positional + no -s → fall back to ./arend.yaml, mirroring non-daemon default.
      String libRef = positional.isEmpty() ? "." : positional.get(0);
      int rc;
      if (daemonStart) {
        // Forward bootstrap-affecting flags so the child JVM owns the same locked state.
        java.util.List<String> extra = new java.util.ArrayList<>();
        if (cmdLine.hasOption("s")) { extra.add("-s"); extra.add(cmdLine.getOptionValue("s")); }
        if (cmdLine.hasOption("e")) { extra.add("-e"); extra.add(cmdLine.getOptionValue("e")); }
        if (cmdLine.hasOption("m")) { extra.add("-m"); extra.add(cmdLine.getOptionValue("m")); }
        if (cmdLine.hasOption("c")) { extra.add("-c"); }
        if (cmdLine.hasOption("r")) { extra.add("-r"); }
        if (cmdLine.hasOption("no-serialize")) { extra.add("--no-serialize"); }
        if (cmdLine.hasOption("slow-warn")) { extra.add("--slow-warn"); extra.add(cmdLine.getOptionValue("slow-warn")); }
        rc = org.arend.frontend.cli.daemon.DaemonStart.run(libRef, ctx.libDirs, extra, syntheticSrcDir);
      } else if (daemonStop) {
        rc = org.arend.frontend.cli.daemon.DaemonStop.run(libRef, ctx.libDirs, syntheticSrcDir);
      } else {
        String op = daemonPing ? "ping" : daemonStatus ? "status" : "refresh";
        rc = org.arend.frontend.cli.daemon.client.DaemonRpc.run(libRef, ctx.libDirs, op, syntheticSrcDir);
        if (rc == org.arend.frontend.cli.daemon.client.DaemonRpc.NO_DAEMON) {
          System.err.println("[ERROR] " + op + ": no daemon running for the given library");
          rc = 1;
        }
      }
      return rc == 0;
    }

    // -i REPL needs only libDirs + server (both set up by bootstrap); short-circuit before
    // collecting requested modules / loading libraries.
    if (cmdLine.hasOption("i")) {
      String replKind = cmdLine.getOptionValue("i", "jline");
      switch (replKind.toLowerCase()) {
        case "plain":
          PlainCliRepl.launch(false, ctx.libDirs, ctx.server);
          break;
        case "jline":
          JLineCliRepl.launch(false, ctx.libDirs, ctx.server);
          break;
        default:
          System.err.println("[ERROR] Unrecognized repl type: " + replKind);
          return false;
      }
      return !ctx.exitWithError;
    }

    // Try to route to a daemon serving the requested library. Returns empty when no
    // daemon is available or healthy — caller falls through to the in-process pipeline.
    if (!cmdLine.hasOption("no-daemon")) {
      java.util.OptionalInt remote = org.arend.frontend.cli.daemon.client.DaemonRpc.tryRouteCli(
          args, cmdLine.getArgList(), ctx.libDirs);
      if (remote.isPresent()) {
        return remote.getAsInt() == 0;
      }
    }

    if (!CliSetup.loadRequestedLibraries(ctx, cmdLine)) return false;
    if (ctx.exitWithError) return false;

    return org.arend.frontend.cli.Dispatch.execute(ctx, cmdLine) == 0;
  }

  public static void main(String[] args) {
    // Internal entry for the daemon child JVM: never returns to normal CLI dispatch.
    // Detected here, before parseArgs, because commons-cli would reject the flag and we
    // also want to bypass any normal-CLI output buffering before the parent has wired up
    // stdout/stderr to daemon.log.
    if (args.length > 0 && ("--daemon-bootstrap".equals(args[0]) || "--daemon-bootstrap-synthetic".equals(args[0]))) {
      org.arend.frontend.cli.daemon.DaemonMain.run(args);
      return;
    }
    if (!new ConsoleMain().run(args)) {
      System.exit(1);
    }
  }
}
