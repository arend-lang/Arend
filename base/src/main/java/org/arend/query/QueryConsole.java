package org.arend.query;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.function.Supplier;

/**
 * Where the query tools print their diagnostics and listings: {@code System.out} and
 * {@code System.err}, read afresh on every call so that a redirect of either is followed, unless
 * the caller gives this thread streams of its own. The IntelliJ plugin's daemon does, since it
 * cannot redirect the streams of the IDE it runs in.
 */
public final class QueryConsole {
  private QueryConsole() {}

  private record Streams(PrintStream out, PrintStream err) {}

  private static final ThreadLocal<Streams> STREAMS = new ThreadLocal<>();

  public static @NotNull PrintStream out() {
    Streams streams = STREAMS.get();
    return streams != null ? streams.out() : System.out;
  }

  public static @NotNull PrintStream err() {
    Streams streams = STREAMS.get();
    return streams != null ? streams.err() : System.err;
  }

  /** Runs {@code body} with this thread's tools printing to {@code out} and {@code err}. */
  public static <T> T with(@NotNull PrintStream out, @NotNull PrintStream err, @NotNull Supplier<T> body) {
    Streams previous = STREAMS.get();
    STREAMS.set(new Streams(out, err));
    try {
      return body.get();
    } finally {
      if (previous != null) STREAMS.set(previous); else STREAMS.remove();
    }
  }
}
