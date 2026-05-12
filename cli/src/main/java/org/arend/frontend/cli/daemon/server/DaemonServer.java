package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.cli.daemon.wire.Frame;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;

/**
 * Daemon-side socket server. Owns three logical threads:
 *
 * <ul>
 *   <li>{@code accept}: blocks on {@link SocketBinder.Bound#channel()}'s accept().
 *       Hands each new connection off to a small cached pool.</li>
 *   <li>per-client (from the pool): reads framed requests, dispatches inline ops
 *       (ping/status/cancel/shutdown) on the same thread. Real commands (M4) will be
 *       enqueued for the worker.</li>
 *   <li>{@code worker}: pulls {@link WorkItem}s from a {@link BlockingQueue}. M3 only
 *       enqueues the shutdown sentinel; M4 will plug real handlers into this loop.</li>
 * </ul>
 *
 * <p>Cancellation: each work item carries an {@link AtomicBoolean} that the worker
 * passes to a {@code CancellationIndicator}. A {@code cancel} op flips the flag for the
 * task whose id matches {@link #currentTaskId}.
 *
 * <p>{@link #shutdownLatch()} is the synchronisation point for {@link
 * org.arend.frontend.cli.daemon.DaemonMain}: it blocks on the latch and {@code
 * System.exit(0)}s once it counts down (firing the JVM shutdown hook that removes the
 * lock + socket files).
 */
public final class DaemonServer {
  private static final String SHUTDOWN_SENTINEL = "_shutdown_";

  private final SocketBinder.Bound bound;
  private final ExecutorService clientPool;
  private final BlockingQueue<WorkItem> workQueue = new LinkedBlockingQueue<>();
  private final AtomicReference<String> workerState = new AtomicReference<>("IDLE");
  private final long startedAt = System.currentTimeMillis();
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  /** Mirrors {@link WorkItem#requestId} of the in-flight task, or {@code ""} when idle. */
  private volatile String currentTaskId = "";
  /** Per-task flag — flipped by {@code cancel} ops; observed by the worker (M4). */
  private volatile AtomicBoolean currentTaskCancel = new AtomicBoolean();

  private Thread acceptThread;
  private Thread workerThread;

  public DaemonServer(SocketBinder.Bound bound) {
    this.bound = bound;
    ThreadFactory tf = r -> {
      Thread t = new Thread(r, "arend-daemon-client");
      t.setDaemon(false);
      return t;
    };
    this.clientPool = Executors.newCachedThreadPool(tf);
  }

  public void start() {
    acceptThread = new Thread(this::acceptLoop, "arend-daemon-accept");
    acceptThread.setDaemon(false);
    workerThread = new Thread(this::workerLoop, "arend-daemon-worker");
    workerThread.setDaemon(false);
    acceptThread.start();
    workerThread.start();
  }

  public CountDownLatch shutdownLatch() {
    return shutdownLatch;
  }

  /** Race-safe socket close; used by the shutdown hook to unblock {@code accept()}. */
  public void close() {
    try {
      bound.channel().close();
    } catch (IOException ignored) {
    }
    clientPool.shutdownNow();
    if (acceptThread != null) acceptThread.interrupt();
    if (workerThread != null) workerThread.interrupt();
    try {
      clientPool.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ───────── accept thread ─────────

  private void acceptLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      SocketChannel ch;
      try {
        ch = bound.channel().accept();
      } catch (ClosedChannelException e) {
        return; // socket closed (shutdown path)
      } catch (IOException e) {
        if (Thread.currentThread().isInterrupted()) return;
        System.err.println("[DAEMON] accept failed: " + e.getMessage());
        continue;
      }
      clientPool.submit(() -> serveClient(ch));
    }
  }

  // ───────── per-client thread (from the pool) ─────────

  private void serveClient(SocketChannel ch) {
    try (ch) {
      while (true) {
        Map<String, Object> req = Frame.read(ch);
        if (req == null) return; // peer closed
        handleRequest(ch, req);
      }
    } catch (IOException e) {
      // Peer disconnect or wire-level error: nothing useful to log per-client; just close.
    }
  }

  private void handleRequest(SocketChannel ch, Map<String, Object> req) throws IOException {
    String id = String.valueOf(req.getOrDefault("id", ""));
    String op = String.valueOf(req.getOrDefault("op", ""));
    switch (op) {
      case "ping" -> {
        Frame.write(ch, Frame.stateFrame(id, workerState.get()));
        Frame.write(ch, Frame.doneFrame(id, 0));
      }
      case "status" -> {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("kind", "status");
        body.put("state", workerState.get());
        body.put("queueDepth", workQueue.size());
        body.put("uptimeMs", System.currentTimeMillis() - startedAt);
        body.put("currentTaskId", currentTaskId);
        body.put("protocolVersion", LockFile.PROTOCOL_VERSION_M3);
        Frame.write(ch, body);
        Frame.write(ch, Frame.doneFrame(id, 0));
      }
      case "cancel" -> {
        String target = String.valueOf(req.getOrDefault("targetId", ""));
        boolean accepted = !target.isEmpty() && target.equals(currentTaskId);
        if (accepted) currentTaskCancel.set(true);
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("kind", "cancel-ack");
        body.put("accepted", accepted);
        Frame.write(ch, body);
        Frame.write(ch, Frame.doneFrame(id, accepted ? 0 : 1));
      }
      case "shutdown" -> {
        Frame.write(ch, Frame.doneFrame(id, 0));
        // Worker pulls the sentinel and counts down the shutdown latch.
        workQueue.add(new WorkItem(SHUTDOWN_SENTINEL, id, null, null, null));
      }
      default -> {
        Frame.write(ch, Frame.errorFrame(id, "unknown op: " + op));
        Frame.write(ch, Frame.doneFrame(id, 1));
      }
    }
  }

  // ───────── worker thread ─────────

  private void workerLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      WorkItem item;
      try {
        item = workQueue.take();
      } catch (InterruptedException e) {
        return;
      }
      if (SHUTDOWN_SENTINEL.equals(item.op)) {
        workerState.set("DRAINING");
        shutdownLatch.countDown();
        return;
      }
      // M4 will dispatch real ops here using item.payload + item.replyTo.
      workerState.set("BUSY");
      currentTaskId = item.requestId;
      currentTaskCancel = new AtomicBoolean();
      try {
        // No-op in M3. M4: route to the per-op handler bound to ctx + reply sink.
      } finally {
        workerState.set("IDLE");
        currentTaskId = "";
      }
    }
  }

  /**
   * Unit of work the worker pulls off the queue.
   *
   * <p>{@code replyTo} is the socket channel the originating request came in on; the
   * worker writes streaming output frames to it. M4 will populate {@code payload} with
   * a per-op argument object (e.g. {@code ProofSearchArgs}).
   */
  public record WorkItem(String op, String requestId, Object payload,
                         SocketChannel replyTo, AtomicBoolean cancel) {}
}
