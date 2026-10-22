package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.ConsoleMain;
import org.arend.daemon.DaemonPaths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class JsonOutputRedirectTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();
  private static CommandLine parse(String... args) {
    return ConsoleMain.parseArgs(args).cmdLine();
  }

  /** A relative --log-file is the client's, however the option is spelt; an absolute one is as given. */
  @Test
  public void aRelativeLogFileIsTheClients() {
    assertEquals(Paths.get("/client/out.log"), JsonOutputRedirect.jsonLogPath(parse("-ss", "x", "--log-file", "out.log"), "/client"));
    assertEquals(Paths.get("/client/out.log"), JsonOutputRedirect.jsonLogPath(parse("-ss", "x", "--log-f=out.log"), "/client"));
    assertEquals(Paths.get("/abs.log"), JsonOutputRedirect.jsonLogPath(parse("-ss", "x", "--log-file", "/abs.log"), "/client"));
    assertEquals(Paths.get("out.log"), JsonOutputRedirect.jsonLogPath(parse("-ss", "x", "--log-file", "out.log"), null));
  }

  /**
   * The default log is no longer in the shared temp directory, where anybody could have put a link
   * of its name to a file of the user's for the next query to overwrite.
   */
  @Test
  public void theDefaultLogIsInADirectoryOfTheUsersOwn() throws IOException {
    assumeTrue(!System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      Path log = JsonOutputRedirect.jsonLogPath(parse("-ss", "x", "--json"), "/client");
      assertEquals(config.resolve("log").resolve("arend-symbol-search.log"), log);
      try (OutputStream out = JsonOutputRedirect.openJsonLog(log)) {
        out.write(1);
      }
      assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(log.getParent())));
    } finally {
      System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    }
  }
}
