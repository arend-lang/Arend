package org.arend.daemon;

import org.arend.daemon.client.DaemonRpc;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A daemon outlives the jar it was started from: Gradle rewrites it in place, an installer replaces
 * it with a new file. Either way the jar is no longer the one the daemon runs, and that is what this
 * has to tell.
 */
public class BuildIdentityTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path jar() throws IOException {
    Path jar = tempFolder.newFile("arend.jar").toPath();
    Files.write(jar, new byte[] { 1, 2, 3 });
    Files.setLastModifiedTime(jar, FileTime.fromMillis(1_000_000L));
    return jar;
  }

  @Test
  public void anUntouchedJarIsCurrent() throws IOException {
    BuildIdentity build = BuildIdentity.of(jar());
    assertNotNull(build);
    assertTrue(build.isCurrent());
  }

  /** What Gradle does: the same file, written over. */
  @Test
  public void aJarRewrittenInPlaceIsNotCurrent() throws IOException {
    Path jar = jar();
    BuildIdentity build = BuildIdentity.of(jar);
    Files.write(jar, new byte[] { 1, 2, 3, 4 });
    assertFalse(build.isCurrent());
  }

  /** What an installer does: a new file renamed over the old one, the size and time even kept. */
  @Test
  public void aJarReplacedByAnotherFileIsNotCurrent() throws IOException {
    Path jar = jar();
    BuildIdentity build = BuildIdentity.of(jar);
    Path next = tempFolder.newFile("next.jar").toPath();
    Files.write(next, new byte[] { 1, 2, 3 });
    Files.setLastModifiedTime(next, FileTime.fromMillis(1_000_000L));
    Files.move(next, jar, StandardCopyOption.REPLACE_EXISTING);
    assertFalse("another file, though its size and time are the same", build.isCurrent());
  }

  @Test
  public void aDeletedJarIsNotCurrent() throws IOException {
    Path jar = jar();
    BuildIdentity build = BuildIdentity.of(jar);
    Files.delete(jar);
    assertFalse(build.isCurrent());
  }

  /** A client of another build runs its command itself; one that cannot tell routes as before. */
  @Test
  public void aDaemonOfAnotherBuildIsToldApart() throws IOException {
    BuildIdentity ours = BuildIdentity.of(jar());
    BuildIdentity same = new BuildIdentity(ours.path(), ours.size(), ours.modified(), ours.key());
    BuildIdentity older = new BuildIdentity(ours.path(), ours.size() + 1, ours.modified() - 1, ours.key());
    BuildIdentity elsewhere = new BuildIdentity("/opt/arend-1.11.jar", ours.size(), ours.modified(), ours.key());
    assertNull(DaemonRpc.otherBuild(lockWith(same), ours));
    assertTrue(DaemonRpc.otherBuild(lockWith(older), ours).contains("changed"));
    assertTrue(DaemonRpc.otherBuild(lockWith(elsewhere), ours).contains("/opt/arend-1.11.jar"));
    assertNull("an IDE's daemon says nothing of its build", DaemonRpc.otherBuild(lockWith(null), ours));
    assertNull("a client run from class directories cannot tell", DaemonRpc.otherBuild(lockWith(older), null));
  }

  private static LockFile lockWith(BuildIdentity build) {
    return new LockFile(1, "/a/arend.yaml", "0123456789abcdef", List.of("/a/arend.yaml"), List.of(),
        LockFile.PROTOCOL_VERSION, 1L, "uds:/s", LockFile.HOST_CLI, LockFile.UNKNOWN_START, "", build);
  }
}
