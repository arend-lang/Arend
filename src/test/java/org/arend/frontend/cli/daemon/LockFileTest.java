package org.arend.frontend.cli.daemon;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The lock file is how a client finds the daemon, so the cases that matter are the ones where
 * it is not simply there and well-formed: absent (no daemon), and unreadable (we do not know).
 * A caller that cannot tell those apart starts a second daemon on top of a live one.
 */
public class LockFileTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path lockPath() throws IOException {
    return tempFolder.newFolder("arend").toPath().resolve("daemon.lock");
  }

  private static LockFile sample(Path socket) {
    return new LockFile(4242, "/libs/mylib/arend.yaml", "0123456789abcdef",
        LockFile.PROTOCOL_VERSION, 1_700_000_000_000L, "uds:" + socket);
  }

  @Test
  public void aLockSurvivesTheRoundTrip() throws IOException {
    Path path = lockPath();
    LockFile written = sample(Path.of("/tmp/daemon.sock"));
    written.writeAtomic(path);

    if (!(LockFile.read(path) instanceof LockFile.Read.Found found)) {
      fail("a lock just written must read back as Found");
      return;
    }
    assertEquals(written, found.lock());
  }

  /** Every library survives, in order. */
  @Test
  public void theServedLibrariesSurviveTheRoundTrip() throws IOException {
    Path path = lockPath();
    LockFile written = new LockFile(4242, "/a/arend.yaml", "0123456789abcdef",
        List.of("/a/arend.yaml", "/b/arend.yaml"), LockFile.PROTOCOL_VERSION, 1L, "uds:/s");
    written.writeAtomic(path);
    LockFile read = LockFile.readIfValid(path).orElseThrow();
    assertEquals(written, read);
    assertTrue(read.serves(Path.of("/b/arend.yaml")));
    assertFalse(read.serves(Path.of("/c/arend.yaml")));
  }

  /**
   * A daemon going away removes its lock from every library it served -- and only where the lock
   * is still its own: a library already taken over by another daemon keeps that daemon's lock,
   * the only way left to reach it.
   */
  @Test
  public void deletingAllCopiesSparesALibraryAnotherDaemonOwns() throws IOException {
    Path a = tempFolder.newFolder("a").toPath(), b = tempFolder.newFolder("b").toPath();
    Files.createDirectories(a.resolve(".arend"));
    Files.createDirectories(b.resolve(".arend"));
    LockFile ours = new LockFile(4242, a.resolve("arend.yaml").toString(), "0123456789abcdef",
        List.of(a.resolve("arend.yaml").toString(), b.resolve("arend.yaml").toString()),
        LockFile.PROTOCOL_VERSION, 1L, "uds:/s");
    LockFile theirs = new LockFile(777, b.resolve("arend.yaml").toString(), "fedcba9876543210",
        LockFile.PROTOCOL_VERSION, 1L, "uds:/t");
    ours.writeAtomic(DaemonPaths.lockFileIn(a));
    theirs.writeAtomic(DaemonPaths.lockFileIn(b));

    ours.deleteAllCopies();
    assertFalse(Files.exists(DaemonPaths.lockFileIn(a)));
    assertEquals(theirs, LockFile.readIfValid(DaemonPaths.lockFileIn(b)).orElseThrow());
  }

  /** A daemon is in the registry until it goes away, and going away removes its library's lock too. */
  @Test
  public void aDaemonIsRegisteredUntilItGoesAway() throws IOException {
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      Path library = tempFolder.newFolder("lib").toPath();
      Files.createDirectories(library.resolve(".arend"));
      LockFile lock = new LockFile(4242, library.resolve("arend.yaml").toString(), "0123456789abcdef",
          LockFile.PROTOCOL_VERSION, 1L, "uds:/s");
      Files.createDirectories(DaemonPaths.registryDir());
      lock.writeAtomic(lock.registryFile());
      lock.writeAtomic(DaemonPaths.lockFileIn(library));
      assertEquals(List.of(lock), LockFile.registered());

      lock.deleteAllCopies();
      assertEquals(List.of(), LockFile.registered());
      assertFalse(Files.exists(DaemonPaths.lockFileIn(library)));
    } finally {
      System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    }
  }

  @Test
  public void anAbsentLockIsAbsent() throws IOException {
    assertTrue(LockFile.read(lockPath()) instanceof LockFile.Read.Absent);
  }

  /**
   * The distinction this type exists for. Every one of these is a file that is *there*, so
   * reporting "no daemon" would invite starting a second one.
   */
  @Test
  public void aDamagedLockIsUnreadableRatherThanAbsent() throws IOException {
    List<String> damaged = List.of(
        "",                                                   // empty
        "pid=notanumber\nlibraryPath=/x\nlibraryHash=ab\n",   // pid is not a number
        "pid=1\nlibraryHash=ab\n",                            // no libraryPath
        "pid=1\nlibraryPath=/x\n",                            // no libraryHash
        "pid=1\nlibraryPath=/x\nlibraryHash=ab\nprotocolVersion=x\n");
    for (String content : damaged) {
      Path path = tempFolder.newFile().toPath();
      Files.writeString(path, content, StandardCharsets.UTF_8);
      assertTrue("must not read as a valid lock: <" + content + ">",
          LockFile.read(path) instanceof LockFile.Read.Unreadable);
      assertTrue(LockFile.readIfValid(path).isEmpty());
    }
  }

  /** A reader must never see a partly written lock, and a failed write must not leave litter. */
  @Test
  public void writingLeavesNoScratchFileBehind() throws IOException {
    Path path = lockPath();
    sample(Path.of("/tmp/a.sock")).writeAtomic(path);
    sample(Path.of("/tmp/b.sock")).writeAtomic(path);

    try (var entries = Files.list(path.getParent())) {
      List<Path> extra = entries.filter(p -> !p.equals(path)).toList();
      assertTrue("scratch files left behind: " + extra, extra.isEmpty());
    }
    assertEquals("uds:/tmp/b.sock", LockFile.readIfValid(path).orElseThrow().socketPath());
  }
}
