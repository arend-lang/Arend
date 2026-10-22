package org.arend.query;

import org.arend.frontend.ConsoleMain;
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

import static org.junit.Assert.assertTrue;

/** {@code -sc} on a server that has resolved nothing yet, as a run without a daemon starts. */
public class ScopeInfoToolTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path libRoot;

  @Before
  public void setUp() throws IOException {
    libRoot = tempFolder.newFolder("lib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    writeModule("A", "\\class Base (E : \\Set)\n");
    writeModule("B", "\\import A\n\n\\class Mid \\extends Base {\n  \\func twice : Nat => 2\n}\n");
    writeModule("C", "\\import B\n\n\\class Top \\extends Mid\n");
  }

  private void writeModule(String name, String body) throws IOException {
    Files.writeString(libRoot.resolve("src").resolve(name + ".ard"), body, StandardCharsets.UTF_8);
  }

  /** The command is the context's first, so it meets the server cold. */
  private String coldScope(String target) {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream oldOut = System.out, oldErr = System.err;
    PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);
    System.setOut(sink);
    System.setErr(sink);
    try {
      new ConsoleMain().warmContext(new String[]{libRoot.toString(), "-sc", target});
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  /**
   * What a class inherits is in its scope: a field of a superclass whose module is not imported
   * here, and a dynamic function of a superclass, neither of which the module's own namespace has.
   */
  @Test
  public void aClassScopeHasWhatItInheritsOnAColdServer() {
    String output = coldScope("C:Top");
    assertTrue(output, output.contains("E -> A:Base.E [FIELD]"));
    assertTrue(output, output.contains("twice -> B:Mid.twice [FUNCTION]"));
  }
}
