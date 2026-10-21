package org.arend.frontend;

import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Running more than one command against a single {@link CommandContext}.
 *
 * <p>Nothing in the CLI does that yet — a run does one command and exits — but the state a
 * command leaves behind is what decides whether the next one is reported correctly, and that
 * has to be pinned before anything depends on it. What is checked here is the boundary itself:
 * the previous command's exit code, module results and targets must not reach the next.
 */
public class WarmContextTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path libRoot;
  private CommandContext ctx;
  private PrintStream oldOut;
  private PrintStream oldErr;
  private ByteArrayOutputStream captured;

  @Before
  public void setUp() throws IOException {
    libRoot = tempFolder.newFolder("lib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\nbinariesDir: bin\n",
        StandardCharsets.UTF_8);
    writeModule("Good", "\\func good : Nat => 0\n");
    writeModule("Other", "\\func other : Nat => 1\n");

    oldOut = System.out;
    oldErr = System.err;
    captured = new ByteArrayOutputStream();
    redirect();
    ctx = new ConsoleMain().warmContext(new String[]{libRoot.toString()});
    restore();
    assertNotNull("loading the context failed:\n" + captured.toString(StandardCharsets.UTF_8), ctx);
  }

  @After
  public void tearDown() {
    System.setOut(oldOut);
    System.setErr(oldErr);
  }

  private void redirect() {
    PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);
    System.setOut(sink);
    System.setErr(sink);
  }

  private void restore() {
    System.setOut(oldOut);
    System.setErr(oldErr);
  }

  private void writeModule(String name, String body) throws IOException {
    Files.writeString(libRoot.resolve("src").resolve(name + ".ard"), body, StandardCharsets.UTF_8);
  }

  private record Run(int exitCode, String output) {}

  private Run run(String... args) {
    captured.reset();
    redirect();
    int exitCode;
    try {
      exitCode = Dispatch.run(ctx, args);
    } finally {
      restore();
    }
    return new Run(exitCode, captured.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void aCommandRunsAgainstTheLoadedContext() {
    Run good = run("Good");
    assertEquals(good.output(), 0, good.exitCode());
    assertTrue(good.output(), good.output().contains("--- Typechecking Good ---"));
  }

  /** The module -p prints is checked too when the command names others, and the run says so. */
  @Test
  public void aPrintTargetOutsideTheScopeIsCheckedToo() {
    Run run = run("Good", "-p", "Other:other");
    assertEquals(run.output(), 0, run.exitCode());
    assertTrue(run.output(), run.output().contains("[INFO] -p target Other:other is outside the typecheck scope; adding Other to it"));
    assertTrue(run.output(), run.output().contains("--- Typechecking Other ---"));
  }

  /** A path to no library loaded here fails, as a local run fails on one that is no library. */
  @Test
  public void aPathToNoLibraryLoadedFails() throws IOException {
    Path file = tempFolder.newFile("notes.txt").toPath();
    Run notALibrary = run(file.toString());
    assertEquals(notALibrary.output(), 1, notALibrary.exitCode());
    assertTrue(notALibrary.output(), notALibrary.output().contains("not a library"));

    Path other = tempFolder.newFolder("other").toPath();
    Files.writeString(other.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    Run notLoaded = run(other.toString());
    assertEquals(notLoaded.output(), 1, notLoaded.exitCode());
    assertTrue(notLoaded.output(), notLoaded.output().contains("does not serve"));
  }

  /** A definition the module does not declare is an error, not a typecheck of nothing that succeeds. */
  @Test
  public void aMissingDefinitionFails() {
    Run missing = run("Good:nope");
    assertEquals(missing.output(), 1, missing.exitCode());
    assertTrue(missing.output(), missing.output().contains("Definition nope is not found in"));

    Run present = run("Good:good");
    assertEquals(present.output(), 0, present.exitCode());
  }

  /** The scope of one command must not widen the next: `Good` then `Other` targets only Other. */
  @Test
  public void targetsDoNotAccumulateAcrossCommands() {
    run("Good");
    Run other = run("Other");
    assertTrue(other.output(), other.output().contains("--- Typechecking Other ---"));
    assertTrue("the previous command's target must not still be in scope:\n" + other.output(),
        !other.output().contains("--- Typechecking Good ---"));
  }

  /** A failed command must not make the next one fail. */
  @Test
  public void aFailureDoesNotLeakIntoTheNextCommand() {
    Run bad = run("No-Such-Module");
    assertEquals(bad.output(), 1, bad.exitCode());
    assertTrue(bad.output(), bad.output().contains("No-Such-Module"));
    assertTrue("a rejected target must not have widened the scope to the whole library:\n"
        + bad.output(), !bad.output().contains("--- Typechecking lib ---"));

    Run good = run("Good");
    assertEquals("the next command must not inherit the failure:\n" + good.output(),
        0, good.exitCode());
  }

  /** parseArgs served the request in full; that is not a failure. */
  @Test
  public void helpSucceedsAndABadFlagDoesNot() {
    assertEquals(0, run("--help").exitCode());
    assertEquals(1, run("--no-such-flag").exitCode());
  }

  /** A whole-library command still means the whole library. */
  @Test
  public void aCommandWithNoTargetTypechecksTheLibrary() {
    Run all = run(libRoot.toString());
    assertEquals(all.output(), 0, all.exitCode());
    assertTrue(all.output(), all.output().contains("--- Typechecking lib ---"));
  }
}
