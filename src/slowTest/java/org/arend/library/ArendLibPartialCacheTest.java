package org.arend.library;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
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
 * Repro test for spurious typechecking errors reported when an upstream arend-lib module's
 * source is touched and the CLI satisfies the rest of the library from the now-partial binary
 * cache. Originally written for one instance of this — the "secondary contradiction" failure
 * mode (commit 78de87dae and friends) — and since generalised, because the same setup with a
 * different invalidation point surfaces structural errors that have nothing to do with
 * {@code contradiction}.
 *
 * <p>Scenario:
 * <ol>
 *   <li>Take a fully built ARC cache (the test requires {@code arend-lib/bin} to already
 *       contain a complete set of .arc files).</li>
 *   <li>Mirror that cache to a temp directory and remove the .arc of one upstream module
 *       (default {@code Algebra.Domain}). This simulates the CLI behavior on
 *       {@code touch arend-lib/src/Algebra/Domain.ard}, which makes the timestamp filter
 *       in {@link CliServerRequester#loadBinaryCache} skip that module.</li>
 *   <li>Build a fresh {@link ArendServer} that loads .ard for every module (so the
 *       concrete tree is present everywhere) and applies the standard
 *       {@code loadBinaryCache} cascade against the trimmed cache. The cascade marks
 *       cross-module references through the deleted module as "incomplete"/"failed",
 *       forcing re-typecheck of a non-trivial subset from source while the rest stay
 *       pure-deserialized.</li>
 *   <li>Type-check every module, the way {@code arend} with no MODULE positional does
 *       (see {@link #WHOLE_LIBRARY}; set it to {@code false} to narrow to
 *       {@code -Darend.partial_cache.target}, default {@code Arith.Exp}).</li>
 *   <li>Fail iff the run produced any non-{@code GOAL} error. The sources are untouched —
 *       only a {@code .arc} was removed — so every such error is caused by the partial
 *       cache. {@code Meta 'contradiction' failed} / {@code Cannot infer contradiction}
 *       errors are reported under their own heading, since they were this test's original
 *       subject.</li>
 * </ol>
 *
 * <p>Two invalidation points are known to reproduce real CLI failures:
 * <pre>
 *   -Darend.partial_cache.touched=Algebra.Domain              # contradiction metas
 *   -Darend.partial_cache.touched=Topology.Locale.PreorderSite # spurious structural errors
 * </pre>
 * The second is the one {@link ArendLibPartialRoundTripTest} cannot see (that test hand-rolls
 * its ARC overlay and so never runs {@code loadBinaryCache}); it reports ~25 errors in
 * {@code Topology.CoverSpace.Locale} and {@code Topology.Locale.Points}, whose sources are
 * unchanged and which typecheck cleanly under {@code arend -r}.
 *
 * <p>Differences from {@link ArendLibPartialRoundTripTest}:
 * <ul>
 *   <li>Loads the cone modules <em>purely from ARC</em>, with NO source-tree overlay on
 *       loaded modules. That's the difference that exposes the visitor's deserialized
 *       super-walk / Java-meta gaps — they only fire when {@code myConcreteProvider} has
 *       no concrete for an upstream class.</li>
 *   <li>Reuses the production {@link CliServerRequester#loadBinaryCache} (and its
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

  /**
   * Whether Phase 4 typechecks every module (as {@code arend} with no MODULE positional
   * does) or only {@code -Darend.partial_cache.target}. Whole-library is the default because
   * a target-scoped typecheck cannot see defects triggered by modules the target does not
   * import — see the comment at the Phase 4 call site for the measured case.
   */
  private static final boolean WHOLE_LIBRARY = true;

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
  public void partialCacheSpuriousErrorRepro() throws Exception {
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
      requester.loadBinaryCache(arendLib, server);
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

      log("Phase 4: typechecking " + (WHOLE_LIBRARY ? "the whole library" : targetName));
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
      // Drive the typecheck exactly the way ConsoleMain does when no MODULE positional is
      // given: one checker call per module, in findModules() order. Scope matters here, it
      // is not just a speed knob — typechecking the target alone lets the whole
      // Topology.Locale.PreorderSite case pass clean (deleting Topology/CoverSpace/Locale.arc
      // and typechecking only that module reports 0 errors, while the same cache under a
      // whole-library run reports 25). Restricting to the target hides any defect whose
      // trigger is a module the target does not import.
      List<ModuleLocation> toCheck = WHOLE_LIBRARY
          ? allLocations
          : Collections.singletonList(targetLoc);
      for (ModuleLocation loc : toCheck) {
        server.getCheckerFor(Collections.singletonList(loc))
            .typecheck(UnstoppableCancellationIndicator.INSTANCE, progress);
      }
      log("Phase 4 complete in " + String.format("%.1fs",
          (System.currentTimeMillis() - phase4Start) / 1000.0));

      // ---- Phase 5: triage errors ---------------------------------------------
      // Any ERROR at all is a finding: the sources are unmodified (only a .arc was removed),
      // and arend-lib's committed state typechecks clean from source, so every error here is
      // caused by the partial cache. GOALs are exempt — arend-lib commits carry those
      // deliberately. `contradiction` errors are still called out separately because that was
      // this test's original subject and stays worth recognising on sight.
      List<String> contradictionErrors = new ArrayList<>();
      List<String> otherErrors = new ArrayList<>();
      int goals = 0;
      for (Map.Entry<ModuleLocation, List<GeneralError>> entry : server.getErrorMap().entrySet()) {
        String modKey = entry.getKey().getModulePath().toString();
        for (GeneralError err : entry.getValue()) {
          if (err.level == GeneralError.Level.GOAL) {
            goals++;
            continue;
          }
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
          + otherErrors.size() + " other, " + goals + " goal(s) ignored");
      for (String e : contradictionErrors) log("  CONTRADICTION: " + e);
      for (String e : otherErrors) log("  OTHER: " + e);

      if (!contradictionErrors.isEmpty() || !otherErrors.isEmpty()) {
        List<String> all = new ArrayList<>(contradictionErrors);
        all.addAll(otherErrors);
        fail("Partial-cache repro produced " + all.size() + " spurious error(s) after deleting "
            + touchedName + ".arc"
            + (contradictionErrors.isEmpty() ? "" : " (" + contradictionErrors.size() + " contradiction-related)")
            + ":\n  " + String.join("\n  ", all));
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
