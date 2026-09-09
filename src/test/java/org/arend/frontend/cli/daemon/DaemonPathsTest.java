package org.arend.frontend.cli.daemon;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Where a daemon's state lives, and who can reach it. The hash is the identity a client checks a
 * lock file against, so one library must produce the same hash however it was named.
 */
public class DaemonPathsTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  /** Canonicalised, because every path {@link DaemonPaths} returns has been through toRealPath. */
  private Path library(String name) throws IOException {
    Path root = tempFolder.newFolder(name).toPath();
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    return root.toRealPath();
  }

  @Test
  public void stateLivesUnderTheLibrarysArendDirectory() throws IOException {
    Path root = library("mylib");
    DaemonPaths paths = DaemonPaths.resolve(root);
    assertEquals(root.resolve(".arend"), paths.arendDir);
    assertEquals(root.resolve(".arend").resolve("daemon.lock"), paths.lockFile);
    assertEquals(root.resolve(".arend").resolve("daemon.sock"), paths.socketFile);
    assertEquals(root.resolve(".arend").resolve("daemon.log"), paths.logFile);
  }

  /** A directory, its arend.yaml, and an uncanonical route to either are the same daemon. */
  @Test
  public void oneLibraryHasOneIdentityHoweverItIsNamed() throws IOException {
    Path root = library("mylib");
    String viaDir = DaemonPaths.resolve(root).libraryHash;
    String viaConfig = DaemonPaths.resolve(root.resolve("arend.yaml")).libraryHash;
    String viaDetour = DaemonPaths.resolve(root.resolve("src").resolve("..")).libraryHash;
    assertEquals(viaDir, viaConfig);
    assertEquals(viaDir, viaDetour);
  }

  @Test
  public void somethingThatIsNotALibraryDoesNotResolve() throws IOException {
    assertNull(DaemonPaths.resolve(tempFolder.newFolder("empty").toPath()));
    assertNull(DaemonPaths.resolve(tempFolder.newFile("loose.txt").toPath()));
  }

  /** The directory holds the socket, so its permissions are the daemon's access control. */
  @Test
  public void theStateDirectoryIsPrivateToItsOwner() throws IOException {
    Path root = library("mylib");
    DaemonPaths paths = DaemonPaths.resolve(root);
    paths.ensureArendDir();
    assertTrue(Files.isDirectory(paths.arendDir));
    if (Files.getFileStore(paths.arendDir).supportsFileAttributeView("posix")) {
      assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(paths.arendDir)));
    }
  }
}
