package org.arend.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
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

public class ArendOptionsTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();
  private static CommandLine parse(String... args) throws ParseException {
    return new DefaultParser().parse(ArendOptions.create(), args);
  }

  /** A relative --log-file is the client's, however the option is spelt; an absolute one is as given. */
  @Test
  public void aRelativeLogFileIsTheClients() throws ParseException {
    assertEquals(Paths.get("/client/out.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-file", "out.log"), "/client"));
    assertEquals(Paths.get("/client/out.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-f=out.log"), "/client"));
    assertEquals(Paths.get("/abs.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-file", "/abs.log"), "/client"));
    assertEquals(Paths.get("out.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-file", "out.log"), null));
  }

  /**
   * The default log is no longer in the shared temp directory, where anybody could have put a link
   * of its name to a file of the user's for the next query to overwrite.
   */
  @Test
  public void theDefaultLogIsInADirectoryOfTheUsersOwn() throws ParseException, IOException {
    assumeTrue(!System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      Path log = ArendOptions.jsonLogPath(parse("-ss", "x", "--json"), "/client");
      assertEquals(config.resolve("log").resolve("arend-symbol-search.log"), log);
      try (OutputStream out = ArendOptions.openJsonLog(log)) {
        out.write(1);
      }
      assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(log.getParent())));
    } finally {
      System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    }
  }
}
