package org.arend.frontend.cli.daemon;

import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** A daemon starts by resolving its libraries, as IntelliJ opens a project: it typechecks nothing until asked. */
public class DaemonBootstrapTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static <T> T captured(StringBuilder output, Supplier<T> body) {
    PrintStream oldOut = System.out, oldErr = System.err;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream sink = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    System.setOut(sink);
    System.setErr(sink);
    try {
      return body.get();
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      output.append(buffer.toString(StandardCharsets.UTF_8));
    }
  }

  @Test
  public void theBootstrapResolvesAndACommandTypechecks() throws IOException {
    Path lib = tempFolder.newFolder("lib").toPath();
    Files.createDirectories(lib.resolve("src"));
    Files.writeString(lib.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    Files.writeString(lib.resolve("src").resolve("Bad.ard"), "\\func bad : Nat => \\Sigma\n", StandardCharsets.UTF_8);

    StringBuilder bootstrap = new StringBuilder();
    CommandContext ctx = captured(bootstrap, () -> new ConsoleMain().runDaemonBootstrap(new String[]{lib.toString()}));
    assertNotNull(bootstrap.toString(), ctx);
    assertTrue(bootstrap.toString(), bootstrap.toString().contains("[INFO] Resolved lib"));
    assertFalse("nothing is typechecked on starting: " + bootstrap, bootstrap.toString().contains("Typechecking"));
    assertFalse("nor is the type error found: " + bootstrap, bootstrap.toString().contains("Type mismatch"));

    StringBuilder command = new StringBuilder();
    int exitCode = captured(command, () -> Dispatch.run(ctx, new String[]{"Bad"}));
    assertEquals(command.toString(), 1, exitCode);
    assertTrue(command.toString(), command.toString().contains("Type mismatch"));
  }
}
