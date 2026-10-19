package org.arend.daemon.server;

import org.arend.daemon.wire.FrameChannel;
import org.arend.daemon.wire.FrameOutputStream;
import org.arend.typechecking.computation.BooleanCancellationIndicator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The daemon wire protocol, for the CLI's daemon and the IntelliJ plugin's alike: the socket is
 * accepted on one thread, each client read on a pool thread, and the requests that need the
 * server -- {@code cli} and {@code refresh} -- run one at a time on a worker, through a
 * {@link Runner}. {@code ping}, {@code status} and {@code cancel} are answered at once;
 * {@code shutdown} lets what is queued run first, then calls {@code onShutdown}. Nothing here exits
 * the process.
 *
 * <p>Every request has to present the daemon's token, which only the registry's copy of its lock
 * holds -- a file of the user's alone. A socket's permissions keep other users out where there is a
 * Unix socket to set them on; the token keeps them out of a TCP port, which any local process can
 * reach, and of a socket somebody else's directory holds. A request without it is answered with an
 * error, and its connection closed.
 *
 * <p>Cancellation: each accepted request gets an indicator that a {@code cancel} trips. The runner
 * hands it to the typechecker; whether it was tripped is re-read once the run returns,
 * since a cancelled typecheck unwinds inside {@code base/} and comes back as an ordinary return.
 */
public final class DaemonServer {
  /** Where one request's output goes. */
  public interface Output {
    /** Names the request to its client, and to a {@code cancel}. */
    String requestId();

    /** The client's stdout, or with {@code stdout} unset its stderr, as a stream. */
    OutputStream stream(boolean stdout);

    void out(String text);

    void err(String text);

    /** The request has finished with {@code exitCode}; nothing is written after it. */
    void done(int exitCode);
  }

  /** The output of a request a client sent over the socket: frames back to that client. */
  public record FrameOutput(FrameChannel channel, String requestId) implements Output {
    @Override
    public FrameOutputStream stream(boolean stdout) {
      return new FrameOutputStream(channel, requestId, stdout);
    }

    @Override
    public void out(String text) {
      send(FrameChannel.stdoutFrame(requestId, text));
    }

    @Override
    public void err(String text) {
      send(FrameChannel.stderrFrame(requestId, text));
    }

    @Override
    public void done(int exitCode) {
      send(FrameChannel.doneFrame(requestId, exitCode));
    }

    private void send(Map<String, Object> frame) {
      try {
        channel.write(frame);
      } catch (IOException ignored) {
        // The client has gone; the request runs to its end regardless.
      }
    }
  }

  /**
   * A request for the worker: {@code arend} with {@code args} as issued from the client's
   * {@code cwd} (null when it did not say), or a {@code refresh} when {@code args} is null.
   */
  public record Request(@Nullable List<String> args, @Nullable String cwd, Output output, BooleanCancellationIndicator cancel) {}

  /** How the worker runs a request; returns its exit code. A tripped {@link Request#cancel} abandons it. */
  @FunctionalInterface
  public interface Runner {
    int run(Request request) throws Exception;
  }

  /** A request the worker has finished, cancelled ones included. */
  public record Finished(String command, long startedAt, long durationMs, int exitCode) {}

  /**
   * What the daemon is doing, for the IDE to show: the request running, with when it started (null
   * when idle), those queued, how many it has answered, and the latest finished, newest first.
   */
  public record Activity(String state, @Nullable String current, @Nullable Long currentSince,
                         List<String> queued, int served, List<Finished> recent) {}

  /** How many finished requests {@link #activity} remembers. */
  private static final int RECENT_LIMIT = 20;

  private record WorkItem(Request request, String command) {}

  /** The request the worker runs, with when it started. */
  private record Running(WorkItem item, long since) {}

  /** Makes the worker stop once what was queued before it has run; recognised by identity. */
  private static final WorkItem SHUTDOWN = new WorkItem(null, "shutdown");

  private final SocketBinder.Bound bound;
  private final byte[] token;
  private final Supplier<List<String>> libraries;
  private final Map<String, Object> lockedFlags;
  private final String host;
  private final Runner runner;
  private final Runnable onShutdown;
  private final Function<List<String>, String> describe;
  private final Runnable onActivity;
  private final long startedAt = System.currentTimeMillis();

  private final LinkedBlockingQueue<WorkItem> workQueue = new LinkedBlockingQueue<>();
  /** Whether the daemon should stop rather than run another request, and what to tell a client it turns away; see {@link #retireWhen}. */
  private volatile BooleanSupplier retire = () -> false;
  private volatile String retireReason = "";
  /**
   * Every request accepted and not yet answered, by id -- queued and running alike, mapped to the
   * indicator that abandons it. Registered before the item is queued, so a cancel finds it whether or
   * not the worker has reached it.
   */
  private final ConcurrentHashMap<String, BooleanCancellationIndicator> inFlight = new ConcurrentHashMap<>();
  /** Null when the worker is idle; one write, so that a reader never pairs a request with another's start. */
  private volatile @Nullable Running current;
  private volatile boolean draining;
  private final ArrayDeque<Finished> recent = new ArrayDeque<>();
  private int served;

