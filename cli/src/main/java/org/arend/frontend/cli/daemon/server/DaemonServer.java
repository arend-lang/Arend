package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.cli.daemon.LockedFlags;
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
import java.util.concurrent.ConcurrentHashMap;
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
 *   <li>{@code accept}: blocks on {@link SocketBinder.Bound#channel()}'s accept(), handing each
 *       new connection to a small cached pool.</li>
 *   <li>per-client (from the pool): reads framed requests and answers the inline ops
 *       (ping/status/cancel/shutdown) on the same thread. {@code cli} and {@code refresh} are
 *       queued for the worker, so a long typecheck does not block accept.</li>
 *   <li>{@code worker}: runs one queued command at a time through {@link CliDispatcher} on the
 *       warm {@link CommandContext}. The shutdown sentinel makes it count down
 *       {@link #shutdownLatch()}, which is what {@code DaemonMain} blocks on.</li>
 * </ul>
 *
 * <p>Cancellation: each accepted request gets an {@link AtomicBoolean} that a {@code cancel} op
 * flips. The worker wraps it in a {@link CancellationIndicator} on
 * {@link CommandContext#cancellation}, so {@code ComputationRunner.checkCanceled()} inside the
 * typechecker observes it, and re-reads the flag once the command returns -- that unwinding is
 * caught and discarded inside {@code base/} and never surfaces as an exception here.
 */
public final class DaemonServer {
  private static final String CLI_OP = "cli";
  private static final String REFRESH_OP = "refresh";
  /** The Unix Ctrl-C convention, reported for anything the client asked us to abandon. */
  public static final int CANCELLED_EXIT_CODE = 130;

  /** How the worker runs one client command. Visible so a test can substitute one it can hold open. */
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
  private final AtomicReference<String> currentTaskId = new AtomicReference<>("");
  private final long startedAt = System.currentTimeMillis();
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  /**
   * Every request accepted and not yet answered, by id -- queued and running alike, mapped to the
   * flag that abandons it. Registered before the item is queued, so a cancel finds it whether or
   * not the worker has reached it: the queue and the running task are not one atomic view, and a
   * request that {@code take()} has removed but the worker has not yet published is in neither.
   */
  private final ConcurrentHashMap<String, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

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
    // nothing. Idempotent, and installing it here is what keeps "started but mute" unreachable.
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
        Map<String, Object> req = FrameChannel.read(ch);
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
        ch.write(FrameChannel.stateFrame(id, workerState.get()));
        ch.write(FrameChannel.doneFrame(id, 0));
      }
      case "status" -> {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("kind", "status");
        body.put("state", workerState.get());
        body.put("queueDepth", workQueue.size());
        body.put("uptimeMs", System.currentTimeMillis() - startedAt);
        body.put("currentTaskId", currentTaskId.get());
        body.put("protocolVersion", LockFile.PROTOCOL_VERSION);
        if (libraryConfig != null) body.put("libraryConfig", libraryConfig);
        body.put("lockedFlags", lockedFlags);
        ch.write(body);
        ch.write(FrameChannel.doneFrame(id, 0));
      }
      case "cancel" -> {
        String target = String.valueOf(req.getOrDefault("targetId", ""));
        boolean accepted = !target.isEmpty() && cancelTask(target);
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("kind", "cancel-ack");
        // Echo the target: a cancel is its own request with its own id, and the client's read
        // loop is waiting on the *command's* id, so this is what lets the waiter recognise its
        // own answer instead of dropping it.
        body.put("targetId", target);
        body.put("accepted", accepted);
        ch.write(body);
        ch.write(FrameChannel.doneFrame(id, accepted ? 0 : 1));
      }
      case "shutdown" -> {
        ch.write(FrameChannel.doneFrame(id, 0));
        workQueue.add(SHUTDOWN);
      }
      case CLI_OP -> queue(new WorkItem(id, extractArgs(req), ch, extractCwd(req), false));
      case REFRESH_OP -> {
        // The daemon's own bootstrap argv, re-dispatched against the warm context so
        // source-timestamp checks pick up edits. Marked internal: these args are what the locked
        // flags are locked *to*, so putting them through the client-facing policy would warn that
        // the authoritative values are being ignored -- false, and unactionable from a refresh.
        String[] args = ctx.bootstrapArgs == null ? new String[0] : ctx.bootstrapArgs.clone();
        queue(new WorkItem(id, args, ch, extractCwd(req), true));
      }
      default -> {
        ch.write(FrameChannel.errorFrame(id, "unknown op: " + op));
        ch.write(FrameChannel.doneFrame(id, 1));
      }
    }
  }

  /** Registers {@code item}'s cancel flag before the item is visible to the worker. */
  private void queue(WorkItem item) {
    inFlight.put(item.requestId(), item.cancel());
    workQueue.add(item);
  }

  /**
   * Abandons the request {@code target}, whether it is running or still queued.
   *
   * <p>The flag is tripped first and without asking which of the two it is: it belongs to that one
   * request, so setting it can never abandon another client's command, and the worker checks it
   * before dispatching, so an item already taken off the queue stops just as one still on it does.
   * Dequeuing stays a separate step because it decides who answers the client -- a request pulled
   * out of the queue never reaches the worker, and nothing else would send it a terminal frame.
   *
   * @return whether the request was one the daemon still holds.
   */
  private boolean cancelTask(String target) {
    AtomicBoolean cancel = inFlight.get(target);
    if (cancel == null) return false;
    cancel.set(true);

    List<WorkItem> dropped = new ArrayList<>();
    workQueue.removeIf(item -> {
      if (!target.equals(item.requestId())) return false;
      dropped.add(item);
      return true;
    });
    for (WorkItem item : dropped) {
      inFlight.remove(item.requestId());
      if (item.replyTo() == null) continue;
      try {
        item.replyTo().write(FrameChannel.stderrFrame(item.requestId(), "[CANCELLED]\n"));
        item.replyTo().write(FrameChannel.doneFrame(item.requestId(), CANCELLED_EXIT_CODE));
      } catch (IOException ignored) {
        // That client is gone; the request is dropped either way.
      }
    }
    return true;
  }

  /** The client's working directory, or null when the peer did not send one. */
  private static String extractCwd(Map<String, Object> req) {
    Object raw = req.get("cwd");
    if (!(raw instanceof String cwd) || cwd.isEmpty()) return null;
    return cwd;
  }

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
      if (item == SHUTDOWN) {
        workerState.set("DRAINING");
        shutdownLatch.countDown();
        return;
      }
      workerState.set("BUSY");
      currentTaskId.set(item.requestId());
      ctx.cancellation = new FlagCancellationIndicator(item.cancel());
      int exitCode = 1;
      try {
        // Bind this thread's System.out / System.err to the originating client so the handler's
        // println calls stream as wire frames. Other daemon threads keep writing to daemon.log.
        StreamRedirector.attach(item.replyTo(), item.requestId());
        try {
          // Already cancelled: a cancel that arrived while this sat in the queue, or in the
          // instant after it was taken off it. Starting the run anyway would hold the worker for
          // a whole typecheck and then throw the answer away.
          if (!item.cancel().get()) {
            exitCode = item.internal()
                ? Dispatch.run(ctx, item.args(), item.clientCwd())
                : runner.run(ctx, item.args(), item.clientCwd());
          }
        } catch (Throwable t) {
          t.printStackTrace(); // goes to the client via the redirect
          exitCode = 1;
        } finally {
          // The flag is the only evidence a cancellation leaves: ComputationInterruptedException
          // is caught inside ComputationRunner.run and ArendCheckerImpl, so a cancelled typecheck
          // arrives here as an ordinary return with an ordinary exit code.
          if (item.cancel().get()) {
            // Close the progress line first: reportModuleProgress leaves "\r[37/412] Typechecking
            // Algebra.Ring" open with no newline, and the marker would reach the client welded to
            // the counter.
            ctx.finishProgressLine();
            System.err.println("[CANCELLED]");
            exitCode = CANCELLED_EXIT_CODE;
          }
          StreamRedirector.detach();
        }
        try {
          item.replyTo().write(FrameChannel.doneFrame(item.requestId(), exitCode));
        } catch (IOException e) {
          // Client probably disconnected before we could finish; nothing to do.
        }
      } finally {
        ctx.cancellation = UnstoppableCancellationIndicator.INSTANCE;
        workerState.set("IDLE");
        currentTaskId.set("");
        inFlight.remove(item.requestId());
      }
    }
  }

  /**
   * One queued command: the request it answers, the argv, the channel its output streams back on,
   * and the flag that abandons it. {@code internal} marks the daemon's own bootstrap argv, which
   * bypasses the client-facing policy in {@link CliDispatcher}.
   */
  private record WorkItem(String requestId, String[] args, FrameChannel replyTo,
                          String clientCwd, boolean internal, AtomicBoolean cancel) {
    WorkItem(String requestId, String[] args, FrameChannel replyTo, String clientCwd,
             boolean internal) {
      this(requestId, args, replyTo, clientCwd, internal, new AtomicBoolean());
    }
  }

  /** Makes the worker count down {@link #shutdownLatch}; recognised by identity. */
  private static final WorkItem SHUTDOWN = new WorkItem("", null, null, null, false);

  /** Bridges the daemon's cancel flag into {@code ComputationRunner.checkCanceled()}. */
  private static final class FlagCancellationIndicator implements CancellationIndicator {
    private final AtomicBoolean flag;

    FlagCancellationIndicator(AtomicBoolean flag) { this.flag = flag; }

    @Override public boolean isCanceled() { return flag.get(); }
    @Override public void cancel() { flag.set(true); }
  }
}
