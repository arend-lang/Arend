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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A context holding several libraries, as a daemon started with {@code arend -d A B} does: what
 * gets loaded, and which of them a later command is about.
 */
public class MultiLibraryWarmContextTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path libDir;
  private Path base;
  private Path app;
  private PrintStream oldOut;
  private PrintStream oldErr;
  private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

  @Before
  public void setUp() throws IOException {
    oldOut = System.out;
    oldErr = System.err;
    // An explicit, empty -L, so nothing under ~/.arend/libs can stand in for a listed library.
    libDir = tempFolder.newFolder("libs").toPath();
    base = library(tempFolder.getRoot().toPath().resolve("base"), "", "Base", "\\func one : Nat => 1\n");
    app = library(tempFolder.getRoot().toPath().resolve("app"), "dependencies: [base]\n", "App",
        "\\import Base\n\\func two : Nat => one\n");
  }

  @After
  public void tearDown() {
    System.setOut(oldOut);
    System.setErr(oldErr);
  }

  private static Path library(Path root, String extraConfig, String module, String body) throws IOException {
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n" + extraConfig, StandardCharsets.UTF_8);
    Files.writeString(root.resolve("src").resolve(module + ".ard"), body, StandardCharsets.UTF_8);
    return root;
  }

  private String captured(Runnable action) {
    captured.reset();
    PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);
    System.setOut(sink);
    System.setErr(sink);
    try {
      action.run();
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  private CommandContext warm(Path... libraries) {
    String[] args = new String[libraries.length + 2];
    args[0] = "-L";
    args[1] = libDir.toString();
    for (int i = 0; i < libraries.length; i++) args[i + 2] = libraries[i].toString();
    CommandContext[] ctx = new CommandContext[1];
    String output = captured(() -> ctx[0] = new ConsoleMain().warmContext(args));
    assertNotNull("loading the context failed:\n" + output, ctx[0]);
    return ctx[0];
  }

  private record Run(int exitCode, String output) {}

  private Run run(CommandContext ctx, String clientCwd, String... args) {
    int[] exitCode = new int[1];
    String output = captured(() -> exitCode[0] = Dispatch.run(ctx, args, clientCwd));
    return new Run(exitCode[0], output);
  }

  /**
   * A library may depend on one listed with it, before or after: the listed copy is used, not a
   * like-named one found through -L, and no -L entry is needed to find it.
   */
  @Test
  public void aListedLibraryIsTheDependencyOfAnother() throws IOException {
    library(libDir.resolve("base"), "", "Decoy", "\\func decoy : Nat => 0\n");
    Run run = run(warm(app, base), null, "App");
    assertEquals("App imports Base, which only the listed copy has:\n" + run.output(), 0, run.exitCode());
  }

  /**
   * Naming libraries picks them; failing that, the directory the command came from does; with no
   * directory to go by, a command covers every library.
   */
  @Test
  public void aCommandCoversTheLibrariesItNamesOrItsDirectorysOrAll() {
    CommandContext ctx = warm(base, app);
    Run here = run(ctx, app.toString());
    assertEquals(here.output(), 0, here.exitCode());
    assertTrue(here.output(), here.output().contains("--- Typechecking app ---"));
    assertFalse(here.output(), here.output().contains("--- Typechecking base ---"));

    Run named = run(ctx, app.toString(), "base");
    assertTrue(named.output(), named.output().contains("--- Typechecking base ---"));
    assertFalse(named.output(), named.output().contains("--- Typechecking app ---"));

    Run all = run(ctx, null);
    assertTrue(all.output(), all.output().contains("--- Typechecking base ---"));
    assertTrue(all.output(), all.output().contains("--- Typechecking app ---"));
  }

  /** A library loaded only as a dependency can be the subject of a command, as one about a shared library is. */
  @Test
  public void aCommandMayBeAboutALibraryLoadedAsADependency() throws IOException {
    Path dependency = library(libDir.resolve("base"), "", "Base", "\\func one : Nat => 1\n");
    CommandContext ctx = warm(app);
    for (Run run : new Run[] { run(ctx, null, "base"), run(ctx, dependency.toString()) }) {
      assertEquals(run.output(), 0, run.exitCode());
      assertTrue(run.output(), run.output().contains("--- Typechecking base ---"));
      assertFalse(run.output(), run.output().contains("--- Typechecking app ---"));
    }
  }
}
