package org.arend.frontend;

import org.apache.commons.cli.CommandLine;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Which commands may be handed to a daemon. Routing happens before the argv is classified, so it
 * is decided from the flags alone -- and a command the daemon cannot serve has to be decided here,
 * not discovered there: {@code tryRouteCli} returns the daemon's exit code and the client exits
 * with it, so a refusal on the far side is the answer the user gets, not a fallback.
 */
public class RoutabilityTest {
  private static CommandLine parse(String... args) {
    CommandLine cmdLine = ConsoleMain.parseArgs(args).cmdLine();
    assertNotNull("arguments should have parsed: " + String.join(" ", args), cmdLine);
    return cmdLine;
  }

  @Test
  public void anOrdinaryTypecheckOrRetrievalIsRoutable() {
    for (String[] argv : List.of(
        new String[] { "arend-lib" },
        new String[] { "arend-lib", "Data.Bool" },
        new String[] { "arend-lib", "-ss", "Monoid" })) {
      assertTrue(String.join(" ", argv), ConsoleMain.isRoutable(parse(argv)));
    }
  }

  /**
   * {@code --no-daemon} is the explicit opt-out. The REPL is interactive and the daemon refuses
   * it outright, so routing it would take the REPL away from anyone with a daemon running.
   * {@code -s} names the sources to check and is read only where targets are classified for a
   * cold context, so a served command silently ignores it and reports success for the daemon's
   * own library -- being a locked flag makes that worse, not better: locking is for flags that
   * shape the server, and this one chooses the work.
   */
  @Test
  public void noDaemonTheReplAndCommandsNamingTheirOwnSourcesAreNot() {
    for (String[] argv : List.of(
        new String[] { "arend-lib", "--no-daemon" },
        new String[] { "-i" },
        new String[] { "arend-lib", "-i" },
        new String[] { "-i", "jline" },
        new String[] { "-s", "../scratch" },
        new String[] { "-s", "src", "-e", "ext" })) {
      assertFalse(String.join(" ", argv), ConsoleMain.isRoutable(parse(argv)));
    }
  }
}
