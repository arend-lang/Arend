package org.arend.frontend.cli;

import org.arend.frontend.ConsoleMain;
import org.arend.typechecking.computation.CancellationIndicator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A cancelled typecheck is not a finished one: the modules it never reached did not pass, and
 * caching that writes the gap to disk for every later run to load as though it had been checked.
 * The daemon makes this reachable in the ordinary way -- Ctrl-C on a routed command.
 */
public class CancelledRunTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final CancellationIndicator CANCELLED = new CancellationIndicator() {
    @Override public boolean isCanceled() { return true; }
    @Override public void cancel() { }
  };

  private Path library() throws IOException {
    Path root = tempFolder.newFolder("lib").toPath();
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\nbinariesDir: bin\n",
        StandardCharsets.UTF_8);
    Files.writeString(root.resolve("src").resolve("Good.ard"), "\\func good : Nat => 0\n",
        StandardCharsets.UTF_8);
    return root;
  }

  private static List<Path> binaries(Path root) throws IOException {
    Path bin = root.resolve("bin");
    if (!Files.isDirectory(bin)) return List.of();
    try (Stream<Path> walk = Files.walk(bin)) {
      return walk.filter(p -> p.getFileName().toString().endsWith(".arc")).toList();
    }
  }

  @Test
  public void aCancelledRunWritesNoBinaryCaches() throws IOException {
    Path root = library();
    CommandContext ctx = new ConsoleMain()
        .runDaemonBootstrap(new String[] { root.toString(), "--serialize" });
    assertNotNull("bootstrap failed", ctx);

    // Baseline: the fixture really does write caches when a run completes.
    assertFalse("the fixture must produce caches on a completed run", binaries(root).isEmpty());
    for (Path arc : binaries(root)) Files.delete(arc);
    assertTrue(binaries(root).isEmpty());

    ctx.cancellation = CANCELLED;
    Dispatch.run(ctx, new String[] { root.toString() });

    assertTrue("a cancelled run must not persist .arc caches: " + binaries(root),
        binaries(root).isEmpty());
  }
}
