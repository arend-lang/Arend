package org.arend.library;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.BinaryLoader;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;

/**
 * Repro test for the "secondary contradiction" failure mode observed when an upstream
 * arend-lib module's source is touched and the CLI tries to satisfy a downstream target
 * via the partial binary cache (commit 78de87dae and friends).
 *
 * <p>Scenario:
 * <ol>
 *   <li>Take a fully built ARC cache (the test requires {@code arend-lib/bin} to already
 *       contain a complete set of .arc files).</li>
 *   <li>Mirror that cache to a temp directory and remove the .arc of one upstream module
 *       (default {@code Algebra.Domain}). This simulates the CLI behavior on
 *       {@code touch arend-lib/src/Algebra/Domain.ard}, which makes the timestamp filter
 *       in {@link BinaryLoader#loadBinaryCache} skip that module.</li>
 *   <li>Build a fresh {@link ArendServer} that loads .ard for every module (so the
 *       concrete tree is present everywhere) and applies the standard
 *       {@code loadBinaryCache} cascade against the trimmed cache. The cascade marks
 *       cross-module references through the deleted module as "incomplete"/"failed",
 *       forcing re-typecheck of a non-trivial subset from source while the rest stay
 *       pure-deserialized.</li>
 *   <li>Type-check the configured target definition (default {@code Arith.Exp};
 *       configurable via {@code -Darend.partial_cache.target=...}).</li>
 *   <li>Fail iff the run produced a {@code Meta 'contradiction' failed} or
 *       {@code Cannot infer contradiction} error.</li>
 * </ol>
 *
 * <p>Differences from {@link ArendLibPartialRoundTripTest}:
 * <ul>
 *   <li>Loads the cone modules <em>purely from ARC</em>, with NO source-tree overlay on
 *       loaded modules. That's the difference that exposes the visitor's deserialized
 *       super-walk / Java-meta gaps — they only fire when {@code myConcreteProvider} has
 *       no concrete for an upstream class.</li>
 *   <li>Reuses the production {@link BinaryLoader#loadBinaryCache} (and its
 *       orphan-shell cascade) instead of hand-rolling the ARC overlay. So the test
 *       exercises the exact buckets you see from the CLI.</li>
 * </ul>
 *
 * <p>Gated off by default. Run with
 * {@code -Darend.partial_cache.enabled=true -Darend.partial_cache.target=Arith.Exp}.
 * Override the invalidation point with {@code -Darend.partial_cache.touched=...}.
 */
public class ArendLibPartialCacheTest {
  private static final Path AREND_LIB_DIR = Paths.get("arend-lib");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private static final String ENABLED_PROPERTY = "arend.partial_cache.enabled";
  private static final String TOUCHED_PROPERTY = "arend.partial_cache.touched";
  private static final String TARGET_PROPERTY = "arend.partial_cache.target";
  private static final String DEFAULT_TOUCHED = "Algebra.Domain";
  private static final String DEFAULT_TARGET = "Arith.Exp";

  private PrintWriter logWriter;
  private Path logFile;
  private long testStartMs;

  private void openLog() throws IOException {
    logFile = Files.createTempFile("arend_partial_cache_", ".log");
    logWriter = new PrintWriter(Files.newBufferedWriter(logFile));
    System.out.println("[ArendLibPartialCacheTest] Log file: " + logFile.toAbsolutePath());
  }

  private void closeLog() {
    if (logWriter != null) {
      logWriter.flush();
      logWriter.close();
    }
  }

  private void log(String msg) {
    String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + msg;
    System.out.println(line);
    if (logWriter != null) {
      logWriter.println(line);
      logWriter.flush();
    }
  }

  private String elapsed() {
    return String.format("%.1fs", (System.currentTimeMillis() - testStartMs) / 1000.0);
  }

