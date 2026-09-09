package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.cli.daemon.wire.FrameChannel;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
 * <p>The streaming is line-buffered: complete lines (terminated by {@code \n}) are
 * sent immediately; the trailing partial line is held until either the next newline
 * or {@link #detach()} flushes it.
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
    outTl.set(new FrameOutputStream(client, requestId, "stdout"));
    errTl.set(new FrameOutputStream(client, requestId, "stderr"));
  }

  /**
   * Flushes the partial-line buffers and clears the current thread's redirect. Each stream is
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
    if (!(stream instanceof FrameOutputStream frames)) return;
    try {
      frames.flushLine();
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

  // ───────── per-task line-buffered frame emitter ─────────

  /**
   * Line-buffered {@link OutputStream} that writes a wire frame per line -- or several, for
   * a line too long to fit in one. Holds incomplete trailing data until either a {@code \n}
   * or a {@link #flushLine()} call.
   */
  private static final class FrameOutputStream extends OutputStream {
    private final FrameChannel client;
    private final String requestId;
    private final String kind;
    private final StringBuilder pending = new StringBuilder();

    FrameOutputStream(FrameChannel client, String requestId, String kind) {
      this.client = client;
      this.requestId = requestId;
      this.kind = kind;
    }

    @Override
    public synchronized void write(int b) throws IOException {
      writeBytes(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
      writeBytes(b, off, len);
    }

    private void writeBytes(byte[] b, int off, int len) throws IOException {
      String s = new String(b, off, len, StandardCharsets.UTF_8);
      pending.append(s);
      int newline;
      while ((newline = pending.indexOf("\n")) >= 0) {
        String line = pending.substring(0, newline + 1);
        pending.delete(0, newline + 1);
        sendFrame(line);
      }
    }

    /** Flush any trailing data that isn't newline-terminated. */
    synchronized void flushLine() throws IOException {
      if (pending.length() > 0) {
        sendFrame(pending.toString());
        pending.setLength(0);
      }
    }

    /**
     * Chars per frame. A frame body is JSON, where one char can reach six bytes
     * (as a JSON numeric escape) before UTF-8 even applies, so the budget is the frame limit
     * divided by eight -- which leaves room for the envelope as well.
     */
    private static final int MAX_CHUNK_CHARS = FrameChannel.MAX_FRAME_BYTES / 8;

    /**
     * Sends {@code data}, split across frames if it is too long for one. The client appends every
     * {@code data} verbatim, so a frame is a piece of the stream rather than a line of it --
     * whereas an oversize body raises an IOException into a {@link PrintStream}, which files it
     * in its trouble flag and tells nobody, so the output would simply disappear.
     */
    private void sendFrame(String data) throws IOException {
      int start = 0;
      do {
        int end = Math.min(start + MAX_CHUNK_CHARS, data.length());
        // Never split a surrogate pair: encoded separately, the halves arrive as replacements.
        if (end < data.length() && Character.isHighSurrogate(data.charAt(end - 1))) end--;
        String chunk = data.substring(start, end);
        client.write("stdout".equals(kind)
            ? FrameChannel.stdoutFrame(requestId, chunk)
            : FrameChannel.stderrFrame(requestId, chunk));
        start = end;
      } while (start < data.length());
    }

    @Override
    public synchronized void flush() throws IOException {
      flushLine();
    }
  }
}
