package org.arend.frontend;

import org.arend.cli.LockedFlags;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConsoleHelp {
  private ConsoleHelp() {}

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
        new Group("Daemon control (a long-lived JVM holding a warm ArendServer; an ordinary "
            + "call auto-routes to it unless --no-daemon is given)", List.of(
            "daemon", "daemon-status", "daemon-ping", "daemon-refresh", "daemon-stop", "no-daemon", "mcp", "mcp-unregister")),
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
    System.out.println("  arend [LIBRARY...] -d                              Start a daemon");
    System.out.println("  arend [LIBRARY] --daemon-{stop,status,refresh}     Daemon control");
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

    // Read off LockedFlags: a second copy of the list in prose goes stale the first time the set
    // changes.
    ConsoleHelpRenderer.printWrapped("Daemon-served commands lock these to the daemon's bootstrap "
        + "values; a per-request value is warned about and ignored (use --no-daemon to override): "
        + LockedFlags.ALL.stream().map(LockedFlags.Flag::display).collect(Collectors.joining(", "))
        + ". Inside a served command -i and the --daemon-* flags are rejected outright, and "
        + "neither -i nor -s is routed to a daemon in the first place.", 0, width);
  }
}