  private FileSourceLibrary buildArendLibrary(Path binBasePath) {
    Path metaBuildPath = AREND_LIB_DIR.resolve("meta/build/classes/java/main");
    FileClassLoaderDelegate delegate =
        Files.isDirectory(metaBuildPath) ? new FileClassLoaderDelegate(metaBuildPath) : null;
    String extensionMainClass = delegate != null ? "org.arend.lib.StdExtension" : null;

    return new FileSourceLibrary(
        "arend-lib",
        /*isExternalLibrary=*/ false,
        /*modificationStamp=*/ -1L,
        /*dependencies=*/ Collections.emptyList(),
        /*version=*/ null,
        /*langVersion=*/ null,
        extensionMainClass,
        /*modules=*/ null,
        AREND_LIB_DIR.resolve("src"),
        binBasePath,
        AREND_LIB_DIR.resolve("test"),
        delegate);
  }

  /** Recursively copy {@code src} into {@code dst}, preserving relative structure. */
  private static void copyTree(Path src, Path dst) throws IOException {
    Files.walkFileTree(src, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Files.createDirectories(dst.resolve(src.relativize(dir)));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.copy(file, dst.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  @Test
  public void partialCacheContradictionRepro() throws Exception {
    Assume.assumeTrue(
        "Set -D" + ENABLED_PROPERTY + "=true to run this test",
        "true".equals(System.getProperty(ENABLED_PROPERTY)));
    Assume.assumeTrue(
        "arend-lib/src not present – skipping",
        Files.isDirectory(AREND_LIB_DIR.resolve("src")));
    Assume.assumeTrue(
        "arend-lib/bin not present – skipping",
        Files.isDirectory(AREND_LIB_DIR.resolve("bin")));

    testStartMs = System.currentTimeMillis();
    openLog();

    String touchedName = System.getProperty(TOUCHED_PROPERTY, DEFAULT_TOUCHED);
    String targetName = System.getProperty(TARGET_PROPERTY, DEFAULT_TARGET);
    log("=== ArendLib partial-cache repro START ===");
    log("Touched module (its .arc will be deleted): " + touchedName);
    log("Target module to typecheck: " + targetName);

    Path tempBin = null;
    try {
      // ---- Phase 1: clone arend-lib/bin into temp, drop the touched .arc -------
      tempBin = Files.createTempDirectory("arend_partial_cache_bin_");
      log("Cloning arend-lib/bin into " + tempBin);
      copyTree(AREND_LIB_DIR.resolve("bin"), tempBin);

      Path touchedArc = tempBin.resolve(touchedName.replace('.', '/') + ".arc");
      Assume.assumeTrue(
          "Touched module's .arc not present in arend-lib/bin – seed the cache first",
          Files.isRegularFile(touchedArc));
      if (Boolean.getBoolean("arend.partial_cache.skipDelete")) {
        log("DEBUG: -Darend.partial_cache.skipDelete=true — leaving " + touchedArc + " intact");
      } else {
        Files.delete(touchedArc);
        log("Deleted " + touchedArc + " to simulate touch on " + touchedName + ".ard");
      }

      // ---- Phase 2: build server + library against the trimmed bin dir --------
      ListErrorReporter reporter = new ListErrorReporter();
      LibraryManager libManager = new LibraryManager(reporter);
      CliServerRequester requester = new CliServerRequester(libManager);
      BinaryLoader binaryLoader = new BinaryLoader(libManager);
      ArendServerImpl server = new ArendServerImpl(requester, false, false, false);
      server.addReadOnlyModule(Prelude.MODULE_LOCATION,
          () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
      FileSourceLibrary arendLib = buildArendLibrary(tempBin);
      libManager.updateLibrary(arendLib, server);

      // Typecheck Prelude first — loadBinaryCache needs it for cross-module resolution.
      server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

      // ---- Phase 3: raw-load every arend-lib .ard + apply binary cache cascade -
      log("Phase 3: raw-loading all arend-lib sources + applying loadBinaryCache");
      long phase3Start = System.currentTimeMillis();

      List<ModulePath> allModulePaths = arendLib.findModules(false);
      List<ModuleLocation> allLocations = new ArrayList<>(allModulePaths.size());
      for (ModulePath path : allModulePaths) {
        allLocations.add(new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE, path));
      }
      log("  found " + allLocations.size() + " source modules");

      server.getCheckerFor(allLocations)
          .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

      // The production cascade — populates "loaded", "incomplete", "failed" buckets,
      // prints the same `[INFO] Binary cache: ...` line the CLI prints.
      binaryLoader.loadBinaryCache(arendLib, server);
      log("Phase 3 complete in " + String.format("%.1fs",
          (System.currentTimeMillis() - phase3Start) / 1000.0));

      // ---- Phase 4: typecheck the target ---------------------------------------
      ModulePath targetPath = ModulePath.fromString(targetName);
      ModuleLocation targetLoc =
          new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE, targetPath);
      Assume.assumeTrue(
          "Target module " + targetName + " not present in arend-lib/src – set -D"
              + TARGET_PROPERTY + "=<module> to override",
          allModulePaths.contains(targetPath));

      log("Phase 4: typechecking " + targetName);
      long phase4Start = System.currentTimeMillis();

      AtomicInteger itemCount = new AtomicInteger();
      ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progress =
          new ProgressReporter<>() {
            @Override public void beginProcessing(int n) {
              log("  typechecking " + n + " items");
            }
            @Override public void beginItem(List<? extends Concrete.ResolvableDefinition> item) {}
            @Override public void endItem(List<? extends Concrete.ResolvableDefinition> item) {
              int total = itemCount.addAndGet(item.size());
              if (total % 200 == 0) log("  " + total + " items typechecked (elapsed: " + elapsed() + ")");
            }
          };
      server.getCheckerFor(Collections.singletonList(targetLoc))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, progress);
      log("Phase 4 complete in " + String.format("%.1fs",
          (System.currentTimeMillis() - phase4Start) / 1000.0));

      // ---- Phase 5: triage errors ---------------------------------------------
      List<String> contradictionErrors = new ArrayList<>();
      List<String> otherErrors = new ArrayList<>();
      for (Map.Entry<ModuleLocation, List<GeneralError>> entry : server.getErrorMap().entrySet()) {
        String modKey = entry.getKey().getModulePath().toString();
        for (GeneralError err : entry.getValue()) {
          String msg = err.getShortMessage();
          String line = modKey + " :: " + msg;
          if (msg.contains("Meta 'contradiction'") || msg.contains("Cannot infer contradiction")) {
            contradictionErrors.add(line);
          } else {
            otherErrors.add(line);
          }
        }
      }
      log("Errors: " + contradictionErrors.size() + " contradiction-related, "
          + otherErrors.size() + " other");
      for (String e : contradictionErrors) log("  CONTRADICTION: " + e);
      for (String e : otherErrors) log("  OTHER: " + e);

      if (!contradictionErrors.isEmpty()) {
        fail("Partial-cache repro reproduced contradiction-meta failure(s):\n  "
            + String.join("\n  ", contradictionErrors)
            + (otherErrors.isEmpty() ? ""
                : "\n(Plus " + otherErrors.size() + " unrelated errors — see log)"));
      }
    } finally {
      log("=== ArendLib partial-cache repro END (total: " + elapsed() + ") ===");
      closeLog();
      if (tempBin != null && Files.isDirectory(tempBin)) {
        try {
          Files.walkFileTree(tempBin, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
        } catch (IOException e) {
          System.err.println("[WARN] failed to clean " + tempBin + ": " + e);
        }
      }
      System.out.println("[ArendLibPartialCacheTest] Full log: "
          + (logFile == null ? "(no log)" : logFile.toAbsolutePath().toString()));
    }
  }
}
