package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.commands.ProofSearch;
import org.arend.frontend.cli.commands.TypecheckPipeline;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.prelude.Prelude;

public class ConsoleMain {
  private static boolean containsHelpToken(String[] values) {
    if (values == null) return false;
    for (String v : values) if ("help".equalsIgnoreCase(v)) return true;
    return false;
  }

  private final static String SYMBOL_SEARCH_HELP = """
      arend -ss <pattern> [option ...]

      Search every loaded library for definitions whose SHORT NAME matches
      <pattern>. Loads each .ard file at most once and persists a per-library
      on-disk index under <library>/<binariesDir>/.arend-symbol-index, so
      subsequent runs with no source changes are near-instant.

      PATTERN
        Foo                literal substring match against the SHORT name
                           (case-insensitive by default). Every Arend
                           identifier character is matched as plain text --
                           no character is a regex metacharacter in this
                           mode. So '*-comm', '^-1', '<*', '||', '+>+',
                           '[*]', '?-elim' all work as literal substrings.

                           Per Arend.g4, an identifier consists of:
                             operators   ~ ! @ # $ % ^ & * - + = < > ? / | : [ ]
                             letters     a-z  A-Z  _
                             Unicode     U+2200..U+22FF, U+2A00..U+2AFF
                                         (math operators: ∀ ∃ ∈ ⊂ ⊆ ∧ ∨ ≤ ⊕ …)
                             cont. only  0-9  '         (not first character)

                           Any other character ('.', '(', ')', '{', '}',
                           ',', ';', '"', '\\', backtick, whitespace) can
                           never appear in an Arend short name, so a plain
                           pattern containing one is rejected with a fix-it
                           pointing at re: or glob:. Most commonly that's a
                           regex sequence ('.*', '.+', '.?', '(?...') or a
                           qualified-name mistake ('Module.Foo' -- pass just
                           'Foo' and read the long name from the output).

                           A '|' in a plain pattern matches literally
                           (since '|' IS a valid identifier char) but emits
                           a soft warning, in case OR was intended.
        eq:<text>          exact short-name match (anchored), e.g. 'eq:pmap'
                           matches the function 'pmap' but not 'pmap2',
                           'pmap_<*-comm', etc.
        glob:<pat>         '*' = any chars, '?' = any one char.
                           Use '\\*' / '\\?' for literal stars / question
                           marks (so glob:'abs\\_\\*' matches abs_*, abs_*q,
                           and so on).
        re:<java-regex>    raw java.util.regex pattern, matched with find()
        hb:<chars>         humpback / camel-and-dash boundary fuzzy match,
                           e.g. 'hb:PMA' on 'PosetAddMonoid', 'hb:p-iP' on
                           'pi-isProp'. Use `case-sensitive` for strict camel.

      MULTIPLE PATTERNS
        Pass several patterns to OR them: a name matches if it satisfies any.
          arend -ss Cauchy -ss Mertens
        Whitespace inside a SINGLE -ss argument is also a separator, so:
          arend -ss "Cauchy Mertens"           # same as -ss Cauchy -ss Mertens
        Prefixed tokens mix freely:
          arend -ss "glob:abs_* hb:cAss" -ss re:^foo.*bar$

      QUERY ECHO
        Each run prints the parsed query before searching so you can see at
        a glance how each token was interpreted ('literal', 'exact', 'glob',
        'regex', 'humpback'). Glob / humpback also show the compiled regex.

      SHELL QUOTING
        Apostrophe `'` is a valid Arend continuation char (it appears in
        names like `-'`, `iabs_-_suc'`, primed-variant suffixes). It is
        also the most common shell quote delimiter, so passing names that
        contain it needs care:
          arend -ss "iabs_-'"            # outer double quotes: '\\'' is literal
          arend -ss 'iabs_-'\\'''        # outer singles, '\\''  splices an apostrophe
          arend -ss iabs_-\\'            # no outer quotes; backslash-escape
        Inside double quotes a bare `'` is NOT a delimiter — it stays in
        the string. So  -ss "a' 'b"  parses as TWO patterns, `a'` and `'b`,
        with the apostrophe on the wrong side of the space. A leading `'`
        in a pattern triggers a soft warning, since no Arend short name
        can start with `'` (it is a continuation-only character).

      EXTRA TOKENS  (each passed as a separate -ss argument)
        case-sensitive     match name case exactly (default: case-insensitive)
        no-cache           bypass the on-disk index, re-parse everything
        limit=N            cap printed matches at N (0 = unlimited; default 200)
        contains=<text>    extra AND substring filter on the short name; can be
                           repeated. Replaces post-pipe `| grep`.
        kind=k1,k2,...     keep only these kinds. Recognised:
                           func, sfunc, lemma, type, axiom, instance, coclause,
                           coerce, level, data, cons|constructor, class, record,
                           field, meta, other.
        only=name|self     restrict scope. Default: every library currently
                           registered (requested libs + transitive deps +
                           prelude). 'self' means the libraries listed on the
                           command line; a literal name (e.g. 'arend-lib')
                           picks that single library. Multiple values can be
                           comma-separated (e.g. only=arend-lib,liba).

      OUTPUT
        Line-oriented; safe to post-filter with `| head`, `| tail`, `| grep`
        if that is what your fingers reach for. `limit=N` and `contains=`
        do the same job inside the tool.

        <abs-path>:<line>:<col>           or  <library:module> for generated
        <library>::<long-name>  [<KIND>]
          <signature on a single line>

        Results are ordered by SHORT-NAME LENGTH (ascending), so an exact-
        length name appears before any longer name that just contains the
        query (e.g. for '-ss fac': 'face' before 'factor' before 'factors'
        before 'leftFactor' before 'completion-factor'). Ties are broken
        alphabetically. For a strict exact match use 'eq:<name>'.

        On zero matches, a plain-mode pattern is decomposed at operator-
        chars / underscore (alphanumeric runs of length >= 3) and the
        index is re-scanned for those parts -- so a miss on
        'natCoef_fromRat' still surfaces names containing 'natCoef' or
        'fromRat' as a "Did you mean?" list. Single-word queries with no
        decomposition emit a plain "No matches." -- there is nothing to
        suggest.

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

      See also: `-rx` / `--reindex` to refresh the index without searching.
      """;

