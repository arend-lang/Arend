package org.arend.frontend.cli.daemon;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.ConsoleMain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the set of CLI flags that the daemon captures at
 * bootstrap and applies to every client invocation (silently overriding any
 * per-call value).
 *
 * <p>Used in three places that must stay in sync:
 * <ul>
 *   <li>{@code CliDispatcher.run} discards client-supplied values for these flags
 *       and emits a warning when the client passes one.</li>
 *   <li>{@code DaemonServer} reports them in the {@code status} frame so
 *       {@code --daemon-status} surfaces what's actually in force.</li>
 *   <li>{@code ConsoleMain}'s {@code --help} epilogue cites the same list.</li>
 * </ul>
 */
public final class LockedFlags {
  /** Short option names (or long-only names when no short form exists) of locked flags. */
  public static final List<String> NAMES = List.of(
      "L", "s", "e", "m", "c", "r", "no-serialize", "slow-warn"
  );

  private LockedFlags() {}

  /**
   * Decode the daemon's bootstrap argv into a map of {flagName → value}, omitting
   * absent flags. Multi-valued flags ({@code -L}) become a {@code List<String>};
   * boolean flags become {@code Boolean.TRUE}; single-valued flags become their
   * raw string. Returns an empty map when bootstrapArgs is null or unparseable.
   */
  public static Map<String, Object> extract(String[] bootstrapArgs) {
    if (bootstrapArgs == null || bootstrapArgs.length == 0) return Map.of();
    CommandLine cmd = ConsoleMain.parseArgs(bootstrapArgs);
    if (cmd == null) return Map.of();
    Map<String, Object> out = new LinkedHashMap<>();
    if (cmd.hasOption("L"))         out.put("L", List.of(cmd.getOptionValues("L")));
    if (cmd.hasOption("s"))         out.put("s", cmd.getOptionValue("s"));
    if (cmd.hasOption("e"))         out.put("e", cmd.getOptionValue("e"));
    if (cmd.hasOption("m"))         out.put("m", cmd.getOptionValue("m"));
    if (cmd.hasOption("c"))         out.put("c", true);
    if (cmd.hasOption("r"))         out.put("r", true);
    if (cmd.hasOption("no-serialize")) out.put("no-serialize", true);
    if (cmd.hasOption("slow-warn")) out.put("slow-warn", cmd.getOptionValue("slow-warn"));
    return out;
  }

  /** Display form for a flag name (prepends {@code -} or {@code --}). */
  public static String display(String name) {
    return (name.length() == 1 ? "-" : "--") + name;
  }
}
