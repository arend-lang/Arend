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
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for arend-lang/Arend#138: on a reused {@code CommandContext} — a long-lived
 * process's situation, and no other — a name-resolution error was reported only on the run that
 * (re-)resolved the module. Every later run listed the module as fine, printed no error, omitted the
 * {@code Number of modules with errors:} line and exited 0, while the source on disk was
 * still broken.
 *
 * <p>The cause was that the report was a side effect of printing: {@code moduleResults} was
 * filled and {@code exitWithError} set by whatever reached the reporters, and
 * {@code ErrorService.setResolverErrors} pushes to them exactly once, when the module is
 * resolved. "Nothing was printed" and "nothing is wrong" were the same state.
 *
 * <p>They are no longer. The pipeline silences the stream for its own duration and prints each
 * module out of the server's error store once that module has been checked, recording the
 * level as it prints — so the module list and the exit code follow from what the server knows
 * rather than from what happened to be emitted, and a warm run says exactly what a cold one
 * says. The fixture drives the real CLI pipeline, because that contract is the thing under
 * test.
 */
public class WarmServerDiagnosticsTest {
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
    writeModule("Leaf", "\\func leaf : Nat => 0\n");
    writeModule("Mid", "\\import Leaf\n\\func mid : Nat => leaf\n");

    oldOut = System.out;
    oldErr = System.err;
    captured = new ByteArrayOutputStream();
    // The pipeline prints straight to System.out / System.err, so the redirect has to be in
    // place before the context is loaded.
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