  private final ExecutorService clientPool = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("arend-daemon-client").factory());
  private final Thread acceptThread = Thread.ofPlatform().daemon().name("arend-daemon-accept").unstarted(this::acceptLoop);
  private final Thread workerThread = Thread.ofPlatform().daemon().name("arend-daemon-worker").unstarted(this::workerLoop);

  /**
   * @param token       what every request has to present, as the registry's copy of the lock says
   * @param libraries   the canonical arend.yaml or zip of every library served, as {@code status} reports them
   * @param lockedFlags the flags fixed at the daemon's start, as {@code status} reports them
   * @param host        what runs the daemon, as its lock says
   * @param describe    what a request asks for, for a person; given null for a {@code refresh}
   * @param onActivity  called whenever {@link #activity} has changed
   */
  public DaemonServer(SocketBinder.Bound bound, String token, Supplier<List<String>> libraries, Map<String, Object> lockedFlags,
                      String host, Runner runner, Runnable onShutdown,
                      Function<List<String>, String> describe, Runnable onActivity) {
    if (token == null || token.isEmpty()) throw new IllegalArgumentException("a daemon needs a token");
    this.bound = bound;
    this.token = token.getBytes(StandardCharsets.UTF_8);
    this.libraries = libraries;
    this.lockedFlags = lockedFlags;
    this.host = host;
    this.runner = runner;
    this.onShutdown = onShutdown;
    this.describe = describe;
    this.onActivity = onActivity;
  }

  /**
   * Makes the daemon stop, rather than run the next request, once {@code retire} says so: the jar it
   * runs from has been rebuilt or replaced, and what it would answer next is a build the user no
   * longer has, if the classes it still needs can be read at all. The request is answered with
   * {@code reason} instead, and so is every one queued behind it.
   */
  public DaemonServer retireWhen(BooleanSupplier retire, String reason) {
    this.retire = retire;
    this.retireReason = reason;
    return this;
  }

  public void start() {
    // Read now what turning a request away takes, while the classes can still be: retiring is for
    // when the jar they come from has changed.
    FrameChannel.stderrFrame("", "");
    FrameChannel.doneFrame("", 0);
    acceptThread.start();
    workerThread.start();
  }

  public long startedAt() {
    return startedAt;
  }

  /** Stops accepting and abandons what is running; the caller removes the lock and the socket. */
  public void close() {
    try {
      bound.channel().close();
    } catch (IOException ignored) {
    }
    inFlight.values().forEach(BooleanCancellationIndicator::cancel);
    clientPool.shutdownNow();
    acceptThread.interrupt();
    workerThread.interrupt();
  }

  private String state(@Nullable Running running) {
    return draining ? "DRAINING" : running == null ? "IDLE" : "BUSY";
  }

  public Activity activity() {
    synchronized (recent) {
      Running running = current;
      return new Activity(state(running), running == null ? null : running.item().command(), running == null ? null : running.since(),
          workQueue.stream().filter(item -> item != SHUTDOWN).map(WorkItem::command).toList(), served, List.copyOf(recent));
    }
  }

  // ───────── accept and client threads ─────────

  private void acceptLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      SocketChannel channel;
      try {
        channel = bound.channel().accept();
      } catch (ClosedChannelException e) {
        return;
      } catch (IOException e) {
        if (Thread.currentThread().isInterrupted()) return;
        System.err.println("[DAEMON] accept failed: " + e.getMessage());
        continue;
      }
      try {
        clientPool.submit(() -> serveClient(channel));
      } catch (RuntimeException e) {
        try {
          channel.close();
        } catch (IOException ignored) {
        }
        return;
      }
    }
  }

  private void serveClient(SocketChannel channel) {
    try (channel) {
      FrameChannel client = new FrameChannel(channel);
      while (true) {
        Map<String, Object> request = FrameChannel.read(channel);
        if (request == null) return;
        if (!authorized(request)) {
          String id = String.valueOf(request.getOrDefault("id", ""));
          client.write(FrameChannel.errorFrame(id, "the request does not present this daemon's token"));
          client.write(FrameChannel.doneFrame(id, 1));
          return;
        }
        handleRequest(client, request);
      }
    } catch (IOException e) {
      // The peer went away or sent garbage; nothing to answer.
    }
  }

  /** Whether {@code request} presents the token, compared in time that does not depend on how much of it matches. */
  private boolean authorized(Map<String, Object> request) {
    return request.get("token") instanceof String presented
        && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
  }

  private void handleRequest(FrameChannel channel, Map<String, Object> request) throws IOException {
    String id = String.valueOf(request.getOrDefault("id", ""));
    String op = String.valueOf(request.getOrDefault("op", ""));
    String cwd = request.get("cwd") instanceof String s && !s.isEmpty() ? s : null;
    switch (op) {
      case "ping" -> {
        channel.write(FrameChannel.stateFrame(id, state(current)));
        channel.write(FrameChannel.doneFrame(id, 0));
      }
      case "status" -> {
        Running running = current;
        channel.write(FrameChannel.statusFrame(id, state(running), workQueue.size(), System.currentTimeMillis() - startedAt,
            running == null ? "" : running.item().request().output().requestId(), libraries.get(), lockedFlags, host));
        channel.write(FrameChannel.doneFrame(id, 0));
      }
      case "cancel" -> {
        String target = String.valueOf(request.getOrDefault("targetId", ""));
        boolean accepted = !target.isEmpty() && cancelTask(target);
        channel.write(FrameChannel.cancelAckFrame(id, target, accepted));
        channel.write(FrameChannel.doneFrame(id, accepted ? 0 : 1));
      }
      case "shutdown" -> {
        channel.write(FrameChannel.doneFrame(id, 0));
        workQueue.add(SHUTDOWN);
      }
      case "cli" -> queue(id, request.get("args") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of(), cwd, channel);
      case "refresh" -> queue(id, null, cwd, channel);
      default -> {
        channel.write(FrameChannel.errorFrame(id, "unknown op: " + op));
        channel.write(FrameChannel.doneFrame(id, 1));
      }
    }
  }

  private void queue(String id, @Nullable List<String> args, @Nullable String cwd, FrameChannel channel) {
    Request request = new Request(args, cwd, new FrameOutput(channel, id), new BooleanCancellationIndicator());
    inFlight.put(id, request.cancel());
    workQueue.add(new WorkItem(request, describe.apply(args)));
    onActivity.run();
  }

  /**
   * Abandons {@code target}, running or queued. The flag is tripped first: it belongs to that one
   * request, and the worker checks it before running an item it has taken. A request still queued
   * is answered here, since the worker never sees it.
   *
   * @return whether the request was one the daemon still holds.
   */
  private boolean cancelTask(String target) {
    BooleanCancellationIndicator cancel = inFlight.get(target);
    if (cancel == null) return false;
    cancel.cancel();
    // A request id names one request, so at most one is queued under it.
    WorkItem queued = workQueue.stream().filter(item -> item != SHUTDOWN && item.request().output().requestId().equals(target)).findFirst().orElse(null);
    if (queued != null && workQueue.remove(queued)) {
      queued.request().output().err("[CANCELLED]\n");
      complete(queued, System.currentTimeMillis(), FrameChannel.CANCELLED_EXIT_CODE);
      onActivity.run();
    }
    return true;
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
        draining = true;
        // Off this thread: stopping the daemon closes it.
        Thread.ofPlatform().daemon().name("arend-daemon-shutdown").start(onShutdown);
        return;
      }
      if (retire.getAsBoolean()) {
        turnAwayAll(item);
        return;
      }
      Request request = item.request();
      long itemStartedAt = System.currentTimeMillis();
      current = new Running(item, itemStartedAt);
      onActivity.run();
      int exitCode = 1;
      try {
        // Already cancelled: starting the run anyway would hold the worker for a whole typecheck.
        if (!request.cancel().isCanceled()) exitCode = runner.run(request);
      } catch (Throwable e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        request.output().err("[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n");
      } finally {
        if (request.cancel().isCanceled()) {
          request.output().err("[CANCELLED]\n");
          exitCode = FrameChannel.CANCELLED_EXIT_CODE;
        }
        current = null;
        complete(item, itemStartedAt, exitCode);
        onActivity.run();
      }
    }
  }

  /** Answers {@code first} and everything queued behind it with the reason to retire, then shuts down. */
  private void turnAwayAll(WorkItem first) {
    draining = true;
    List<WorkItem> items = new ArrayList<>();
    items.add(first);
    workQueue.drainTo(items);
    for (WorkItem item : items) {
      if (item == SHUTDOWN) continue;
      item.request().output().err("[ERROR] " + retireReason + "\n");
      complete(item, System.currentTimeMillis(), 1);
    }
    onActivity.run();
    Thread.ofPlatform().daemon().name("arend-daemon-shutdown").start(onShutdown);
  }

  /** Records {@code item} as finished and only then tells its client, so that a client hearing {@code done} finds the daemon past it. */
  private void complete(WorkItem item, long itemStartedAt, int exitCode) {
    synchronized (recent) {
      served++;
      recent.addFirst(new Finished(item.command(), itemStartedAt, System.currentTimeMillis() - itemStartedAt, exitCode));
      while (recent.size() > RECENT_LIMIT) recent.removeLast();
    }
    inFlight.remove(item.request().output().requestId());
    item.request().output().done(exitCode);
  }
}
