package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.daemon.client.DaemonClient;
import org.arend.frontend.cli.daemon.server.DaemonServer;
import org.arend.frontend.cli.daemon.server.SocketBinder;
import org.arend.frontend.cli.daemon.wire.SocketAddress;
import org.arend.frontend.cli.daemon.server.StreamRedirector;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * What the daemon does to a cancelled task, and what it does to every <em>other</em> task while
 * doing it. The command runner is injected so a task can be held open as long as a test needs,
 * which is the only way to observe a request arriving while another is still running.
 */
public class DaemonCancellationTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private DaemonServer server;
  private SocketAddress address;

  /** Released to let a held command finish. */
  private final CountDownLatch release = new CountDownLatch(1);
  /** Counts down as each command actually starts running on the worker. */
  private final CountDownLatch firstStarted = new CountDownLatch(1);
  /** Commands that ran to completion, so a test can tell "cancelled" from "never reached". */
  private final List<String> completed = new ArrayList<>();
  /** Set if a held command ever observes a cancellation aimed at somebody else. */
  private final java.util.concurrent.atomic.AtomicBoolean heldSawCancel =
      new java.util.concurrent.atomic.AtomicBoolean();

  @Before
  public void setUp() throws IOException {
    assumeTrue("Unix domain sockets required",
        !System.getProperty("os.name", "").toLowerCase().startsWith("windows"));

    Path sock = tempFolder.newFolder("state").toPath().resolve("daemon.sock");
    SocketBinder.Bound bound = SocketBinder.bind(sock, "0123456789abcdef");
    address = SocketAddress.parse(bound.address());
    CommandContext ctx = new CommandContext();

    // "hold"  — blocks until the test releases it, then returns 0 like a normal command.
    // "watch" — spins until it observes cancellation, then returns 0 *without* throwing, which
    //           is what the real pipeline does: ComputationInterruptedException is swallowed
    //           inside base/ and never reaches the worker.
    DaemonServer.CommandRunner runner = (context, args, cwd) -> {
      String name = args.length == 0 ? "" : args[0];
      try {
        if (name.startsWith("hold")) {
          firstStarted.countDown();
          // Poll rather than block, so that a cancel flag set behind this command's back is
          // actually seen. A command that merely blocks cannot witness the bug.
          long deadline = System.currentTimeMillis() + 30_000;
          while (release.getCount() > 0 && System.currentTimeMillis() < deadline) {
            if (context.cancellation.isCanceled()) heldSawCancel.set(true);
            Thread.sleep(5);
          }
        } else if (name.startsWith("watch")) {
          firstStarted.countDown();
          long deadline = System.currentTimeMillis() + 30_000;
          while (!context.cancellation.isCanceled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      synchronized (completed) { completed.add(name); }
      return 0;
    };

    server = new DaemonServer(bound, ctx, "test-library", runner);
    server.start();
  }

  @After
  public void tearDown() {
    release.countDown();
    if (server != null) server.close();
    StreamRedirector.uninstall();
  }

  /** One request on its own connection, run on its own thread so the test can keep talking. */
  private final class Call {
    final String id = UUID.randomUUID().toString();
    private final AtomicReference<Integer> exitCode = new AtomicReference<>();
    private final StringBuilder output = new StringBuilder();
    private final CountDownLatch done = new CountDownLatch(1);
    private final Thread thread;

    Call(String... args) {
      thread = new Thread(() -> {
        try (DaemonClient client = DaemonClient.connect(address)) {
          int rc = client.invoke(id, "cli", Map.of("args", List.of(args)), frame -> {
            Object data = frame.get("data");
            if (data != null) synchronized (output) { output.append(data); }
          }, null);
          exitCode.set(rc);
        } catch (IOException e) {
          exitCode.set(-99);
        } finally {
          done.countDown();
        }
      }, "call-" + args[0]);
      thread.setDaemon(true);
      thread.start();
    }

    boolean finished(int seconds) throws InterruptedException {
      return done.await(seconds, TimeUnit.SECONDS);
    }

    int exitCode() { return exitCode.get(); }
    String output() { synchronized (output) { return output.toString(); } }
  }

  private void cancel(String targetId) throws IOException {
    try (DaemonClient client = DaemonClient.connect(address)) {
      client.invoke("cancel", Map.of("targetId", targetId), null);
    }
  }

  private int queueDepth() throws IOException {
    AtomicInteger depth = new AtomicInteger(-1);
    try (DaemonClient client = DaemonClient.connect(address)) {
      client.invoke("status", Map.of(), frame -> {
        Object d = frame.get("queueDepth");
        if (d instanceof Number n) depth.set(n.intValue());
      });
    }
    return depth.get();
  }

  private void awaitQueueDepth(int expected) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline && queueDepth() != expected) Thread.sleep(20);
    assertEquals("queue never reached the expected depth", expected, queueDepth());
  }

  /**
   * A cancelled command must say it was cancelled. The typechecker swallows its own
   * interruption, so the worker sees an ordinary return and has to consult the flag it set.
   */
  @Test
  public void cancellingTheRunningTaskReportsItAsCancelled() throws Exception {
    Call running = new Call("watch");
    assertTrue("the command must reach the worker", firstStarted.await(10, TimeUnit.SECONDS));
    cancel(running.id);
    assertTrue("a cancelled command must terminate", running.finished(30));
    assertEquals("a cancelled run must not report success", 130, running.exitCode());
    assertTrue("the user must be told it was cancelled: " + running.output(),
        running.output().contains("[CANCELLED]"));
  }

  /** Cancelling a queued request must not touch the one the worker is actually running. */
  @Test
  public void cancellingAQueuedTaskLeavesTheRunningOneAlone() throws Exception {
    Call running = new Call("hold");
    assertTrue("the first command must reach the worker", firstStarted.await(10, TimeUnit.SECONDS));
    Call queued = new Call("queued");
    awaitQueueDepth(1);

    cancel(queued.id);
    // Leave the running command polling long enough to witness a flag set behind its back.
    Thread.sleep(300);

    release.countDown();
    assertTrue("the running command must still finish", running.finished(30));
    assertEquals("a cancel aimed at a queued request must not cancel the running one",
        0, running.exitCode());
    assertFalse("the running command must not be reported as cancelled: " + running.output(),
        running.output().contains("[CANCELLED]"));
    assertFalse("a cancel aimed at a queued request must not trip the running task's flag",
        heldSawCancel.get());
    synchronized (completed) {
      assertTrue("the running command must have completed: " + completed, completed.contains("hold"));
      assertFalse("the cancelled command must never have run: " + completed, completed.contains("queued"));
    }
  }

  /**
   * A cancelled queued request is dropped from the queue, and its client is still waiting for a
   * terminal frame. Without one it blocks until its shutdown hook times out and then reports
   * that the daemon never acknowledged the cancellation -- the opposite of what happened.
   */
  @Test
  public void cancellingAQueuedTaskAnswersItsOwnClient() throws Exception {
    Call running = new Call("hold");
    assertTrue("the first command must reach the worker", firstStarted.await(10, TimeUnit.SECONDS));
    Call queued = new Call("queued");
    awaitQueueDepth(1);

    cancel(queued.id);

    assertTrue("a cancelled queued request must be answered, not left hanging",
        queued.finished(10));
    assertEquals("a cancelled request reports the cancellation exit code", 130, queued.exitCode());
  }
}
