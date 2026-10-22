package org.arend.daemon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Function;

/**
 * Which build of arend a process runs: the jar it was started from, as the file was when it started.
 *
 * <p>A daemon runs for days, and the JVM reads a class out of its jar the first time it needs it. A
 * Gradle rebuild rewrites the jar in place, and the daemon then fails the first time it needs a class
 * it had not loaded; a jar replaced by a new file instead leaves it running the old build, answering
 * as a version the user no longer has. Either way the jar is not what it was, which is what this
 * tells: the daemon records it in its lock, a client from another build runs its command itself,
 * and a daemon whose jar has changed under it stops (see {@code DaemonServer#retireWhen}). A daemon
 * {@code arend -d} starts runs from a copy of the jar, which nothing rebuilds: it can still read its
 * classes, to say it is stopping, and on Windows its open jar does not keep the original from being
 * replaced or deleted.
 *
 * <p>Known only for a process started from a single jar -- {@code arend} or a daemon -- and not for one run from class directories, as a test is.
 *
 * @param path     the jar's absolute path
 * @param size     its size in bytes
 * @param modified its modification time, in epoch milliseconds
 * @param key      what the file system identifies the file by, an inode on Linux; empty where there is none
 */
public record BuildIdentity(String path, long size, long modified, String key) {
  private static volatile BuildIdentity ofThisProcess;
  private static volatile boolean computed;

  /**
   * The jar a daemon was started from, when it runs from a copy of it ({@code DaemonStart}), and with
   * {@code .size}, {@code .modified} and {@code .key} after it, that jar as it was when it was copied:
   * the build the daemon runs is that one, and it is the jar's changing from it that retires the daemon.
   */
  public static final String ORIGINAL_JAR_PROPERTY = "arend.daemon.originalJar";

  /**
   * The build this process runs, read the first time it is asked -- which a daemon does as it starts
   * -- or null when it runs from something else than a single jar. A daemon running from a copy runs
   * the build its parent copied ({@link #jvmProperties}), whatever the jar is by the time it asks.
   */
  public static BuildIdentity ofThisProcess() {
    if (!computed) {
      synchronized (BuildIdentity.class) {
        if (!computed) {
          String classpath = System.getProperty("java.class.path", "");
          ofThisProcess = System.getProperty(ORIGINAL_JAR_PROPERTY) != null ? fromProperties(System::getProperty)
              : classpath.contains(File.pathSeparator) || !classpath.endsWith(".jar") ? null : of(Path.of(classpath));
          computed = true;
        }
      }
    }
    return ofThisProcess;
  }

  /** The {@code -D} options that tell a daemon running from a copy of this build's jar which build it runs. */
  public List<String> jvmProperties() {
    return List.of("-D" + ORIGINAL_JAR_PROPERTY + "=" + path, "-D" + ORIGINAL_JAR_PROPERTY + ".size=" + size,
        "-D" + ORIGINAL_JAR_PROPERTY + ".modified=" + modified, "-D" + ORIGINAL_JAR_PROPERTY + ".key=" + key);
  }

  /** The build {@link #jvmProperties} names, as {@code property} has them; null when they do not name one. */
  static BuildIdentity fromProperties(Function<String, String> property) {
    String path = property.apply(ORIGINAL_JAR_PROPERTY);
    String key = property.apply(ORIGINAL_JAR_PROPERTY + ".key");
    try {
      return path == null || key == null ? null : new BuildIdentity(path, Long.parseLong(property.apply(ORIGINAL_JAR_PROPERTY + ".size")),
          Long.parseLong(property.apply(ORIGINAL_JAR_PROPERTY + ".modified")), key);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** The jar at {@code jar} as it is now; null when it cannot be read. */
  public static BuildIdentity of(Path jar) {
    try {
      Path absolute = jar.toAbsolutePath().normalize();
      BasicFileAttributes attributes = Files.readAttributes(absolute, BasicFileAttributes.class);
      Object key = attributes.fileKey();
      return new BuildIdentity(absolute.toString(), attributes.size(), attributes.lastModifiedTime().toMillis(),
          key == null ? "" : key.toString());
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** Whether the jar is still the file it was: neither rewritten in place nor replaced by another. */
  public boolean isCurrent() {
    return equals(of(Path.of(path)));
  }
}
