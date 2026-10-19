package org.arend.daemon.server;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Where a socket goes when its library's path is too long for one: a directory of the user's own in
 * the shared temp directory, whose name anybody could have taken first.
 */
public class SocketBinderShortPathTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static String user() {
    return System.getProperty("user.name", "user").replaceAll("[^A-Za-z0-9._-]", "_");
  }

  @Test
  public void aDirectoryOfOurOwnIsMadePrivate() throws IOException {
    assumeTrue(!System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
    Path tmp = tempFolder.newFolder("tmp").toPath();
    Path dir = Files.createDirectory(tmp.resolve("arend-" + user()));
    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));

    assertEquals(dir.resolve("tag.sock"), SocketBinder.shortSocketPath("tag", null, tmp.toString()));
    assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
  }

  /**
   * Another user's directory of that name is not used: they could swap the socket in it for their
   * own. Here the name is a link to the root directory, which stands in for theirs.
   */
  @Test
  public void aDirectoryAnotherUserOwnsIsNotUsed() throws IOException {
    assumeTrue(!System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
    assumeFalse("root owns everything", "root".equals(System.getProperty("user.name")));
    Path tmp = tempFolder.newFolder("tmp").toPath();
    Files.createSymbolicLink(tmp.resolve("arend-" + user()), Path.of("/"));
    assertNull(SocketBinder.shortSocketPath("tag", null, tmp.toString()));
  }
}
