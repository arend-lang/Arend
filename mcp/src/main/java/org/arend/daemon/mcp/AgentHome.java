package org.arend.daemon.mcp;

import org.arend.daemon.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * The agents' configs of one home: the one this process works on, Java's {@code user.home}.
 *
 * <p>A process given a home of its own -- a test, {@code -Duser.home} -- still has the user's
 * {@code HOME} in its environment, and {@code CODEX_HOME} or {@code CLAUDE_CONFIG_DIR} there point an
 * agent at the user's configs. So such a variable is honoured only when the environment's home is
 * this one.
 */
final class AgentHome {
  private AgentHome() {}

  /** The variable the platform's programs take the home from. */
  private static String homeVariable() {
    return Platform.isWindows() ? "USERPROFILE" : "HOME";
  }

  /** Whether {@code environment} names {@code home} as the home; not when it names none. */
  static boolean isHomeOf(Path home, Map<String, String> environment) {
    String declared = environment.get(homeVariable());
    return declared != null && !declared.isEmpty() && same(home, Path.of(declared));
  }

  /** The variable {@code name} of this process's environment, when it is about {@code home}; else, and when unset or empty, null. */
  static String variable(Path home, String name) {
    return variable(home, name, System.getenv());
  }

  static String variable(Path home, String name, Map<String, String> environment) {
    String value = environment.get(name);
    return value == null || value.isEmpty() || !isHomeOf(home, environment) ? null : value;
  }

  private static boolean same(Path a, Path b) {
    try {
      return a.toRealPath().equals(b.toRealPath());
    } catch (IOException e) {
      return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
  }
}
