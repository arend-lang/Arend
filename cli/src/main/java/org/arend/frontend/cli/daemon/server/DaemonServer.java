package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.cli.daemon.LockedFlags;
import org.arend.frontend.cli.daemon.wire.Frame;
import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;

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
 * <p>Cancellation: each task carries an {@link AtomicBoolean} that a {@code cancel} op flips
 * when its {@code targetId} matches the running task. The worker wraps that flag in a {@link
 * FlagCancellationIndicator} and installs it on {@link CommandContext#cancellation}, so
 * {@code ComputationRunner.checkCanceled()} inside the typechecker observes it and unwinds.
 * The worker re-reads the flag once the command returns, because that unwinding is caught and
 * discarded inside {@code base/} and never surfaces as an exception here.
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
  /** The Unix Ctrl-C convention, reported for anything the client asked us to abandon. */
  public static final int CANCELLED_EXIT_CODE = 130;

  /** An in-flight task: the request it belongs to and the flag that abandons it. */
  private record Task(String id, AtomicBoolean cancel) {}

  private static final Task IDLE = new Task("", new AtomicBoolean());

  /**
   * How the worker runs one client command. The daemon's own is {@link CliDispatcher#run}; a
   * test substitutes one it can hold open, which is the only way to observe what the server does
   * to a task that is still running while another request arrives.
   */
  @FunctionalInterface
  public interface CommandRunner {
    int run(CommandContext ctx, String[] args, String clientCwd);
  }

  private final SocketBinder.Bound bound;
  private final CommandContext ctx;
  private final String libraryConfig;
  private final CommandRunner runner;
  private final Map<String, Object> lockedFlags;
  private final ExecutorService clientPool;
  private final BlockingQueue<WorkItem> workQueue = new LinkedBlockingQueue<>();
  private final AtomicReference<String> workerState = new AtomicReference<>("IDLE");
  private final long startedAt = System.currentTimeMillis();
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  /**
   * The in-flight task and its cancel flag, as one value.
   *
   * <p>Held together because a {@code cancel} reads them together: published as two separate
   * fields, a cancel arriving between the two writes matches the <em>new</em> task's id and
   * trips the <em>previous</em> task's flag, so it is acknowledged and then silently lost.
   */
  private final AtomicReference<Task> currentTask = new AtomicReference<>(IDLE);

  private Thread acceptThread;
  private Thread workerThread;

  public DaemonServer(SocketBinder.Bound bound, CommandContext ctx, String libraryConfig) {
    this(bound, ctx, libraryConfig, CliDispatcher::run);
  }

  public DaemonServer(SocketBinder.Bound bound, CommandContext ctx, String libraryConfig,
                      CommandRunner runner) {
    this.bound = bound;
    this.ctx = ctx;
    this.libraryConfig = libraryConfig;
    this.runner = runner;
    this.lockedFlags = LockedFlags.extract(ctx.bootstrapArgs);
    ThreadFactory tf = r -> {
      Thread t = new Thread(r, "arend-daemon-client");
      t.setDaemon(false);
      return t;
    };
    this.clientPool = Executors.newCachedThreadPool(tf);
  }

  public void start() {
    // Without this the worker's println calls go to the daemon's own stdout and the client sees
    // nothing. It is idempotent, and a server that cannot answer is not a useful state to leave
    // reachable by forgetting a call.
    StreamRedirector.install();
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
      FrameChannel client = new FrameChannel(ch);
      while (true) {
        Map<String, Object> req = Frame.read(ch);
        if (req == null) return; // peer closed
        handleRequest(client, req);
      }
    } catch (IOException e) {
      // Peer disconnect or wire-level error: nothing useful to log per-client; just close.
    }
  }

  private void handleRequest(FrameChannel ch, Map<String, Object> req) throws IOException {
    String id = String.valueOf(req.getOrDefault("id", ""));
    String op = String.valueOf(req.getOrDefault("op", ""));
    switch (op) {
      case "ping" -> {
        ch.write(Frame.stateFrame(id, workerState.get()));
        ch.write(Frame.doneFrame(id, 0));
      }
      case "status" -> {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("kind", "status");
        body.put("state", workerState.get());
        body.put("queueDepth", workQueue.size());
        body.put("uptimeMs", System.currentTimeMillis() - startedAt);
        body.put("currentTaskId", currentTask.get().id());
        body.put("protocolVersion", LockFile.PROTOCOL_VERSION);
        if (libraryConfig != null) body.put("libraryConfig", libraryConfig);
        body.put("lockedFlags", lockedFlags);
        ch.write(body);
        ch.write(Frame.doneFrame(id, 0));
      }
      case "cancel" -> {
        String target = String.valueOf(req.getOrDefault("targetId", ""));
        boolean accepted = !target.isEmpty() && cancelTask(target);
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("kind", "cancel-ack");
        body.put("accepted", accepted);
        ch.write(body);
        ch.write(Frame.doneFrame(id, accepted ? 0 : 1));
      }
      case "shutdown" -> {
        ch.write(Frame.doneFrame(id, 0));
        // Worker pulls the sentinel and counts down the shutdown latch.
        workQueue.add(new WorkItem(SHUTDOWN_SENTINEL, id, null, null, null, false));
      }
      case CLI_OP -> {
        String[] args = extractArgs(req);
        workQueue.add(new WorkItem(CLI_OP, id, args, ch, extractCwd(req), false));
      }
      case REFRESH_OP -> {
        // Reuse the cli-op worker path with the daemon's frozen bootstrap argv —
        // re-running it against the warm context, source-timestamp checks pick up
        // edits, and any streaming output goes back to this client.
        String[] args = ctx.bootstrapArgs == null ? new String[0] : ctx.bootstrapArgs.clone();
        // Marked internal: these are the daemon's own bootstrap args, which is what the locked
        // flags are locked to. Running them through the client-facing policy would warn that the
        // authoritative values are being ignored -- false, and unactionable from a refresh, which
        // carries no argv of its own to change.
        workQueue.add(new WorkItem(CLI_OP, id, args, ch, extractCwd(req), true));
      }
      default -> {
        ch.write(Frame.errorFrame(id, "unknown op: " + op));
        ch.write(Frame.doneFrame(id, 1));
      }
    }
  }

  /**
   * Abandons the request {@code target}, whether it is running or still queued.
   *
   * <p>The two are separate cases and must stay separate. Only the running task's own flag may
   * be tripped: cancelling something the worker has not reached yet is no reason to stop what it
   * is doing, which would abandon a different client's command. And a queued request that is
   * dropped has a client still waiting for a terminal frame -- nothing else will ever send it
   * one -- so it is answered here, on its own channel and under its own id.
   *
   * @return whether anything was actually cancelled.
   */
  private boolean cancelTask(String target) {
    Task running = currentTask.get();
    if (target.equals(running.id())) {
      running.cancel().set(true);
      return true;
    }
    List<WorkItem> dropped = new ArrayList<>();
    workQueue.removeIf(item -> {
      if (!target.equals(item.requestId())) return false;
      dropped.add(item);
      return true;
    });
    for (WorkItem item : dropped) {
      if (item.replyTo() == null) continue;
      try {
        item.replyTo().write(Frame.stderrFrame(item.requestId(), "[CANCELLED]\n"));
        item.replyTo().write(Frame.doneFrame(item.requestId(), CANCELLED_EXIT_CODE));
      } catch (IOException ignored) {
        // That client is gone; the request is dropped either way.
      }
    }
    return !dropped.isEmpty();
  }

  /** The client's working directory, or null when the peer did not send one. */
  private static String extractCwd(Map<String, Object> req) {
    Object raw = req.get("cwd");
    if (!(raw instanceof String cwd) || cwd.isEmpty()) return null;
    return cwd;
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
      Task task = new Task(item.requestId(), new AtomicBoolean());
      currentTask.set(task);
      ctx.cancellation = new FlagCancellationIndicator(task.cancel());
      int exitCode = 1;
      try {
        // Bind this thread's System.out / System.err to the originating client so the
        // handler's println calls stream as wire frames. Other daemon threads keep
        // writing to daemon.log via the default sink.
        StreamRedirector.attach(item.replyTo(), item.requestId());
        try {
          exitCode = item.internal()
              ? Dispatch.run(ctx, (String[]) item.payload, item.clientCwd())
              : runner.run(ctx, (String[]) item.payload, item.clientCwd());
        } catch (Throwable t) {
          t.printStackTrace(); // goes to the client via the redirect
          exitCode = 1;
        } finally {
          // The flag is the only evidence a cancellation leaves. ComputationInterruptedException
          // never reaches this far: ComputationRunner.run catches it and returns, and
          // ArendCheckerImpl catches it around resolving -- so a cancelled typecheck arrives
          // here as an ordinary return with an ordinary exit code, and reporting that verbatim
          // makes an abandoned run indistinguishable from a successful one.
          if (task.cancel().get()) {
            System.err.println("[CANCELLED]");
            exitCode = CANCELLED_EXIT_CODE;
          }
          StreamRedirector.detach();
        }
        try {
          item.replyTo().write(Frame.doneFrame(item.requestId(), exitCode));
        } catch (IOException e) {
          // Client probably disconnected before we could finish; nothing to do.
        }
      } finally {
        ctx.cancellation = UnstoppableCancellationIndicator.INSTANCE;
        workerState.set("IDLE");
        currentTask.set(IDLE);
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
  public record WorkItem(String op, String requestId, Object payload, FrameChannel replyTo,
                        String clientCwd, boolean internal) {}

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
