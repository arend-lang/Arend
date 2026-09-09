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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Where a daemon's state lives, and who can reach it. The hash is the identity a client checks
 * a lock file against, so two libraries must never share one, and one library must produce the
 * same hash however it was named on the command line.
 */
public class DaemonPathsTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path library(String name) throws IOException {
    Path root = tempFolder.newFolder(name).toPath();
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    return root;
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
  public void differentLibrariesHaveDifferentIdentities() throws IOException {
    assertNotEquals(DaemonPaths.resolve(library("a")).libraryHash,
        DaemonPaths.resolve(library("b")).libraryHash);
  }

  /** A synthetic daemon is keyed to its source directory and must not collide with a real one. */
  @Test
  public void aSyntheticDaemonIsDistinctFromTheLibraryAtTheSamePath() throws IOException {
    Path root = library("mylib");
    assertNotEquals(DaemonPaths.resolve(root).libraryHash,
        DaemonPaths.resolveSynthetic(root).libraryHash);
  }

  @Test
  public void somethingThatIsNotALibraryDoesNotResolve() throws IOException {
    assertNull(DaemonPaths.resolve(tempFolder.newFolder("empty").toPath()));
    assertNull(DaemonPaths.resolve(tempFolder.newFile("loose.txt").toPath()));
  }

  /**
   * The .arend directory holds the socket clients connect to; anything that can reach it can run
   * commands against a warm library. On a shared machine the umask default would be
   * world-readable.
   */
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

  @Test
  public void creatingTheStateDirectoryIsIdempotent() throws IOException {
    DaemonPaths paths = DaemonPaths.resolve(library("mylib"));
    paths.ensureArendDir();
    paths.ensureArendDir();
    assertTrue(Files.isDirectory(paths.arendDir));
  }
}
