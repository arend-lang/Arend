package org.arend.frontend;

import org.apache.commons.cli.Option;
import org.arend.frontend.cli.daemon.LockedFlags;
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
        new Group("Library and source paths. LIBRARY names are looked up in the -L directories, "
            + "which replace ~/.arend/libs when given and load nothing themselves. -s loads a source "
            + "directory as a library named `\\default` that needs no arend.yaml and depends on the "
            + "LIBRARY positionals; with -s, ./arend.yaml is not loaded. -e / -m give that library its "
            + "extensions.", List.of(
            "libdir", "sources", "extensions", "extension-main")),
        new Group("Typecheck (the workflow when no other workflow's flag is given)", List.of(
            "test", "print", "recompile", "double-check", "serialize")),
        new Group("Daemon control (a long-lived JVM holding a library; a typecheck or search "
            + "about it goes to it, unless --no-daemon, -s or -i is given)", List.of(
            "daemon", "daemon-status", "daemon-ping", "daemon-refresh", "daemon-stop", "no-daemon")),
        new Group("REPL", List.of("interactive")),
        new Group("Information retrieval (queries against the loaded libraries, no typecheck; "
            + "of several of these flags, the first in this list runs)", List.of(
            "symbol-search", "proof-search", "find-usages", "class-hierarchy", "scope")),
        new Group("Diagnostics / verbosity", diagnosticOptions),
        new Group("Meta", List.of("help", "version"))
    );

    int width = ConsoleHelpRenderer.detectTerminalWidth();
    int indent = ConsoleHelpRenderer.computeIndent(cmdOptions, width);

    // In the order ConsoleMain tries them, so that "the first one given runs" is the truth.
    List<String[]> workflows = List.of(
        new String[] {"arend [LIBRARY] -d", "Start a daemon"},
        new String[] {"arend [LIBRARY] --daemon-{stop,status,refresh,ping}", "Daemon control"},
        new String[] {"arend [TARGET...] -i [plain|jline]", "REPL"},
        new String[] {"arend [LIBRARY...] {-ss|-ps|-fu|-ch|-sc} ARG...", "Information retrieval (no typecheck)"},
        new String[] {"arend [TARGET...]", "Typecheck"});
    int column = workflows.stream().mapToInt(workflow -> workflow[0].length()).max().orElse(0) + 4;
    System.out.println("Workflows, by precedence: after -h and -v, the first one whose flag is given runs.");
    for (String[] workflow : workflows) {
      System.out.println("  " + workflow[0] + " ".repeat(column - workflow[0].length()) + workflow[1]);
    }
    System.out.println();
    ConsoleHelpRenderer.printWrapped("TARGET is a LIBRARY or a MODULE[:DEF]; a module narrows the typecheck to "
        + "it (or to one definition in it), and the REPL loads it. LIBRARY is a directory containing "
        + "arend.yaml, an arend.yaml file, a .zip library, or a library name, found as <name>/arend.yaml or "
        + "<name>.zip in a library directory (-L). Without a LIBRARY, ./arend.yaml is loaded when present. "
        + "Positionals go before a search flag, which takes the tokens after it up to the next option, and "
        + "before -i unless its kind is given. At most one of -d and the --daemon-* flags may be given.", 0, width);
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
    ConsoleHelpRenderer.printWrapped("On a command a daemon serves, these keep the values given to -d; a value "
        + "given with the command is warned about and ignored (--no-daemon runs it here instead): "
        + LockedFlags.ALL.stream().map(LockedFlags.Flag::display).collect(Collectors.joining(", "))
        + ". A daemon rejects -i and the --daemon-* flags outright.", 0, width);
  }

}
