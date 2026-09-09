package org.arend.frontend;

import org.apache.commons.cli.CommandLine;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Which commands may be handed to a daemon.
 *
 * <p>Routing happens before the argv is classified, so it has to be decided from the flags
 * alone -- and a command the daemon cannot serve must be decided here, not discovered there.
 * The daemon's refusal is not a fallback: {@code tryRouteCli} returns the daemon's exit code,
 * and the client exits with it.
 */
public class RoutabilityTest {
  private static CommandLine parse(String... args) {
    CommandLine cmdLine = ConsoleMain.parseArgs(args).cmdLine();
    assertNotNull("arguments should have parsed: " + String.join(" ", args), cmdLine);
    return cmdLine;
  }

  @Test
  public void anOrdinaryTypecheckIsRoutable() {
    assertTrue(ConsoleMain.isRoutable(parse("arend-lib")));
    assertTrue(ConsoleMain.isRoutable(parse("arend-lib", "Data.Bool")));
  }

  @Test
  public void aRetrievalQueryIsRoutable() {
    assertTrue(ConsoleMain.isRoutable(parse("arend-lib", "-ss", "Monoid")));
  }

  @Test
  public void noDaemonOptsOut() {
    assertFalse(ConsoleMain.isRoutable(parse("arend-lib", "--no-daemon")));
  }

  /**
   * The REPL is interactive and the daemon refuses it outright. Routed anyway, {@code arend -i}
   * inside a library that has a daemon exits 1 with "not supported in daemon mode" and never
   * starts a REPL -- so having a daemon running takes the REPL away.
   */
  @Test
  public void theReplIsNotRoutable() {
    assertFalse(ConsoleMain.isRoutable(parse("-i")));
    assertFalse(ConsoleMain.isRoutable(parse("arend-lib", "-i")));
    assertFalse(ConsoleMain.isRoutable(parse("-i", "jline")));
  }

  /**
   * {@code -s} names the sources to check. It is read only where targets are classified for a
   * cold context, so a daemon-served command silently ignores it and checks its own library
   * instead -- reporting success for work on a completely different target. That it is a locked
   * flag makes it worse, not better: locking is for flags that shape the server, and this one
   * chooses what gets typechecked.
   */
  @Test
  public void aCommandThatNamesItsOwnSourcesIsNotRoutable() {
    assertFalse(ConsoleMain.isRoutable(parse("-s", "../scratch")));
    assertFalse(ConsoleMain.isRoutable(parse("-s", "src", "-e", "ext")));
  }
}
