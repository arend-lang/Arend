package org.arend.frontend.cli;

import org.arend.ext.module.ModulePath;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * What a warm context carries from one command into the next. A daemon reuses one context for
 * every client, so anything {@code beginCommand} forgets to clear is one client's state showing
 * up in another's output.
 */
public class CommandContextTest {
  /** Runs {@code body} with stderr captured. */
  private static String capturingErr(Runnable body) {
    PrintStream realErr = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      body.run();
    } finally {
      System.setErr(realErr);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  /**
   * A command that ends without {@code finishProgressLine} -- cancelled, or unwound by a throw --
   * leaves a partial line open, and the next command emits a stray blank line before its first
   * diagnostic.
   */
  @Test
  public void beginCommandDoesNotLeaveAProgressLineOpen() {
    CommandContext ctx = new CommandContext();
    capturingErr(() -> ctx.reportModuleProgress(0, 2, ModulePath.fromString("Foo")));
    ctx.beginCommand();
    assertEquals("a new command must not inherit an open progress line",
        "", capturingErr(ctx::finishProgressLine));
  }

  /**
   * The padding that erases the previous line is measured against the previous module name; kept
   * across commands, it pads the next client's first line to a name from somebody else's run.
   */
  @Test
  public void beginCommandForgetsThePreviousProgressWidth() {
    CommandContext ctx = new CommandContext();
    capturingErr(() -> ctx.reportModuleProgress(0, 2, ModulePath.fromString("AVeryLongModuleName")));
    ctx.beginCommand();
    String fresh = capturingErr(() -> ctx.reportModuleProgress(0, 2, ModulePath.fromString("A")));
    assertFalse("a fresh progress line must not be padded to the last command's width: <" + fresh + ">",
        fresh.contains("  "));
  }
}
