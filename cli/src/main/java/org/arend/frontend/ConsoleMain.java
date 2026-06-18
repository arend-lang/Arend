package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.commands.TypecheckPipeline;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.prelude.Prelude;

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
      cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
      cmdOptions.addOption(Option.builder().longOpt("slow-warn").hasArg().argName("ms").desc("emit a [WARN] line on stderr when typechecking of an individual definition exceeds this many milliseconds (default 5000; pass 0 to disable)").build());
      cmdOptions.addOption(null, "serialize", false, "after typechecking, persist typechecked modules as .arc binary caches; without this flag, no .arc files are written.");
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

      if (cmdLine.hasOption("ps") && containsHelpToken(cmdLine.getOptionValues("ps"))) {
        printTopicHelp(PROOF_SEARCH_HELP);
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
            + "no retrieval / REPL flag is given)", List.of(
            "test", "print", "recompile", "double-check", "serialize")),
        new Group("REPL", List.of("interactive")),
        new Group("Information retrieval (queries against the loaded library)", List.of(
            "proof-search")),
        new Group("Diagnostics / verbosity", List.of(
            "slow-warn",
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
    System.out.println("  arend [LIBRARY] -ps ...                            Information retrieval (no typecheck)");
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
    // Wrap the title block itself: group headers can be long-form, so they shouldn't
    // run off the right edge either.
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

    return org.arend.frontend.cli.Dispatch.execute(ctx, cmdLine) == 0;
  }

  public static void main(String[] args) {
    if (!new ConsoleMain().run(args)) {
      System.exit(1);
    }
  }
}
