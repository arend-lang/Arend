package org.arend.frontend;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

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
      Find definitions by NAME across the loaded libraries. Prints each match's
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
        A.B.C          long name: match dotted parts of the qualified name; the last
                       segment is the short name (details under LONG-NAME SEARCH).

      SMART CASE (all modes except re:): a lowercase letter matches either case, an
      uppercase letter matches uppercase only. So `monoid` finds `Monoid`, while
      `Monoid` skips a lowercase `monoid`.

      Multiple patterns are OR'd; whitespace inside one argument also separates, so
      `-ss "Monoid Ring"` is the same as `-ss Monoid -ss Ring`.

      LONG-NAME SEARCH: a dotted pattern like `Monoid.*-comm` searches by parts of the
      qualified name -- the last segment matches the short name (rules above), and each
      earlier segment must match, in order, an enclosing module/namespace segment. A
      definition's full name thus narrows down to that one definition.

      OPTIONS  (each a separate -ss argument)
        limit=N            cap matches; 0 = unlimited (default 200)
        kind=k,...         keep only these kinds: func sfunc lemma type axiom instance
                           coclause coerce level data cons class record field meta
        self               restrict to the requested (top-level) libraries; skip
                           dependencies (a no-op in the REPL)

      SCOPE is every loaded library (the LIBRARY positionals + their dependencies +
      prelude); to search less, load less or pass `self`. Matches are ordered
      shortest-name-first (exact-length names lead), then alphabetically. A query that
      misses is split at operators into word-parts to suggest near names ("Did you mean?").

      """;

  // TAIL (CLI): OUTPUT with the --json note, and `arend ... -ss` examples.
  private static final String SS_TAIL_CLI = """
      OUTPUT is line-oriented: location, then `[library::]module:LongName [KIND]` (the
      `library::` prefix appears only when more than one library is in scope), then the
      one-line signature. With --json output is `{"results":[...],"count":N}` where
      count is the total match count and results is truncated to `limit`; diagnostics
      go to a log file (default <tmpdir>/arend-symbol-search.log) so stdout stays pure JSON.

      EXAMPLES
        arend arend-lib -ss Monoid                    substring
        arend arend-lib -ss glob:pmap                 exact name
        arend arend-lib -ss 'glob:*-comm'             names ending in -comm
        arend arend-lib -ss hb:PAM                    humpback -> PosetAddMonoid
        arend arend-lib -ss Ring -ss kind=class
        arend arend-lib -ss "pmap transport"          OR two names
        arend arend-lib -ss 're:comm$'                names ending in "comm"
        arend arend-lib -ss 'Monoid.*-comm'           long name: *-comm under a Monoid path
      """;

  // TAIL (REPL): OUTPUT without JSON, and `:ss` examples (no shell quoting).
  private static final String SS_TAIL_REPL = """
      OUTPUT is line-oriented: location, then `[library::]module:LongName [KIND]` (the
      `library::` prefix appears only when more than one library is in scope), then the
      one-line signature.

      EXAMPLES
        :ss Monoid                 substring
        :ss glob:pmap              exact name
        :ss glob:*-comm            names ending in -comm
        :ss hb:PAM                 humpback -> PosetAddMonoid
        :ss Ring kind=class
        :ss pmap transport         OR two names
        :ss re:comm$               names ending in "comm"
        :ss Monoid.*-comm          long name: *-comm under a Monoid path
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
        expr               A single sub-expression that must appear somewhere in
                           the signature; `_` matches any subexpression.
        expr \\and expr     Conjunction inside one clause: both must match the same
                           parameter (or the same codomain).
        e1 -> e2 -> codom  Position-aware: e1 matches a parameter; e2 matches a
                           later parameter in the pi; codom matches the codomain.
                           With one `->` the left side is 'any parameter' and the
                           right side is the codomain.
                           Patterns may be parenthesised.

      OPTIONS
        print-full         Print each match's full SIGNATURE (the same header/body
                           -ss shows) instead of only the matching slice.
        limit=N            cap matches; 0 = unlimited (default 200)
        self               restrict to the requested (top-level) libraries; skip
                           dependencies (a no-op in the REPL)

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


  // -fu / :find-usages help, split like -ss so the CLI and REPL share FU_BODY;
  // only the synopsis (FU_HEAD_CLI) and the tail (OUTPUT extras + EXAMPLES) differ.
  private static final String FU_HEAD_CLI = """
      arend [LIBRARY ...] -fu <MODULE_PATH>:<GROUP_PATH> [option ...]

      """;

  private static final String FU_BODY = """
      Find every textual usage of the named definition that resolves to it after name resolution.
      Same flow as IntelliJ's Find Usages.
      Text-search narrows files first; ArendServer's resolveAll then validates each candidate by referable identity.

      SPEC
        MODULE_PATH    dotted module path,         e.g. Algebra.Monoid
        GROUP_PATH     dotted in-module path,      e.g. Monoid.equals
                       Reaches class fields, constructors, and \\where members.
        An optional `<library>::` prefix is accepted and scopes resolution to that
        library, so a candidate line printed on an ambiguous lookup (e.g.
        `arend-lib::Arith.Trig.Real:sin`) can be pasted back verbatim.

      EXTRA TOKENS  (each a separate token)
        with-tests        also search test sources
        no-line           omit the source line content from output
        aliases=false     don't include the target's alias name in the search
        limit=N           cap printed usages at N (0 = unlimited; default 500)
        self              hunt usages only in the requested (top-level) libraries; skip
                          dependencies (a no-op in the REPL). The target itself is still
                          resolved against the full scope, so it may live in a dependency.

      ALIAS HANDLING
        Direct uses, the target's own alias, and locally renamed imports (`\\import M (foo \\as bar)` then `bar` in body) are caught.
        Multi-hop renames are followed via fixed-point iteration.
        Implicit references through instance resolution are NOT caught (they have no textual form).
        A FIELD target additionally misses usages whose receiver type is known only after typechecking (this search resolves but does not typecheck); a [WARN] is printed for fields.

      OUTPUT (same shape as -ss: location, then the enclosing definition, then the source line)
        Usages of [<library>::]<module>:<long-name>  [<KIND>]
        (the `<library>::` prefix appears only when more than one library is in scope)

        <path>:<line>:<col>                             -- one per usage; usages sharing a
        <path>:<line>:<col>                                row share the two lines below
        [<library>::]<module>:<enclosing-def>  [KIND]   -- the definition the usage sits in
          <source line, usages highlighted>
        ...
        Found N usage(s)
      """;

  // TAIL (CLI): the --json note and `arend ... -fu` examples.
  private static final String FU_TAIL_CLI = """

      With --json: `{"results":[...],"count":N}` on stdout (diagnostics to a log file);
      one entry per usage -- never grouped by row.

      EXAMPLES
        arend -L libs my-lib -fu 'Algebra.Monoid:Monoid.equals'
        arend -L libs my-lib -fu 'Paths:transport' -fu limit=20 -fu no-line
        arend -L libs my-lib -fu self -fu 'Foo:bar'
      """;

  // TAIL (REPL): `:fu` examples (no --json, no shell quoting needed).
  private static final String FU_TAIL_REPL = """

      EXAMPLES
        :fu 'Algebra.Monoid:Monoid.equals'
        :fu Paths:transport limit=20 no-line
        :fu arend-lib::Arith.Trig.Real:sin
      """;


  static final String CLASS_HIERARCHY_HELP = """
      arend -ch <CLASS> [option ...]

      Print the inheritance lattice around a class plus every \\instance and \\new construction site.
      Resolves <CLASS> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p. An optional
                                      '<library>::' prefix scopes to that library (and is
                                      printed back on output); without it, a module path
                                      found in several loaded libraries picks the first and warns.
        - '<short-name>'              looked up via the symbol index, restricted to
                                      CLASS / RECORD entries. Multiple matches print
                                      the candidates so you can pick.

      EXTRA TOKENS  (each as a separate -ch argument)
        up                only superclass chain
        down              only subclass tree (and constructors of subclasses)
        no-instances      omit the \\instance section
        no-news           omit the \\new section
        with-fields       annotate each tree node with its directly-declared fields
        with-tests        also search test sources
        self              build the hierarchy from the requested (top-level) libraries
                          only; skip dependencies (a no-op in the REPL). The target class
                          is still resolved against the full scope, so it may live in a
                          dependency — you then see your own slice (subclasses / instances
                          / \\new sites) of it.
        format=tree|flat  Output format.
                          `tree` (default) uses pseudographics for a human-friendly view.
                          `flat` emits one tagged relation per line for grep / agentic loops
                          (the `lib::` prefix below is dropped when only one library is in scope):
                            TARGET      lib::M:C  KIND  abs:line:col
                            EXTENDS     lib::M:C  lib::N:Parent
                            EXTENDED-BY lib::M:C  lib::M:Sub
                            INSTANCE    lib::M:C  abs:line:col  instanceName
                            NEW         lib::M:C  abs:line:col  impl=...  miss=...
        limit=N           cap printed instance / new lines (default 200; 0 = all)

      With --json the hierarchy is one object on stdout, mirroring the trees:
        {"target":{name,kind,location}, "superclasses":[<node>..], "subclasses":[<node>..],
         "instances":[{instance,class,location}..], "newSites":[{class,location,impl,missing}..],
         "counts":{instances,newSites}}
      where a <node> is {name, location[, record][, fields][, repeat], children:[<node>..]}
      (children = parents in superclasses / children in subclasses; repeat flags a diamond).
      Diagnostics go to a log file (as with --json -ss). up/down/no-instances/no-news/limit apply.

      EXAMPLES
        arend -L libs my-lib -ch 'CMonoid'
        arend -L libs my-lib -ch 'Algebra.Monoid:CMonoid' -ch with-fields
        arend -L libs my-lib -ch 'BaseSet' -ch down
        arend -L libs my-lib -ch 'Monoid' -ch format=flat -ch up
        arend -L libs my-lib -ch 'Monoid' --json

      Implicit instances inferred during typechecking are NOT shown -- they have no source declaration.
      Use -fu on the class itself to see all reference sites instead.
      """;
 
  private static final String SCOPE_HELP = """
      arend -sc <REFERABLE> [<PATTERN>] [option ...]

      Dump the ambient name scope visible at a given referable's position.
      Mainly intended for debugging reference-resolution issues: which names are in scope here, and what do they actually resolve to?

      Resolves <REFERABLE> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p / -ch. An optional
                                      '<library>::' prefix scopes to that library (and is
                                      printed back on output); without it, a module path
                                      found in several loaded libraries picks the first and warns.
        - '<short-name>'              looked up via the symbol index.
                                      Multiple matches print the candidates so you can pick.

      <PATTERN> is optional and uses the same grammar as -ss (literal substring by default; glob:, re:, hb: prefixes available).
      When given, only scope entries whose short name matches are printed.

      EXTRA TOKENS  (each as a separate -sc argument)
        (no context=)     static and dynamic entries merged into one sorted list (default)
        context=static    only static-scope entries
        context=dynamic   only dynamic-scope entries (record/class fields)
        context=all       print STATIC, DYNAMIC, PLEVEL, HLEVEL sections in turn

      OUTPUT FORMAT
        Each in-scope entry is printed as
            SHORT_NAME -> [LIBRARY::]MODULE_PATH:LONG_NAME [KIND]
        (the LIBRARY:: prefix appears only when more than one library is in scope;
         locally-bound referables that have no global location print as
            SHORT_NAME -> (local <RefType>))
        With --json the scope is one object {"target":..,"entries":[{name,context,
        kind,module,longName[,library]} | {name,context,local:true,refType}],"count":N}
        on stdout; diagnostics go to a log file (as with --json -ss).

      In the REPL, `:sc` with NO argument dumps the current session scope (Prelude +
      the REPL module + everything you have imported); `:sc <REFERABLE> ...` behaves
      as above. The context= tokens and a <PATTERN> apply to the spec form only.

      EXAMPLES
        arend -L libs my-lib -sc 'Algebra.Monoid:Monoid'
        arend -L libs my-lib -sc 'Monoid' 'hb:CM'
        arend -L libs my-lib -sc 'Algebra.Monoid:Monoid' context=all
        arend -L libs my-lib -sc 'Algebra.Monoid:Monoid' --json
        :sc                                    (REPL) dump the current session scope
      """;

  public static void printSymbolSearch() {
    ConsoleHelpRenderer.printTopicHelp(symbolSearchHelp());
  }

  public static void printProofSearch() {
    ConsoleHelpRenderer.printTopicHelp(proofSearchHelp());
  }

  /** The {@code -fu} CLI help: synopsis + shared body + CLI tail (--json note, examples). */
  public static String findUsagesHelp() {
    return FU_HEAD_CLI + FU_BODY + FU_TAIL_CLI;
  }

  /** The {@code :find-usages} REPL help: the shared body with no synopsis and {@code :fu} examples. */
  public static String findUsagesReplHelp() {
    return FU_BODY + FU_TAIL_REPL;
  }

  public static void printFindUsages() {
    ConsoleHelpRenderer.printTopicHelp(findUsagesHelp());
  }

  public static void printClassHierarchy() {
    ConsoleHelpRenderer.printTopicHelp(CLASS_HIERARCHY_HELP);
  }

  /** The {@code -ch} / {@code :class-hierarchy} help text (shared by the CLI and the REPL). */
  public static String classHierarchyHelp() {
    return CLASS_HIERARCHY_HELP;
  }

  public static void printScope() {
    ConsoleHelpRenderer.printTopicHelp(SCOPE_HELP);
  }

  /** The {@code -sc} / {@code :scope} help text (shared by the CLI and the REPL). */
  public static String scopeHelp() {
    return SCOPE_HELP;
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

    int width = ConsoleHelpRenderer.detectTerminalWidth();
    int indent = ConsoleHelpRenderer.computeIndent(cmdOptions, width);

    System.out.println("Workflows (mutually exclusive; first matching flag wins):");
    System.out.println("  arend [LIBRARY] [MODULE[:DEF]]                     Typecheck workflows");
    System.out.println("  arend [LIBRARY] -i [plain|jline]                   REPL");
    System.out.println("  arend [LIBRARY] {-ss|-ps|-fu|-ch|-sc} ...          Information retrieval (no typecheck)");
    System.out.println();
    ConsoleHelpRenderer.printWrapped("LIBRARY is a path to a directory containing arend.yaml, the arend.yaml file "
        + "itself, a .zip library, or a library name resolved via -L / the default library root "
        + "(~/.arend/libs). Omitted means ./arend.yaml is loaded when present. MODULE / MODULE:DEF "
        + "positionals narrow the typecheck scope.", 0, width);
    System.out.println();

    Set<String> placed = new HashSet<>();
    for (Group group : groups) {
      ConsoleHelpRenderer.printGroup(cmdOptions, group.title(), group.longOpts(), placed, width, indent);
    }

    List<String> leftover = new ArrayList<>();
    for (Option opt : cmdOptions.getOptions()) {
      if (opt.getLongOpt() != null && !placed.contains(opt.getLongOpt())) {
        leftover.add(opt.getLongOpt());
      }
    }
    if (!leftover.isEmpty()) {
      ConsoleHelpRenderer.printGroup(cmdOptions, "Other", leftover, placed, width, indent);
    }
  }

}
