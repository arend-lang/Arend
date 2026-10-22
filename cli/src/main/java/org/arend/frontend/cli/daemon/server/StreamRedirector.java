package org.arend.frontend.cli.daemon.server;

import org.arend.daemon.wire.FrameChannel;
import org.arend.daemon.wire.FrameOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Per-thread redirection of {@link System#out} / {@link System#err} for the daemon
 * worker.
 *
 * <p>{@link #install()} swaps the global streams once at daemon startup for delegating
 * {@link PrintStream}s that consult a {@link ThreadLocal}. While a worker task runs,
 * {@link #attach} sets the thread-local to a {@link FrameOutputStream} that emits
 * {@code FrameChannel.stdoutFrame} / {@code FrameChannel.stderrFrame} payloads to the originating
 * client socket. {@link #detach} restores the thread to the default sink (daemon.log
 * via the original {@code System.out}).
 *
 * <p>Other daemon threads (accept, client-read) keep writing to the default sink
 * because their thread-local stays unset.
 *
 * <p>The installed streams flush on every write, so what a command prints reaches its client at
 * once, a progress line without its newline too; {@link #detach()} sends what a character split
 * across writes still holds back.
 */
public final class StreamRedirector {
  private static final ThreadLocal<OutputStream> outTl = new ThreadLocal<>();
  private static final ThreadLocal<OutputStream> errTl = new ThreadLocal<>();

  private static PrintStream defaultOut;
  private static PrintStream defaultErr;
  private static boolean installed = false;

  private StreamRedirector() {}

  /**
   * Undoes {@link #install()}. The install is process-wide with no natural end, which is fine in
   * a daemon JVM and wrong anywhere else: without this, anything that saves and restores
   * {@code System.out} around its own work -- {@code JsonOutputRedirect} does -- restores the
   * shim instead of the real stream.
   */
  public static synchronized void uninstall() {
    if (!installed) return;
    System.setOut(defaultOut);
    System.setErr(defaultErr);
    defaultOut = null;
    defaultErr = null;
    installed = false;
  }

  /** One-shot global install at daemon startup. */
  public static synchronized void install() {
    if (installed) return;
    defaultOut = System.out;
    defaultErr = System.err;
    System.setOut(new PrintStream(new DelegatingOutputStream(outTl, defaultOut), true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(new DelegatingOutputStream(errTl, defaultErr), true, StandardCharsets.UTF_8));
    installed = true;
  }

  /**
   * Binds the current thread's stdout/stderr to a frame stream emitting to {@code client}. The
   * thread-locals are removed by {@link #detach()}.
   */
  public static void attach(FrameChannel client, String requestId) {
    outTl.set(new FrameOutputStream(client, requestId, true));
    errTl.set(new FrameOutputStream(client, requestId, false));
  }

  /**
   * Flushes what the frame streams hold back -- a character split across writes -- and clears the
   * current thread's redirect. Each stream is
   * flushed separately: one try around both would skip the second whenever the first throws --
   * which is what a half-closed client does -- and stderr is the one that comes second.
   */
  public static void detach() {
    try {
      flushQuietly(outTl.get());
      flushQuietly(errTl.get());
    } finally {
      outTl.remove();
      errTl.remove();
    }
  }

  private static void flushQuietly(OutputStream stream) {
    if (stream == null) return;
    try {
      stream.flush();
    } catch (IOException ignored) {
      // The client is gone; there is nowhere left to report this.
    }
  }

  // ───────── delegating wrapper used by the global PrintStream ─────────

  private static final class DelegatingOutputStream extends OutputStream {
    private final ThreadLocal<OutputStream> tl;
    private final OutputStream fallback;

    DelegatingOutputStream(ThreadLocal<OutputStream> tl, OutputStream fallback) {
      this.tl = tl;
      this.fallback = fallback;
    }

    private OutputStream current() {
      OutputStream o = tl.get();
      return o != null ? o : fallback;
    }

    @Override public void write(int b) throws IOException { current().write(b); }
    @Override public void write(byte[] b, int off, int len) throws IOException { current().write(b, off, len); }
    @Override public void flush() throws IOException { current().flush(); }
  }
}