  private final static String AI_HELP = """
      arend -ai [MODULE | MODULE:DEF]

      Agent-oriented all-in-one mode. Runs in sequence:

        1. Name resolution (suggest-only)
           For each unresolved short name, looks candidates up in the
           binary symbol index and prints a "Candidates for 'X' at ..."
           block listing each match's library::module:longName, the
           qualified name to splice into the source, and any required
           import. The CLI never rewrites your sources -- pick the
           right candidate and paste it in yourself. (An earlier
           auto-rewrite version was retired because position/length
           mistakes could mangle identifiers when several refs failed
           on the same line.)
        2. Typecheck
           Standard typecheck pass on the in-scope modules.
        3. Signature mirror
           Writes signature-only views of every typechecked module to
           <library>/.sig/<module>.ard. Function/lemma/instance bodies
           and class-field implementations are replaced with `{?}`; data
           constructors, field declarations, namespace commands, and
           \\where structure are preserved. Definitions with typecheck
           errors are replaced with `-- skipped: <name> (typecheck
           errors)` so the .sig pool only contains verified declarations.
        4. Symbol index refresh
           Rebuilds the on-disk symbol index used by -ss / -fu / -ch /
           -sc.

      GRANULARITY (positional args)
        no args        every requested library, end-to-end
        MODULE         that module + its transitive raw-import closure
        MODULE:DEF     same plus an existence check for DEF

      EXAMPLES
        arend -L libs my-lib -ai
        arend -L libs my-lib -ai Algebra.Group
        arend -L libs my-lib -ai Algebra.Group:comm-Group
        arend -L libs my-lib -ai -r           # full recompile + AI mode

      Replaces the older -nr / -rr / -sig flags.
      """;

  private final static String REINDEX_HELP = """
      arend -rx [only=name,...]

      Build (or refresh) the on-disk symbol index for every library in scope,
      then exit. Use this after creating/renaming modules so subsequent
      `-ss`, `-ai`, `-fu`, `-ch`, `-sc` runs see the new definitions.

      EXTRA TOKENS  (each as a separate -rx argument)
        only=name|self    restrict scope. Same semantics as the -ss
                          only= token (default: every loaded library).

      EXAMPLES
        arend -L libs my-lib -rx
        arend -L libs my-lib -rx only=self
      """;

