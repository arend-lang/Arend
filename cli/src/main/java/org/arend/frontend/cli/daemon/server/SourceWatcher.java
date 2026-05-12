package org.arend.frontend.cli.daemon.server;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background NIO {@link WatchService} that fires {@code trigger.run()} a short time
 * after the last {@code .ard} edit under any of the {@code roots}. Used by the daemon
 * to auto-refresh its warm context when sources change.
 *
 * <p>Debounce is implemented with {@link WatchService#poll(long, TimeUnit) poll(timeout)}:
 * each event refreshes a "last event" timestamp; whenever {@code poll} times out and the
 * idle window has elapsed, we fire {@code trigger} once and reset. Coalescing happens
 * inside the trigger itself (the daemon skips enqueueing if a refresh is already in
 * flight), so a single saved file followed by ten more edits within the debounce window
 * produces one refresh, not eleven.
 *
 * <p>Only {@code .ard} entries count; editor temp/backup files ({@code .swp},
 * {@code ~}, IDE marker files) are ignored so a save doesn't fan out into noise.
 * Directories created mid-watch are picked up by re-walking and registering them.
 */
public final class SourceWatcher {
  private final List<Path> roots;
  private final Runnable trigger;
  private final long debounceMs;

  private WatchService watcher;
  private Thread thread;
  private volatile boolean running;
  private final AtomicLong lastEventNanos = new AtomicLong(-1);

  public SourceWatcher(List<Path> roots, Runnable trigger, long debounceMs) {
    this.roots = roots;
    this.trigger = trigger;
    this.debounceMs = debounceMs;
  }

  public void start() throws IOException {
    watcher = FileSystems.getDefault().newWatchService();
    for (Path root : roots) {
      if (root != null) registerRecursive(root);
    }
    running = true;
    thread = new Thread(this::loop, "arend-daemon-watch");
    thread.setDaemon(true);
    thread.start();
  }

  public void stop() {
    running = false;
    if (watcher != null) {
      try { watcher.close(); } catch (IOException ignored) {}
    }
    if (thread != null) thread.interrupt();
  }

  private void registerRecursive(Path root) throws IOException {
    if (!Files.isDirectory(root)) return;
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        dir.register(watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private void loop() {
    while (running) {
      WatchKey key;
      try {
        key = watcher.poll(debounceMs, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        return;
      } catch (ClosedWatchServiceException e) {
        return;
      }

      if (key != null) {
        Path keyDir = (Path) key.watchable();
        boolean sawArd = false;
        for (WatchEvent<?> evt : key.pollEvents()) {
          if (evt.kind() == StandardWatchEventKinds.OVERFLOW) {
            // OVERFLOW means we missed events; treat as a content change to be safe.
            sawArd = true;
            continue;
          }
          Object ctx = evt.context();
          if (!(ctx instanceof Path name)) continue;
          Path full = keyDir.resolve(name);
          String filename = name.toString();
          if (filename.endsWith(".ard")) {
            sawArd = true;
          }
          if (evt.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(full)) {
            try { registerRecursive(full); } catch (IOException ignored) {}
          }
        }
        if (!key.reset()) {
          // The directory we were watching disappeared; nothing useful to do besides
          // continuing the loop. registerRecursive on any new dir already covers re-adds.
        }
        if (sawArd) lastEventNanos.set(System.nanoTime());
      } else {
        // poll timed out — if the idle window has elapsed since the last event, fire.
        long last = lastEventNanos.get();
        if (last != -1 && System.nanoTime() - last >= debounceMs * 1_000_000L
            && lastEventNanos.compareAndSet(last, -1)) {
          try {
            trigger.run();
          } catch (Throwable t) {
            t.printStackTrace();
          }
        }
      }
    }
  }
}
