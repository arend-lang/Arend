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
        LockFile.PROTOCOL_VERSION, 1_700_000_000_000L, "uds:" + socket, LockFile.UNKNOWN_START);
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
    return new LockFile(pid, "/a/arend.yaml", "0123456789abcdef", LockFile.PROTOCOL_VERSION, 1L, "uds:/s", processStart);
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
