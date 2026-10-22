package org.arend.query;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

/**
 * Where the query tools print their diagnostics and listings: {@code System.out} and
 * {@code System.err}, read afresh on every call so that a redirect of either is followed.
 */
public final class QueryConsole {
  private QueryConsole() {}

  public static @NotNull PrintStream out() {
    return System.out;
  }

  public static @NotNull PrintStream err() {
    return System.err;
  }
}