  private final static String PROOF_SEARCH_HELP = """
      arend -ps <pattern> [print-full]

      Search every loaded library for definitions whose SIGNATURE (parameters
      and codomain) contains expressions matching <pattern>. Unlike -ss, this
      runs name resolution on the whole library first, so it is a lot slower
      than -ss but matches by structure rather than name.

      PATTERN GRAMMAR
        expr               a single sub-expression that must appear somewhere
                           in the signature; `_` matches any subexpression
        expr \\and expr     conjunction inside one clause: both must match
                           the same parameter (or the same codomain)
        e1 -> e2 -> codom  position-aware: e1 must match a parameter, e2 must
                           match a parameter that comes later in the pi, and
                           codom must match the codomain. With one `->` the
                           left side is 'any parameter' and the right side is
                           the codomain. Patterns may be parenthesised.

      OPTIONS
        print-full         print the entire definition with matching subterms
                           highlighted, instead of only the matching slice.

      EXAMPLES
        arend -L libs my-lib -ps 'Monoid'
        arend -L libs my-lib -ps 'Group -> _ = _'
        arend -L libs my-lib -ps 'isProp \\and _ -> _'
        arend -L libs my-lib -ps 'Monoid -> _' -ps print-full

      See also: https://arend-lang.github.io/documentation/plugin-manual/navigating#proof-search
      """;

