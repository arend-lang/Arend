package org.arend.frontend.cli.daemon;

import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.PrivateFiles;
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
    return new LockFile(4242, "/libs/mylib/arend.yaml", "0123456789abcdef", List.of("/libs/mylib/arend.yaml"), List.of(), LockFile.PROTOCOL_VERSION, 1_700_000_000_000L, "uds:" + socket, LockFile.HOST_CLI);
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

  /** An IDE's lock says so, since its PID must never be signalled; a lock that says nothing is a CLI daemon's. */
  @Test
  public void theHostSurvivesTheRoundTrip() throws IOException {
    Path path = lockPath();
    LockFile ide = new LockFile(4242, "/a/arend.yaml", "0123456789abcdef", List.of("/a/arend.yaml"), List.of(),
        LockFile.PROTOCOL_VERSION, 1L, "uds:/s", LockFile.HOST_INTELLIJ);
    ide.writeAtomic(path);
    LockFile read = LockFile.readIfValid(path).orElseThrow();
    assertEquals(ide, read);
    assertTrue(read.isHostedByIde());

    Files.writeString(path, Files.readString(path).replaceAll("(?m)^host=.*\\R", ""));
    LockFile old = LockFile.readIfValid(path).orElseThrow();
    assertEquals(LockFile.HOST_CLI, old.host());
    assertFalse(old.isHostedByIde());
  }

  /** Every library survives, in order. */
  @Test
  public void theServedLibrariesSurviveTheRoundTrip() throws IOException {
    Path path = lockPath();
    LockFile written = new LockFile(4242, "/a/arend.yaml", "0123456789abcdef",
        List.of("/a/arend.yaml", "/b/arend.yaml"), List.of(), LockFile.PROTOCOL_VERSION, 1L, "uds:/s", LockFile.HOST_CLI);
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
        List.of(a.resolve("arend.yaml").toString(), b.resolve("arend.yaml").toString()), List.of(),
        LockFile.PROTOCOL_VERSION, 1L, "uds:/s", LockFile.HOST_CLI);
    LockFile theirs = new LockFile(777, b.resolve("arend.yaml").toString(), "fedcba9876543210", List.of(b.resolve("arend.yaml").toString()), List.of(), LockFile.PROTOCOL_VERSION, 1L, "uds:/t", LockFile.HOST_CLI);
    ours.writeAtomic(DaemonPaths.lockFileIn(a));
    theirs.writeAtomic(DaemonPaths.lockFileIn(b));

    ours.deleteAllCopies();
    assertFalse(Files.exists(DaemonPaths.lockFileIn(a)));
    assertEquals(theirs, LockFile.readIfValid(DaemonPaths.lockFileIn(b)).orElseThrow());
  }

  /**
   * A shared library gets no copy of the lock -- it is no daemon's -- but the registry gets one,
   * and the daemon going away removes that too.
   */
  @Test
  public void aSharedLibraryIsInTheRegistryAndNotInItsOwnDirectory() throws IOException {
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      Path owned = tempFolder.newFolder("owned").toPath();
      String sharedConfig = config.resolve("libs/sharedlib/arend.yaml").toString();
      LockFile lock = new LockFile(4242, owned.resolve("arend.yaml").toString(), "0123456789abcdef",
          List.of(sharedConfig, owned.resolve("arend.yaml").toString()), List.of(sharedConfig),
          LockFile.PROTOCOL_VERSION, 1L, "uds:/s", LockFile.HOST_CLI);
      assertEquals(List.of(DaemonPaths.lockFileIn(owned)), lock.lockFiles());
      assertTrue(lock.hasLoaded(Path.of(sharedConfig)));

      PrivateFiles.ensurePrivateDirectory(DaemonPaths.registryDir());
      lock.writeAtomic(lock.registryFile());
      assertEquals(List.of(lock), LockFile.registered());
      lock.deleteAllCopies();
      assertEquals(List.of(), LockFile.registered());
    } finally {
      System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    }
  }

  /** A daemon is in the registry until it goes away, and going away removes its library's lock too. */
  @Test
  public void aDaemonIsRegisteredUntilItGoesAway() throws IOException {
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      Path library = tempFolder.newFolder("lib").toPath();
      Files.createDirectories(library.resolve(".arend"));
      LockFile lock = new LockFile(4242, library.resolve("arend.yaml").toString(), "0123456789abcdef", List.of(library.resolve("arend.yaml").toString()), List.of(), LockFile.PROTOCOL_VERSION, 1L, "uds:/s", LockFile.HOST_CLI);
      PrivateFiles.ensurePrivateDirectory(DaemonPaths.registryDir());
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

  /** A registry others may write is no registry: anything in it may be their plant. */
  @Test
  public void aRegistryOthersCanWriteIsIgnored() throws IOException {
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      LockFile lock = ofProcess(4242, 1L);
      PrivateFiles.ensurePrivateDirectory(DaemonPaths.registryDir());
      lock.writeAtomic(lock.registryFile());
      assertEquals(List.of(lock), LockFile.registered());
      Files.setPosixFilePermissions(DaemonPaths.registryDir(), java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxr-x"));
      assertEquals(List.of(), LockFile.registered());
      // A daemon writing its lock takes the directory back.
      PrivateFiles.ensurePrivateDirectory(DaemonPaths.registryDir());
      assertEquals(List.of(lock), LockFile.registered());
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

  private static LockFile ofProcess(long pid, long processStart) {
    return new LockFile(pid, "/a/arend.yaml", "0123456789abcdef", List.of("/a/arend.yaml"), List.of(),
        LockFile.PROTOCOL_VERSION, 1L, "uds:/s", LockFile.HOST_CLI, processStart, "", null);
  }

  /**
   * A PID outlives its process in a lock, and is reused: the process it names then is somebody
   * else's, which --daemon-stop would signal. A lock names the process that started when it says.
   */
  @Test
  public void aReusedPidIsNotTheDaemon() {
    ProcessHandle self = ProcessHandle.current();
    long started = self.info().startInstant().orElseThrow().toEpochMilli();
    assertTrue(ofProcess(self.pid(), started).processAlive());
    assertTrue(ofProcess(self.pid(), started).process().isPresent());
    assertFalse("a process that started an hour later is another one",
        ofProcess(self.pid(), started + 3_600_000L).processAlive());
    assertTrue(ofProcess(self.pid(), started + 3_600_000L).process().isEmpty());
    // A lock from before processStart is judged by its PID alone, as it always was.
    assertTrue(ofProcess(self.pid(), LockFile.UNKNOWN_START).processAlive());
  }

  /**
   * The token lets whoever has it drive the daemon, so it goes to the registry -- the user's own
   * directory -- and to no library's copy, which may sit anywhere.
   */
  @Test
  public void onlyTheRegistrysCopyCarriesTheToken() throws IOException {
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, tempFolder.newFolder("config").toString());
    try {
      Path root = tempFolder.newFolder("lib").toPath();
      Files.writeString(root.resolve("arend.yaml"), "", StandardCharsets.UTF_8);
      DaemonPaths paths = DaemonPaths.resolve(root);
      paths.ensureArendDir();
      String token = LockFile.newToken();
      LockFile lock = LockFile.ofThisProcess(paths, List.of(paths.libraryConfig.toString()), List.of(), 1L, "uds:/s",
          LockFile.HOST_CLI, token);
      lock.writeEverywhere();

      assertEquals("", LockFile.readIfValid(paths.lockFile).orElseThrow().token());
      assertFalse(Files.readString(paths.lockFile).contains(token));
      assertEquals(token, LockFile.readIfValid(lock.registryFile()).orElseThrow().token());
      assertEquals(java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
          java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(lock.registryFile()));
      assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(
          Files.getPosixFilePermissions(DaemonPaths.registryDir())));
      // Looked up through the library, the lock is the registry's, token and all.
      assertEquals(lock, ((LockFile.Read.Found) LockFile.lookup(paths)).lock());
    } finally {
      System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    }
  }

  @Test
  public void theProcessStartSurvivesTheRoundTrip() throws IOException {
    Path path = lockPath();
    LockFile written = ofProcess(4242, 1_700_000_000_123L);
    written.writeAtomic(path);
    assertEquals(written, LockFile.readIfValid(path).orElseThrow());

    Files.writeString(path, Files.readString(path).replaceAll("(?m)^processStart=.*\\R", ""));
    assertEquals(LockFile.UNKNOWN_START, LockFile.readIfValid(path).orElseThrow().processStart());
  }
}
