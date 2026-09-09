package org.arend.frontend.cli.daemon;

import java.util.ArrayList;
import java.util.List;

/**
 * SIGTERM, wait, SIGKILL, wait -- the one copy, used by {@code --daemon-stop} and by a
 * {@code -d} whose child never became ready.
 *
 * <p>A single {@code destroy()} and an immediate return is not a kill: the JVM runs a shutdown
 * hook, and mid-typecheck that is not instant, so a caller would go on to delete the lock of a
 * process still on its way out -- or still running. Deleting the lock of something alive is how a
 * daemon becomes unreachable, with {@code --daemon-ping} and {@code --daemon-stop} both reporting
 * "no daemon" against a socket that is still bound.
 */
public final class ProcessKill {
  public static final int GRACEFUL_TIMEOUT_SECONDS = 30;
  public static final int FORCEFUL_TIMEOUT_SECONDS = 5;

  private ProcessKill() {}

  /**
   * Signals {@code roots} and their descendants, escalating to SIGKILL if they outlast
   * {@link #GRACEFUL_TIMEOUT_SECONDS}.
   *
   * <p>Descendants are collected before the first signal, because destroying the parent is what
   * loses them: on Linux the spawned process is {@code setsid}, which forks when its caller is
   * already a process-group leader, so the handle held can be the wrapper with the JVM underneath.
   *
   * @param onEscalate told the graceful timeout expired, so the caller can say so.
   * @return whether everything is actually gone.
   */
  public static boolean terminateTree(List<ProcessHandle> roots, Runnable onEscalate) {
    List<ProcessHandle> tree = new ArrayList<>();
    for (ProcessHandle root : roots) {
      tree.addAll(root.descendants().toList());
      tree.add(root);
    }
    tree.forEach(ProcessHandle::destroy);
    if (awaitExit(tree, GRACEFUL_TIMEOUT_SECONDS)) return true;
    onEscalate.run();
    tree.forEach(ProcessHandle::destroyForcibly);
    return awaitExit(tree, FORCEFUL_TIMEOUT_SECONDS);
  }

  /** Whether every handle in {@code tree} is gone within {@code timeoutSeconds}. */
  private static boolean awaitExit(List<ProcessHandle> tree, int timeoutSeconds) {
    long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
    while (System.currentTimeMillis() < deadline) {
      if (tree.stream().noneMatch(ProcessHandle::isAlive)) return true;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return tree.stream().noneMatch(ProcessHandle::isAlive);
  }
}
