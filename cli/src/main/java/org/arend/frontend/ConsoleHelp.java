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

final class ConsoleHelp {
  private ConsoleHelp() {}

  private static final String SYMBOL_SEARCH_HELP = """
      arend -ss <pattern> [option ...]

      Search every loaded library for definitions whose SHORT NAME matches <pattern>.
      Each .ard file is loaded at most once; results live in a per-library on-disk index at <library>/<binariesDir>/.arend-symbol-index.
      Subsequent runs with no source changes are near-instant.

      PATTERN
        Foo                Literal substring match against the SHORT name (smart case — see SMART CASE below).
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
        re:<java-regex>    Raw java.util.regex pattern, matched with find() — so it is UNANCHORED:
                           `re:Monoid` hits any name CONTAINING "Monoid"; anchor with `^` / `$` for a whole-name match.
                           Full Java syntax works: `|`, `[...]`, `(?:...)`, lookahead `(?=...)`, backrefs `\\1`, `\\p{...}`, `(?i)` …
                           CASE-SENSITIVE exactly as typed — unlike every other mode, re: is NOT smart-case.
                           So `re:^[A-Z]` / `re:\\p{Lu}` match capitals only (e.g. "starts with a capital").
                           Prepend `(?i)` to opt back into case-insensitivity: `re:(?i)monoid`.
                           A space inside the pattern splits it into separate patterns (see MULTIPLE PATTERNS), so a regex
                           cannot contain a literal space — Arend short names never do anyway.
        hb:<chars>         Humpback / camel-and-dash boundary fuzzy match: each char must start a new "word" —
                           every NON-PLAIN char starts a word: an uppercase letter (capital run splits: `HLevels`=H·Levels),
                           a digit (embedded digit splits: `log1p`=log·1p, `expm1`=exp·m·1), or an operator/symbol,
                           as does a run after a dash / underscore / operator char.
                           E.g. `hb:PAM` matches `PosetAddMonoid` (Poset·Add·Monoid); `hb:CM` matches `CMonoid`, `ContMap`;
                           `hb:ccel1` matches `compose-coef-expm1-log1p` (compose·coef·expm1·log1p, with the trailing `1`).
                           A `-` glued to a `_` does not split off a following LOWERCASE word (`abs_-left` = abs·-left), but a
                           non-plain char always starts a word — so `HLevel_-1` = HLevel·-·1 and `hb:HL-2s` matches `HLevels_-2-sigma`.
                           Boundary detection is ALWAYS case-sensitive (an uppercase letter is case-based), so hb: never over-matches.
                           Pattern letters follow the usual SMART CASE (below): `hb:PAM` needs capital P·A·M (finds `PosetAddMonoid`,
                           not a lowercase `p_a_m` name), while `hb:pam` finds both.

      SMART CASE  (every mode except re:)
        A lowercase letter in the pattern matches EITHER case; an uppercase letter matches UPPERCASE only.
        So `monoid` finds both `Monoid` and `monoid`, whereas `Monoid` skips a lowercase `monoid`.
        There is no case flag. For full case control — e.g. a lowercase-ONLY match — use re:, which is case-sensitive as typed.

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
        no-cache           bypass the on-disk index, re-parse everything
        limit=N            cap printed matches at N (0 = unlimited; default 200)
        contains=<text>    Extra AND substring filter on the short name (smart case, like a plain pattern); can be repeated.
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

        With the global `--json` flag, results are emitted instead as a JSON array on stdout (one object per line:
        library [omitted when a single non-prelude library is loaded], file, module, line, column, longName, kind,
        signature). [INFO] logs and this query echo are routed to stderr, so stdout stays pure JSON (redirect the
        log with `2>/tmp/log`).

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
        arend -L libs my-lib -ss Cauchy -ss Mertens         (OR via multi-flag)
        arend -L libs my-lib -ss 'eq:pmap' -ss kind=func,lemma
        arend -L libs my-lib -ss 'glob:abs_*' -ss limit=20
        arend -L libs my-lib -ss 're:^abs.*\\+.*$'                (regex, anchored both ends)
        arend -L libs my-lib -ss 're:^[A-Z]'                      (Capitalised names; re: is case-sensitive as typed)
        arend -L libs my-lib -ss 'hb:PAM'                         (humpback -> PosetAddMonoid, PosetAbMonoid, …)
        arend -L libs my-lib -ss only=self -ss 'shared-name'
        arend -L libs my-lib -ss Monoid -ss contains=Add   (short name matches 'Monoid' AND contains 'Add', e.g. AddMonoid)
      """;


  private static final String PROOF_SEARCH_HELP = """
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

  static void printSymbolSearch() {
    printTopicHelp(SYMBOL_SEARCH_HELP);
  }

  static void printProofSearch() {
    printTopicHelp(PROOF_SEARCH_HELP);
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
