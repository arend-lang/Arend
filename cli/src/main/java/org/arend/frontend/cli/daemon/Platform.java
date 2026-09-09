package org.arend.frontend.cli.daemon;

import java.util.Locale;

/**
 * Which operating system this is, for the handful of daemon decisions that depend on it.
 *
 * <p>One copy, because the answers have to agree: the wrapper the parent spawns, whether a Unix
 * domain socket is attempted at all, and the name of the null device are all decisions about the
 * same platform, and a copy of the test in each is a copy to forget.
 */
public final class Platform {
  private Platform() {}

  private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

  public static boolean isWindows() {
    return OS.startsWith("windows");
  }

  public static boolean isMac() {
    return OS.contains("mac");
  }

  /** Whether Unix domain sockets are worth attempting here. */
  public static boolean supportsUnixSockets() {
    return !isWindows();
  }
}
