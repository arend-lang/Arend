package org.arend.cli;

import org.apache.commons.cli.CommandLine;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The CLI flags the daemon captures at bootstrap and applies to every client invocation,
 * overriding any per-call value.
 *
 * <p>One table, because every daemon and the CLI read it: a daemon warns when a command it serves
 * passes one, the CLI's daemon reports them in its {@code status} frame, {@code -d} forwards them
 * to the daemon it starts, and {@code --help} lists them. Here rather than in the CLI because the
 * IntelliJ plugin's daemon warns about the same flags.
 */
public final class LockedFlags {
  /** How a locked flag carries its value on the command line. */
  public enum Kind { BOOLEAN, SINGLE, MULTI }

  /** Short option name, or the long name where there is no short form. */
  public record Flag(String name, Kind kind) {
    /** Display form: {@code -x} or {@code --long}. */
    public String display() {
      return (name.length() == 1 ? "-" : "--") + name;
    }
  }

  public static final List<Flag> ALL = List.of(
      new Flag("L", Kind.MULTI),
      new Flag("s", Kind.SINGLE),
      new Flag("e", Kind.SINGLE),
      new Flag("m", Kind.SINGLE),
      new Flag("c", Kind.BOOLEAN),
      new Flag("r", Kind.BOOLEAN),
      new Flag("serialize", Kind.BOOLEAN));

  private LockedFlags() {}

  /** Warns to {@code err} of each locked flag {@code cmdLine} passes: {@code authority} decides it instead. */
  public static void warnIgnored(CommandLine cmdLine, String authority, PrintStream err) {
    for (Flag flag : ALL) {
      if (cmdLine.hasOption(flag.name())) {
        err.println("[WARN] " + flag.display() + " passed to a daemon-served command is ignored ("
            + authority + " is authoritative; use --no-daemon to override).");
      }
    }
  }

  /**
   * Decodes the daemon's parsed bootstrap command line into {flag → value}, omitting absent flags.
   * Empty when there is none.
   */
  public static Map<String, Object> extract(CommandLine cmd) {
    if (cmd == null) return Map.of();
    Map<String, Object> out = new LinkedHashMap<>();
    for (Flag flag : ALL) {
      if (!cmd.hasOption(flag.name())) continue;
      out.put(flag.name(), switch (flag.kind()) {
        case MULTI -> List.of(cmd.getOptionValues(flag.name()));
        case SINGLE -> cmd.getOptionValue(flag.name());
        case BOOLEAN -> true;
      });
    }
    return out;
  }
}
