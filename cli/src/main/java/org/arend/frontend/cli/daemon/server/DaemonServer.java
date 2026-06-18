package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.cli.daemon.LockedFlags;
import org.arend.frontend.cli.daemon.wire.Frame;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.ComputationInterruptedException;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 *       (ping/status/cancel/shutdown) on the same thread. {@code cli} and {@code
 *       refresh} ops are enqueued for the worker so long-running typechecks don't block
 *       the accept thread.</li>
 *   <li>{@code worker}: pulls {@link WorkItem}s from a {@link BlockingQueue}. The
 *       sentinel item triggers a clean shutdown; {@code cli} items run through
 *       {@link CliDispatcher} on the warm {@link CommandContext}.</li>
 * </ul>
 *
 * <p>Cancellation: each work item carries an {@link AtomicBoolean} that a {@code cancel}
 * op flips when its {@code targetId} matches {@link #currentTaskId}. The worker wraps
 * that flag in a {@link FlagCancellationIndicator} and installs it on {@link
 * CommandContext#cancellation}, so {@code ComputationRunner.checkCanceled()} inside the
 * typechecker observes the cancel and throws {@link ComputationInterruptedException}.
 *
 * <p>{@link #shutdownLatch()} is the synchronisation point for {@link
 * org.arend.frontend.cli.daemon.DaemonMain}: it blocks on the latch and {@code
 * System.exit(0)}s once it counts down (firing the JVM shutdown hook that removes the
 * lock + socket files).
 */
public final class DaemonServer {
  private static final String SHUTDOWN_SENTINEL = "_shutdown_";
  private static final String CLI_OP = "cli";
  private static final String REFRESH_OP = "refresh";

  private final SocketBinder.Bound bound;
  private final CommandContext ctx;
  private final String libraryConfig;
  private final Map<String, Object> lockedFlags;
  private final ExecutorService clientPool;
  private final BlockingQueue<WorkItem> workQueue = new LinkedBlockingQueue<>();
  private final AtomicReference<String> workerState = new AtomicReference<>("IDLE");
  private final long startedAt = System.currentTimeMillis();
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  /** Mirrors {@link WorkItem#requestId} of the in-flight task, or {@code ""} when idle. */
  private volatile String currentTaskId = "";
  /** Per-task flag — flipped by {@code cancel} ops; observed by the worker. */
  private volatile AtomicBoolean currentTaskCancel = new AtomicBoolean();

  private Thread acceptThread;
  private Thread workerThread;

  public DaemonServer(SocketBinder.Bound bound, CommandContext ctx, String libraryConfig) {
    this.bound = bound;
    this.ctx = ctx;
    this.libraryConfig = libraryConfig;
    this.lockedFlags = LockedFlags.extract(ctx.bootstrapArgs);
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
        if (libraryConfig != null) body.put("libraryConfig", libraryConfig);
        body.put("lockedFlags", lockedFlags);
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
      case CLI_OP -> {
        String[] args = extractArgs(req);
        workQueue.add(new WorkItem(CLI_OP, id, args, ch, null));
      }
      case REFRESH_OP -> {
        // Reuse the cli-op worker path with the daemon's frozen bootstrap argv —
        // re-running -ai against the warm context, source-timestamp checks pick up
        // edits, and any streaming output goes back to this client.
        String[] args = ctx.bootstrapArgs == null ? new String[0] : ctx.bootstrapArgs.clone();
        workQueue.add(new WorkItem(CLI_OP, id, args, ch, null));
      }
      default -> {
        Frame.write(ch, Frame.errorFrame(id, "unknown op: " + op));
        Frame.write(ch, Frame.doneFrame(id, 1));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static String[] extractArgs(Map<String, Object> req) {
    Object raw = req.get("args");
    if (!(raw instanceof List<?> list)) return new String[0];
    List<String> out = new ArrayList<>(list.size());
    for (Object o : list) out.add(String.valueOf(o));
    return out.toArray(new String[0]);
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
      workerState.set("BUSY");
      currentTaskId = item.requestId;
      AtomicBoolean cancelFlag = new AtomicBoolean();
      currentTaskCancel = cancelFlag;
      ctx.cancellation = new FlagCancellationIndicator(cancelFlag);
      int exitCode = 1;
      try {
        if (CLI_OP.equals(item.op)) {
          // Bind this thread's System.out / System.err to the originating client so the
          // handler's println calls stream as wire frames. Other daemon threads keep
          // writing to daemon.log via the default sink.
          StreamRedirector.attach(item.replyTo, item.requestId);
          try {
            exitCode = CliDispatcher.run(ctx, (String[]) item.payload, item.requestId);
          } catch (ComputationInterruptedException e) {
            // A cancel op tripped the indicator; report 130 (the Unix Ctrl-C convention).
            System.err.println("[CANCELLED]");
            exitCode = 130;
          } catch (Throwable t) {
            t.printStackTrace(); // goes to the client via the redirect
            exitCode = 1;
          } finally {
            StreamRedirector.detach();
          }
          try {
            synchronized (item.replyTo) {
              Frame.write(item.replyTo, Frame.doneFrame(item.requestId, exitCode));
            }
          } catch (IOException e) {
            // Client probably disconnected before we could finish; nothing to do.
          }
        }
      } finally {
        ctx.cancellation = UnstoppableCancellationIndicator.INSTANCE;
        workerState.set("IDLE");
        currentTaskId = "";
      }
    }
  }

  /**
   * Unit of work the worker pulls off the queue.
   *
   * <p>{@code replyTo} is the socket channel the originating request came in on; the
   * worker writes streaming output frames to it. For {@code cli} ops the {@code payload}
   * is the {@code String[]} of CLI arguments handed off to {@link CliDispatcher}.
   */
  public record WorkItem(String op, String requestId, Object payload,
                         SocketChannel replyTo, AtomicBoolean cancel) {}

  /**
   * {@link CancellationIndicator} that reads from the same {@link AtomicBoolean} the
   * cancel-op handler flips. Bridges the daemon's task-tracking model into the
   * typechecker's {@code ComputationRunner.checkCanceled()} machinery.
   */
  private static final class FlagCancellationIndicator implements CancellationIndicator {
    private final AtomicBoolean flag;

    FlagCancellationIndicator(AtomicBoolean flag) { this.flag = flag; }

    @Override public boolean isCanceled() { return flag.get(); }
    @Override public void cancel() { flag.set(true); }
  }
}
