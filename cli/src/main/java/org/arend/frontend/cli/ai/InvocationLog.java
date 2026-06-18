package org.arend.frontend.cli.ai;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Per-invocation verbose log file.
 *
 * <p>Path layout: {@code <library>/.arend/log/<yyyyMMdd-HHmmss-SSS>-<reqid>.log}.
 * If {@code .arend/log/} can't be created, the log silently degrades to a system-temp
 * file; the path is still reported in the closing summary so the caller can find it.
 *
 * <p>The file is opened lazily on the first write — a router that never receives any
 * log/info/stage calls produces no log file at all. This matters during Phase 1 of the
 * rollout, when the router exists but no callsites have been migrated to it yet.
 *
 * <p>Retention: at construction time, prune the parent directory to {@link #KEEP} most
 * recent entries (best-effort; IO errors are swallowed).
 */
public final class InvocationLog implements AutoCloseable {
  public static final int KEEP = 20;

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  private final Path path;
  private PrintWriter writer;
  private boolean closed;

  private InvocationLog(Path path) {
    this.path = path;
  }

  /**
   * Create a log handle for the given library root, generating a fresh filename.
   * The file is not created until the first {@link #writeLine} call.
   */
  public static InvocationLog forLibraryRoot(Path libraryRoot, String requestId) {
    Path logDir = libraryRoot.resolve(".arend").resolve("log");
    Path target;
    try {
      Files.createDirectories(logDir);
      target = logDir.resolve(filename(requestId));
      pruneOld(logDir);
    } catch (IOException e) {
      // Fall back to system temp; the caller will still get a usable path string.
      target = fallbackTempPath(requestId);
    }
    return new InvocationLog(target);
  }

  /** Create a log at an explicit path. */
  public static InvocationLog atExplicitPath(Path file) {
    return new InvocationLog(file);
  }

  public Path path() { return path; }

  /** Append one line. Opens the file on first call. Best-effort: IO errors are swallowed. */
  public synchronized void writeLine(String line) {
    if (closed) return;
    try {
      if (writer == null) {
        Files.createDirectories(path.getParent());
        Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        writer = new PrintWriter(w, true);
      }
      writer.println(line);
    } catch (IOException ignored) {
      // best-effort
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (writer != null) {
      writer.flush();
      writer.close();
      writer = null;
    }
  }

  /** True once at least one line has been written and the file exists on disk. */
  public synchronized boolean wasUsed() {
    return writer != null || Files.exists(path);
  }

  private static String filename(String requestId) {
    String ts = TS_FMT.format(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
    String suffix = requestId == null || requestId.isEmpty() ? "local" : sanitize(requestId);
    return ts + "-" + suffix + ".log";
  }

  private static String sanitize(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
    }
    return sb.toString();
  }

  private static Path fallbackTempPath(String requestId) {
    String name = filename(requestId);
    return Path.of(System.getProperty("java.io.tmpdir"), "arend-" + name);
  }

  private static void pruneOld(Path dir) {
    try (Stream<Path> entries = Files.list(dir)) {
      List<Path> files = new ArrayList<>();
      entries.filter(p -> p.getFileName().toString().endsWith(".log")).forEach(files::add);
      if (files.size() <= KEEP) return;
      files.sort(Comparator.comparing(p -> {
        try { return Files.getLastModifiedTime(p); } catch (IOException e) { return null; }
      }, Comparator.nullsFirst(Comparator.naturalOrder())));
      int toDelete = files.size() - KEEP;
      for (int i = 0; i < toDelete; i++) {
        try { Files.deleteIfExists(files.get(i)); } catch (IOException ignored) {}
      }
    } catch (IOException ignored) {
    }
  }
}
