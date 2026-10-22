package org.arend.frontend.cli.daemon;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
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

  private static volatile Boolean unixSockets;

  /**
   * Whether Unix domain sockets are worth attempting here: whether one can be opened at all. Windows
   * has them since Windows 10 1803, and enforces the socket file's ACL on connect as Unix does its
   * permissions; one without them, or a JVM that cannot open one, gets TCP.
   */
  public static boolean supportsUnixSockets() {
    Boolean supported = unixSockets;
    if (supported == null) {
      try (ServerSocketChannel ignored = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
        supported = true;
      } catch (IOException | UnsupportedOperationException e) {
        supported = false;
      }
      unixSockets = supported;
    }
    return supported;
  }
}