  private final static String FIND_USAGES_HELP = """
      arend -fu <MODULE_PATH>:<GROUP_PATH> [option ...]

      Find every textual usage of the named definition that resolves to it
      after name resolution. Same flow as IntelliJ's Find Usages: text-search
      narrows files, then ArendServer resolveAll validates each candidate via
      identity comparison on the resolved Referable.

      SPEC
        MODULE_PATH    dotted module path,         e.g. Algebra.Monoid
        GROUP_PATH     dotted in-module path,      e.g. Monoid.equals
                       Reaches class fields, constructors, and \\where members.

      EXTRA TOKENS  (each as a separate -fu argument)
        with-tests        also search test sources
        no-line           omit the source line content from output
        aliases=false     don't include the target's alias name in the search
        limit=N           cap printed usages at N (0 = unlimited; default 500)
        only=name|self    restrict scope (default: every loaded library;
                          'self' = libraries listed on the command line;
                          comma-separated for multiple).

      ALIAS HANDLING
        Direct uses, the target's own alias, and locally renamed imports
        (`\\import M (foo \\as bar)` then `bar` in body) are caught. Multi-hop
        renames are followed via fixed-point iteration. Implicit references
        through instance resolution are NOT caught (they have no textual form).

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

      Print the inheritance lattice around a class plus every \\instance and
      \\new construction site. Resolves <CLASS> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p
        - '<short-name>'              looked up via the symbol index, restricted
                                      to CLASS / RECORD entries. Multiple matches
                                      print the candidates so you can pick.

      EXTRA TOKENS  (each as a separate -ch argument)
        up                only superclass chain
        down              only subclass tree (and constructors of subclasses)
        no-instances      omit the \\instance section
        no-news           omit the \\new section
        with-fields       annotate each tree node with its directly-declared fields
        with-tests        also search test sources
        only=name|self    restrict library scope (default: all loaded libraries)
        format=tree|flat  output format. tree (default) uses pseudographics for
                          a human-friendly view; flat emits one tagged relation
                          per line for grep / agentic loops:
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

      Implicit instances inferred during typechecking are NOT shown -- they have
      no source declaration. Use -fu on the class itself to see all reference
      sites instead.
      """;
  private final static String SCOPE_HELP = """
      arend -sc <REFERABLE> [<PATTERN>] [option ...]

      Dump the ambient name scope visible at a given referable's position.
      Mainly intended for debugging reference-resolution issues: which names
      are in scope here, and what do they actually resolve to?

      Resolves <REFERABLE> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p / -ch
        - '<short-name>'              looked up via the symbol index. Multiple
                                      matches print the candidates so you can pick.

      <PATTERN> is optional and uses the same grammar as -ss (literal substring
      by default; eq:, glob:, re:, hb: prefixes available). When given, only
      scope entries whose short name matches are printed.

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

  private CommandLine parseArgs(String[] args) {
    try {
      Options cmdOptions = new Options();
      cmdOptions.addOption("h", "help", false, "print this message");
      cmdOptions.addOption(Option.builder("L").longOpt("libdir").hasArg().argName("dir").desc("directory containing libraries").build());
      cmdOptions.addOption(Option.builder("s").longOpt("sources").hasArg().argName("dir").desc("project source directory").build());
      cmdOptions.addOption(Option.builder("e").longOpt("extensions").hasArg().argName("dir").desc("language extensions directory").build());
      cmdOptions.addOption(Option.builder("m").longOpt("extension-main").hasArg().argName("class").desc("main extension class").build());
      cmdOptions.addOption(Option.builder("c").longOpt("double-check").desc("double check correctness of the result").build());
      cmdOptions.addOption(Option.builder("i").longOpt("interactive").hasArg().optionalArg(true).argName("type").desc("start an interactive REPL, type can be plain or jline (default)").build());
      cmdOptions.addOption(Option.builder("p").longOpt("print").hasArg().argName("target").desc("print a definition or a module").build());
      cmdOptions.addOption(Option.builder("ps").longOpt("proof-search").hasArgs().argName("pattern")
          .desc("search by signature shape (parameters/codomain). Pass `-ps help` for the full grammar.").build());
      cmdOptions.addOption(Option.builder("ss").longOpt("symbol-search").hasArgs().argName("pattern")
          .desc("search by short name (uses an mtime-cached on-disk index). Pass `-ss help` for the full grammar.").build());
      cmdOptions.addOption(Option.builder("rx").longOpt("reindex").hasArgs().optionalArg(true).argName("only=lib,...")
          .desc("build/refresh the on-disk symbol index for every library in scope, then exit. Optional `only=name,...` token. Pass `-rx help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("fu").longOpt("find-usages").hasArgs().argName("MODULE:DEF")
          .desc("find every usage of a definition. Pass `-fu help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("ch").longOpt("class-hierarchy").hasArgs().argName("CLASS")
          .desc("print super/sub-class trees plus \\new and \\instance sites. Accepts MODULE:CLASS or a bare class name (resolved via the symbol index). Pass `-ch help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("sc").longOpt("scope").hasArgs().argName("REFERABLE")
          .desc("dump the ambient scope at a referable's position; debug aid for reference-resolution issues. Accepts MODULE:PATH or a bare short name, plus an optional -ss-style pattern to filter results. Pass `-sc help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("ai").longOpt("ai-pipeline").desc("agent-oriented all-in-one mode: (1) name-resolve: for each unresolved short name, list candidates (qualified name + required imports) the user can paste back into the source -- never rewrites, (2) typecheck, (3) emit signature-only mirrors to <library>/.sig/<module>.ard for verified definitions only (failed defs replaced with `-- skipped:` comments), (4) refresh the binary symbol index used by -ss/-fu/-ch/-sc. Honors positional-arg granularity (no args = library; MODULE; MODULE:DEF). Pass `-ai help` for full grammar.").build());
      cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
      cmdOptions.addOption("t", "test", false, "run tests");
      cmdOptions.addOption("v", "version", false, "print language version");
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_TIMES).build());
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_SIZES).build());
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_MODULES).build());
      cmdOptions.addOption(Option.builder().longOpt(TypecheckPipeline.SHOW_MODULES_WITH_INSTANCES).build());
      CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

      if (cmdLine.hasOption("h")) {
        new HelpFormatter().printHelp("arend [FILES]", cmdOptions);
        return null;
      }

      if (cmdLine.hasOption("v")) {
        System.out.println("Arend " + Prelude.VERSION);
        return null;
      }

      if (cmdLine.hasOption("ss") && containsHelpToken(cmdLine.getOptionValues("ss"))) {
        System.out.println(SYMBOL_SEARCH_HELP);
        return null;
      }

      if (cmdLine.hasOption("ps") && containsHelpToken(cmdLine.getOptionValues("ps"))) {
        System.out.println(PROOF_SEARCH_HELP);
        return null;
      }

      if (cmdLine.hasOption("fu") && containsHelpToken(cmdLine.getOptionValues("fu"))) {
        System.out.println(FIND_USAGES_HELP);
        return null;
      }

      if (cmdLine.hasOption("ch") && containsHelpToken(cmdLine.getOptionValues("ch"))) {
        System.out.println(CLASS_HIERARCHY_HELP);
        return null;
      }

      if (cmdLine.hasOption("sc") && containsHelpToken(cmdLine.getOptionValues("sc"))) {
        System.out.println(SCOPE_HELP);
        return null;
      }

      if (cmdLine.hasOption("rx") && containsHelpToken(cmdLine.getOptionValues("rx"))) {
        System.out.println(REINDEX_HELP);
        return null;
      }

      return cmdLine;
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      return null;
    }
  }

  private boolean run(String[] args) {
    CommandLine cmdLine = parseArgs(args);
    if (cmdLine == null) return false;

    CommandContext ctx = new CommandContext();
    if (!CliSetup.bootstrap(ctx, cmdLine)) return false;
    if (ctx.exitWithError) return false;

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

    if (!CliSetup.loadRequestedLibraries(ctx, cmdLine)) return false;
    if (ctx.exitWithError) return false;

    // Dispatch to the requested command. Resolution-only commands (-ss/-rx/-fu/-ch/-sc/-ps)
    // return immediately; everything else falls through to the default typecheck pipeline.
    if (cmdLine.hasOption("ss")) {
      org.arend.frontend.symbol.SymbolSearch.Parsed parsed =
          org.arend.frontend.symbol.SymbolSearch.parseArgs(cmdLine.getOptionValues("ss"), ctx.systemErrErrorReporter);
      if (parsed == null) return false;
      org.arend.frontend.symbol.SymbolSearch.run(parsed.patterns(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return !ctx.exitWithError;
    }

    if (cmdLine.hasOption("rx")) {
      java.util.Set<String> only = null;
      String[] rxArgs = cmdLine.getOptionValues("rx") == null ? new String[0] : cmdLine.getOptionValues("rx");
      for (String arg : rxArgs) {
        if (arg.startsWith("only=")) {
          if (only == null) only = new java.util.HashSet<>();
          for (String s : arg.substring("only=".length()).split(",")) {
            if (!s.isEmpty()) only.add(s.trim());
          }
        } else {
          System.err.println("[ERROR] Unknown -rx token: " + arg);
          return false;
        }
      }
      org.arend.frontend.symbol.SymbolSearch.reindex(ctx.requestedLibraries, ctx.libraryManager, ctx.server, only);
      return !ctx.exitWithError;
    }

    if (cmdLine.hasOption("fu")) {
      org.arend.frontend.symbol.UsageSearch.Parsed parsed =
          org.arend.frontend.symbol.UsageSearch.parseArgs(cmdLine.getOptionValues("fu"));
      if (parsed == null) return false;
      org.arend.frontend.symbol.UsageSearch.run(parsed.spec(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return !ctx.exitWithError;
    }

    if (cmdLine.hasOption("ch")) {
      org.arend.frontend.symbol.ClassHierarchy.Parsed parsed =
          org.arend.frontend.symbol.ClassHierarchy.parseArgs(cmdLine.getOptionValues("ch"));
      if (parsed == null) return false;
      org.arend.frontend.symbol.ClassHierarchy.run(parsed.spec(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return !ctx.exitWithError;
    }

    if (cmdLine.hasOption("sc")) {
      org.arend.frontend.symbol.ReferableScope.Parsed parsed =
          org.arend.frontend.symbol.ReferableScope.parseArgs(cmdLine.getOptionValues("sc"));
      if (parsed == null) return false;
      org.arend.frontend.symbol.ReferableScope.run(parsed.spec(), parsed.pattern(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return !ctx.exitWithError;
    }

    if (cmdLine.hasOption("ps")) {
      boolean psOk = ProofSearch.run(ctx, cmdLine.getOptionValues("ps"));
      return psOk && !ctx.exitWithError;
    }

    boolean tcOk = TypecheckPipeline.run(ctx, cmdLine);
    return tcOk && !ctx.exitWithError;
  }

  public static void main(String[] args) {
    if (!new ConsoleMain().run(args)) {
      System.exit(1);
    }
  }
}
