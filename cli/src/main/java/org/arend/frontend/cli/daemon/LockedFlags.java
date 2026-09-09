package org.arend.frontend.cli.daemon;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.ConsoleMain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The CLI flags the daemon captures at bootstrap and applies to every client invocation,
 * overriding any per-call value.
 *
 * <p>One table, because three places read it: {@code CliDispatcher} warns when a client passes
 * one, {@code DaemonServer} reports them in the {@code status} frame, and {@code --help} lists
 * them.
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

  /**
   * Decodes the daemon's bootstrap argv into {flag → value}, omitting absent flags. Empty when
   * {@code bootstrapArgs} is null or unparseable.
   */
  public static Map<String, Object> extract(String[] bootstrapArgs) {
    if (bootstrapArgs == null || bootstrapArgs.length == 0) return Map.of();
    CommandLine cmd = ConsoleMain.parseArgs(bootstrapArgs).cmdLine();
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