  // ───────── fixture helpers ─────────

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
    Path file = libRoot.resolve("src").resolve(name + ".ard");
    long before = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
    Files.writeString(file, body, StandardCharsets.UTF_8);
    // Stamp the mtime a second ahead so the change is unambiguously newer than the previous
    // pass's .arc rather than riding on filesystem timer granularity.
    Files.setLastModifiedTime(file,
        FileTime.fromMillis(Math.max(before, System.currentTimeMillis()) + 1000));
  }

  private void deleteModule(String name) throws IOException {
    Files.delete(libRoot.resolve("src").resolve(name + ".ard"));
  }

  /** One further command against the loaded context. */
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

  private record Run(int exitCode, String output) {
    void assertReportsBrokenModule(String phase, String module, String message) {
      assertTrue(phase + ": expected '" + message + "' in\n" + output, output.contains(message));
      assertTrue(phase + ": expected [✗] " + module + " in\n" + output,
          output.contains("[✗] " + module));
      assertTrue(phase + ": expected the error-count line in\n" + output,
          output.contains("Number of modules with errors:"));
      assertEquals(phase + ": expected a failing exit code for\n" + output, 1, exitCode);
    }

    void assertClean(String phase) {
      assertTrue(phase + ": expected no [ERROR] in\n" + output, !output.contains("[ERROR]"));
      assertTrue(phase + ": expected no error-count line in\n" + output,
          !output.contains("Number of modules with errors:"));
      assertEquals(phase + ": expected exit code 0 for\n" + output, 0, exitCode);
    }

    /** Occurrences of {@code needle}; a diagnostic must be printed exactly once per run. */
    int count(String needle) {
      int n = 0;
      for (int i = output.indexOf(needle); i >= 0; i = output.indexOf(needle, i + needle.length())) n++;
      return n;
    }
  }

  // ───────── tests ─────────

  /** The bug: an unresolved reference must be reported by run 1, run 2 and run 3 alike. */
  @Test
  public void anUnresolvedReferenceIsReportedByEveryRun() throws IOException {
    writeModule("Broken", "\\func broken : Nat => noSuchName\n");

    for (int i = 1; i <= 3; i++) {
      run("lib").assertReportsBrokenModule("run " + i, "Broken", "Cannot resolve reference");
    }
  }

  /** Duplicate names are resolver diagnostics too, and were lost the same way. */
  @Test
  public void aDuplicateNameIsReportedByEveryRun() throws IOException {
    writeModule("Dup", "\\func dup : Nat => 0\n\\func dup : Nat => 1\n");

    for (int i = 1; i <= 3; i++) {
      run("lib").assertReportsBrokenModule("run " + i, "Dup", "Duplicate name");
    }
  }

  /** The contrast class, which always worked: it must keep working. */
  @Test
  public void aTypeErrorIsReportedByEveryRun() throws IOException {
    writeModule("Broken", "\\func broken : Nat => 0 0\n");

    for (int i = 1; i <= 3; i++) {
      run("lib").assertReportsBrokenModule("run " + i, "Broken",
          "Expression is applied to an argument");
    }
  }

  /**
   * Re-emitting a stored diagnostic must not double it up on the run that also produced it
   * live — the run that first resolves the module reports it through both paths.
   */
  @Test
  public void aDiagnosticIsPrintedOncePerRun() throws IOException {
    writeModule("Broken", "\\func broken : Nat => noSuchName\n");

    assertEquals("run 1 must print the error exactly once",
        1, run("lib").count("Cannot resolve reference"));
    assertEquals("run 2 must print the error exactly once",
        1, run("lib").count("Cannot resolve reference"));
  }

  /** …and it must disappear once the source is fixed, rather than being replayed forever. */
  @Test
  public void aFixedModuleStopsBeingReported() throws IOException {
    writeModule("Broken", "\\func broken : Nat => noSuchName\n");
    run("lib").assertReportsBrokenModule("broken run", "Broken", "Cannot resolve reference");

    writeModule("Broken", "\\func broken : Nat => 0\n");
    run("lib").assertClean("after the fix");
    run("lib").assertClean("second run after the fix");
  }

  /** A targeted request (`arend Broken`) must report it too, and fail. */
  @Test
  public void aTargetedRequestReportsTheStoredResolverError() throws IOException {
    writeModule("Broken", "\\func broken : Nat => noSuchName\n");
    run("lib");

    Run second = run("Broken");
    assertTrue("targeted run must report the error:\n" + second.output(),
        second.output().contains("Cannot resolve reference"));
    assertEquals("targeted run must fail", 1, second.exitCode());
  }

  /**
   * A resolution error is printed after the {@code "--- Typechecking ... ---"} banner, so
   * that the natural per-module filter (`arend M | grep -A20 'Typechecking M'`) shows it.
   * It used to be printed by the resolve pass, i.e. before the banner.
   */
  @Test
  public void aResolutionErrorIsPrintedAfterTheBanner() throws IOException {
    writeModule("Broken", "\\func broken : Nat => noSuchName\n");

    String output = run("lib").output();
    int banner = output.indexOf("--- Typechecking lib ---");
    int error = output.indexOf("Cannot resolve reference");
    assertTrue("no banner in\n" + output, banner >= 0);
    assertTrue("the error must follow the banner in\n" + output, error > banner);
  }

  /** A goal must survive the same way an error does, and keep its text. */
  @Test
  public void aGoalIsReportedByEveryRun() throws IOException {
    writeModule("Goals", "\\func g : Nat => {?}\n");

    for (int i = 1; i <= 3; i++) {
      Run r = run("lib");
      assertTrue("run " + i + ": expected a goal in\n" + r.output(), r.output().contains("Goal"));
      assertTrue("run " + i + ": expected [◯] Goals in\n" + r.output(),
          r.output().contains("[◯] Goals"));
      assertEquals("run " + i + ": a goal alone must not fail the run\n" + r.output(),
          0, r.exitCode());
    }
  }

  /**
   * The double-checker reports straight to the context, never through the server's error
   * store, so it has to print and fail on its own account. Guards the one class of diagnostic
   * that the store-driven report cannot see.
   */
  @Test
  public void aDoubleCheckFailureStillFailsTheRun() throws IOException {
    writeModule("Broken", "\\func broken : Nat => noSuchName\n");

    Run r = run("-c", "lib");
    assertEquals("a broken module must still fail under -c:\n" + r.output(), 1, r.exitCode());
  }

  /**
   * A module whose source file is gone must drop out of the report. The server keeps it —
   * nothing rescans the library between requests — so a warm context used to keep listing it
   * (and replaying its diagnostics) forever.
   */
  @Test
  public void aDeletedModuleDisappearsFromTheReport() throws IOException {
    writeModule("Broken", "\\func broken : Nat => noSuchName\n");
    run("lib").assertReportsBrokenModule("before the delete", "Broken", "Cannot resolve reference");

    deleteModule("Broken");
    Run after = run("lib");
    assertTrue("a deleted module must not be listed:\n" + after.output(),
        !after.output().contains("Broken"));
    after.assertClean("after the delete");
  }
}
