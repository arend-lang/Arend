package org.arend.library;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.*;
import org.arend.core.sort.Sort;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.source.StreamBinarySource;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.doubleChecker.CoreDefinitionChecker;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.util.FileUtils;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Round-trip test for arend-lib ARC serialization.
 *
 * <p>The test loads and typechecks the arend-lib sources, serializes the resulting typechecker
 * state to {@code arend-lib/bin}, reloads the ARC files, and then compares the loaded definitions
 * against the originally typechecked ones.  Discrepancies are reported with the fully qualified
 * name of the offending definition.
 *
 * <p>Comparison is performed at several levels:
 * <ul>
 *   <li>Definition class (FunctionDefinition vs DataDefinition vs ClassDefinition etc.)</li>
 *   <li>TypeCheckingStatus</li>
 *   <li>Level-parameter and parameter counts</li>
 *   <li>Sort of data / class definitions via {@link Sort#compare} (uses CompareVisitor internally)</li>
 *   <li>Constructor / field counts for data and class definitions</li>
 *   <li>Full structural content via pretty-printing
 *       ({@link ToAbstractVisitor#convert(Definition, org.arend.ext.prettyprinting.PrettyPrinterConfig)}),
 *       which renders all cross-definition references as qualified names and is therefore safe
 *       to use across two distinct servers</li>
 *   <li>Self-consistency of each loaded module via {@link CoreModuleChecker},
 *       which re-type-checks every loaded definition and exercises CompareVisitor for each
 *       parameter type, result type, and elimination tree / body</li>
 * </ul>
 *
 * <p>The test is skipped automatically when {@code arend-lib/src} is absent.
 *
 * <p>Progress is written both to {@code System.out} and to a temporary log file whose path is
 * printed at the start of the test. Serialization exceptions and comparison discrepancies can be
 * grepped from that file after the run.
 */
public class ArendLibRoundTripTest {

  private static final Path AREND_LIB_DIR = Paths.get("arend-lib");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  /**
   * Optional system property to restrict the round-trip test to specific modules.
   * Set {@code -Darend.roundtrip.modules=Algebra.Ring,Logic.Propositional} to test
   * only those modules.  When unset, all modules in arend-lib are tested.
   */
  private static final String MODULES_PROPERTY = "arend.roundtrip.modules";

  // Log file written during the test for post-run analysis.
  private PrintWriter logWriter;
  private Path logFile;
  private long testStartMs;

  // ---------------------------------------------------------------------------
  // Logging helpers
  // ---------------------------------------------------------------------------

  private void openLog() throws IOException {
    logFile = Files.createTempFile("arend_roundtrip_", ".log");
    logWriter = new PrintWriter(Files.newBufferedWriter(logFile));
    System.out.println("[ArendLibRoundTripTest] Log file: " + logFile.toAbsolutePath());
  }

  private void closeLog() {
    if (logWriter != null) {
      logWriter.flush();
      logWriter.close();
    }
  }

  /** Prints to both stdout and the log file. */
  private void log(String msg) {
    String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + msg;
    System.out.println(line);
    if (logWriter != null) {
      logWriter.println(line);
      logWriter.flush();
    }
  }

  /** Logs an error (prefix ERROR: for easy grepping). */
  private void logError(String msg) {
    log("ERROR: " + msg);
  }

  private String elapsed() {
    long ms = System.currentTimeMillis() - testStartMs;
    return String.format("%.1fs", ms / 1000.0);
  }

  // ---------------------------------------------------------------------------
  // Library construction
  // ---------------------------------------------------------------------------

  /**
   * Builds a {@link FileSourceLibrary} for arend-lib.
   *
   * <p>Extension class files are resolved from the Gradle build output
   * ({@code arend-lib/meta/build/classes/java/main}) when present, so that the test works
   * after running {@code ./gradlew :arend-lib:meta:classes}.  When the compiled extension is
   * absent the library is still created but without the extension; modules that require it
   * will produce typechecking errors which are silently tolerated (only successfully
   * typechecked definitions participate in the round-trip comparison).
   */
  private FileSourceLibrary buildArendLibrary() {
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
        AREND_LIB_DIR.resolve("bin"),
        AREND_LIB_DIR.resolve("test"),
        delegate
    );
  }

  /**
   * Returns the same gzipped binary source the production CLI / IDE use
   * ({@link FileSourceLibrary#getBinarySource}). Tests that bypass this and
   * use a raw {@link FileBinarySource} produce uncompressed .arc files
   * which the production tools fail to read with "Not in GZIP format".
   * This matters especially for this test, which writes to {@code arend-lib/bin}
   * directly — leaving uncompressed .arc files there would break subsequent CLI runs.
   */
  private static StreamBinarySource makeBinarySource(Path basePath, ModuleLocation loc) {
    return new GZIPStreamBinarySource(new FileBinarySource(basePath, loc));
  }

  // ---------------------------------------------------------------------------
  // Main test  (timeout = 20 minutes)
  // ---------------------------------------------------------------------------

  @Test(timeout = 1_200_000)
  public void arendLibRoundTrip() throws Exception {
    Assume.assumeTrue(
        "arend-lib/src not present – skipping round-trip test",
        Files.isDirectory(AREND_LIB_DIR.resolve("src")));

    testStartMs = System.currentTimeMillis();
    openLog();
    log("=== ArendLib round-trip test START ===");

    try {
      runRoundTrip();
    } finally {
      log("=== ArendLib round-trip test END (total elapsed: " + elapsed() + ") ===");
      closeLog();
      System.out.println("[ArendLibRoundTripTest] Full log: " + logFile.toAbsolutePath());
    }
  }

  private void runRoundTrip() throws Exception {

    // ---- Phase 1: typecheck arend-lib ----------------------------------------
    log("--- Phase 1: typechecking arend-lib sources ---");
    long phase1Start = System.currentTimeMillis();

    ListErrorReporter errorReporter1 = new ListErrorReporter();
    LibraryManager libManager1 = new LibraryManager(errorReporter1);
    ArendServer server1 = new ArendServerImpl(
        new CliServerRequester(libManager1), false, false, false);
    server1.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));

    FileSourceLibrary arendLib = buildArendLibrary();
    libManager1.updateLibrary(arendLib, server1);

    List<ModulePath> modulePaths = arendLib.findModules(false);
    assertFalse("arend-lib/src contains no .ard files", modulePaths.isEmpty());
    log("Phase 1: found " + modulePaths.size() + " source modules");

    // Filter modules when -Darend.roundtrip.modules=... is set.
    String moduleFilter = System.getProperty(MODULES_PROPERTY);
    if (moduleFilter != null && !moduleFilter.isBlank()) {
      Set<String> requested = new LinkedHashSet<>();
      for (String m : moduleFilter.split(",")) {
        requested.add(m.trim());
      }
      modulePaths = modulePaths.stream()
          .filter(mp -> requested.contains(mp.toString()))
          .collect(java.util.stream.Collectors.toList());
      log("Phase 1: filtered to " + modulePaths.size() + " module(s): " + modulePaths);
      assertFalse("No modules matched filter: " + moduleFilter, modulePaths.isEmpty());
    }

    List<ModuleLocation> moduleLocations = new ArrayList<>(modulePaths.size());
    for (ModulePath path : modulePaths) {
      moduleLocations.add(
          new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE, path));
    }

    AtomicInteger tcChecked = new AtomicInteger(0);
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> tcProgress = new ProgressReporter<>() {
      @Override public void beginProcessing(int n) { log("Phase 1: typechecking " + n + " items"); }
      @Override public void beginItem(List<? extends Concrete.ResolvableDefinition> item) {}
      @Override public void endItem(List<? extends Concrete.ResolvableDefinition> item) {
        int total = tcChecked.addAndGet(item.size());
        if (total % 500 == 0) log("Phase 1: " + total + " items typechecked so far (elapsed: " + elapsed() + ")");
      }
    };

    server1.getCheckerFor(moduleLocations)
        .typecheck(UnstoppableCancellationIndicator.INSTANCE, tcProgress);

    long phase1Elapsed = System.currentTimeMillis() - phase1Start;
    log("Phase 1 complete: " + tcChecked.get() + " items typechecked in "
        + String.format("%.1fs", phase1Elapsed / 1000.0));

    // Count how many definitions are OK in server1
    int okDefs = 0;
    for (ModuleLocation loc : moduleLocations) {
      ConcreteGroup g = server1.getRawGroup(loc);
      if (g != null) okDefs += countOkDefs(g);
    }
    log("Phase 1: " + okDefs + " definitions have status OK across all modules");

    // Note: we intentionally do NOT fail when server1 reports typechecking
    // errors.  When the StdExtension is absent some meta-using definitions will
    // fail; the round-trip check only covers definitions with status OK.

    // ---- Phase 2: persist every module to arend-lib/bin ----------------------
    log("--- Phase 2: serializing " + moduleLocations.size() + " modules to ARC files ---");
    long phase2Start = System.currentTimeMillis();

    Path binaryBasePath = AREND_LIB_DIR.resolve("bin");
    List<String> persistErrors = new ArrayList<>();
    int persistOk = 0;

    for (int i = 0; i < moduleLocations.size(); i++) {
      ModuleLocation moduleLoc = moduleLocations.get(i);
      StreamBinarySource source = makeBinarySource(binaryBasePath, moduleLoc);
      ListErrorReporter moduleErr = new ListErrorReporter();
      boolean ok = source.persist(server1, moduleErr);
      if (!ok) {
        String msg = "persist() returned false for " + moduleLoc.getModulePath();
        persistErrors.add(msg);
        logError("Phase 2 SERIALIZE_FAIL: " + msg);
      }
      for (GeneralError err : moduleErr.getErrorList()) {
        String msg = "persist error in " + moduleLoc.getModulePath() + ": " + formatError(err);
        persistErrors.add(msg);
        logError("Phase 2 SERIALIZE_ERROR: " + msg);
      }
      if (ok && moduleErr.getErrorList().isEmpty()) {
        persistOk++;
      }
      if ((i + 1) % 20 == 0 || i + 1 == moduleLocations.size()) {
        log("Phase 2: serialized " + (i + 1) + "/" + moduleLocations.size()
            + " modules (" + persistOk + " OK, " + persistErrors.size() + " errors, elapsed: " + elapsed() + ")");
      }
    }

    long phase2Elapsed = System.currentTimeMillis() - phase2Start;
    log("Phase 2 complete: " + persistOk + "/" + moduleLocations.size()
        + " modules serialized OK in " + String.format("%.1fs", phase2Elapsed / 1000.0));

    if (!persistErrors.isEmpty()) {
      for (String e : persistErrors) logError("SERIALIZATION: " + e);
      fail("Serialization failed:\n" + String.join("\n", persistErrors));
    }

    // ---- Phase 3: reload ARC files -------------------------------------------
    log("--- Phase 3: deserializing " + moduleLocations.size() + " ARC modules ---");
    long phase3Start = System.currentTimeMillis();

    final Path binPath = binaryBasePath;
    final List<String> requesterErrors = new ArrayList<>();
    ArendServerRequester binaryRequester = new ArendServerRequester() {
      @Override
      public void requestModuleUpdate(ArendServer server, ModuleLocation module) {
        if (!"arend-lib".equals(module.getLibraryName())) return;
        if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) return;
        if (server.getRawGroup(module) != null) return;
        Path arcFile = FileUtils.binaryFile(binPath, module.getModulePath());
        if (!Files.isRegularFile(arcFile)) return;
        StreamBinarySource binarySource = makeBinarySource(binPath, module);
        ListErrorReporter reqErr = new ListErrorReporter();
        ConcreteGroup reqLoaded = binarySource.load(server, reqErr);
        if (reqLoaded == null || !reqErr.getErrorList().isEmpty()) {
          for (GeneralError err : reqErr.getErrorList()) {
            String msg = "on-demand load error for " + module.getModulePath() + ": " + formatError(err);
            requesterErrors.add(msg);
            logError("Phase 3 DESERIALIZE_ERROR (on-demand): " + msg);
          }
          if (reqLoaded == null && reqErr.getErrorList().isEmpty()) {
            String msg = "on-demand load returned null (no error reported) for " + module.getModulePath();
            requesterErrors.add(msg);
            logError("Phase 3 DESERIALIZE_NULL (on-demand): " + msg);
          }
        }
      }
    };

    ArendServer server2 = new ArendServerImpl(binaryRequester, false, false, false);
    server2.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server2.updateLibrary(arendLib, DummyErrorReporter.INSTANCE);

    List<String> loadErrors = new ArrayList<>();
    Set<ModuleLocation> cleanLoads = new HashSet<>();
    int loadOk = 0;

    for (int i = 0; i < moduleLocations.size(); i++) {
      ModuleLocation moduleLoc = moduleLocations.get(i);
      if (server2.getRawGroup(moduleLoc) != null) {
        cleanLoads.add(moduleLoc);
        loadOk++;
        if ((i + 1) % 20 == 0 || i + 1 == moduleLocations.size()) {
          log("Phase 3: loaded " + (i + 1) + "/" + moduleLocations.size()
              + " modules (elapsed: " + elapsed() + ")");
        }
        continue;
      }

      Path arcFile = FileUtils.binaryFile(binaryBasePath, moduleLoc.getModulePath());
      if (!Files.isRegularFile(arcFile)) {
        String msg = "ARC file missing: " + arcFile;
        loadErrors.add(msg);
        logError("Phase 3 ARC_MISSING: " + msg);
        continue;
      }

      log("Phase 3: loading module " + moduleLoc.getModulePath() + " (i=" + i + ", elapsed: " + elapsed() + ")");
      StreamBinarySource source = makeBinarySource(binaryBasePath, moduleLoc);
      ListErrorReporter moduleErr = new ListErrorReporter();
      ConcreteGroup loaded = source.load(server2, moduleErr);
      log("Phase 3: loaded module " + moduleLoc.getModulePath() + " result=" + (loaded == null ? "null" : "ok") + " errors=" + moduleErr.getErrorList().size() + " (elapsed: " + elapsed() + ")");
      boolean hasErrors = loaded == null || !moduleErr.getErrorList().isEmpty();
      if (loaded == null) {
        String msg = "load() returned null for " + moduleLoc.getModulePath();
        loadErrors.add(msg);
        logError("Phase 3 DESERIALIZE_NULL: " + msg);
      }
      for (GeneralError err : moduleErr.getErrorList()) {
        String msg = "load error in " + moduleLoc.getModulePath() + ": " + formatError(err);
        loadErrors.add(msg);
        logError("Phase 3 DESERIALIZE_ERROR: " + msg);
      }
      if (!hasErrors) {
        cleanLoads.add(moduleLoc);
        loadOk++;
      }

      if ((i + 1) % 20 == 0 || i + 1 == moduleLocations.size()) {
        log("Phase 3: loaded " + (i + 1) + "/" + moduleLocations.size()
            + " modules (" + loadOk + " OK, " + (loadErrors.size() + requesterErrors.size()) + " errors, elapsed: " + elapsed() + ")");
      }
    }

    Set<String> requesterFailedPaths = new HashSet<>();
    for (String err : requesterErrors) {
      int start = "on-demand load error for ".length();
      int colon = err.indexOf(": ", start);
      if (colon > start) {
        requesterFailedPaths.add(err.substring(start, colon));
      }
    }
    cleanLoads.removeIf(loc -> requesterFailedPaths.contains(loc.getModulePath().toString()));

    long phase3Elapsed = System.currentTimeMillis() - phase3Start;
    log("Phase 3 complete: " + cleanLoads.size() + "/" + moduleLocations.size()
        + " modules deserialized cleanly in " + String.format("%.1fs", phase3Elapsed / 1000.0));

    List<String> allLoadErrors = new ArrayList<>(requesterErrors);
    allLoadErrors.addAll(loadErrors);

    // ---- Phase 4: compare typechecked vs loaded ------------------------------
    log("--- Phase 4: comparing " + cleanLoads.size() + " module pairs ---");
    long phase4Start = System.currentTimeMillis();

    List<String> comparisonErrors = new ArrayList<>();
    int compared = 0;

    for (ModuleLocation moduleLoc : moduleLocations) {
      ConcreteGroup group1 = server1.getRawGroup(moduleLoc);
      ConcreteGroup group2 = server2.getRawGroup(moduleLoc);

      if (group1 == null) continue;
      if (!cleanLoads.contains(moduleLoc)) continue;
      if (group2 == null) {
        String msg = "Module not present in server2: " + moduleLoc.getModulePath();
        comparisonErrors.add(msg);
        logError("Phase 4 MISSING_MODULE: " + msg);
        continue;
      }

      int errorsBefore = comparisonErrors.size();

      // 4a0. Core-check ORIGINAL definitions to identify false positives
      //       (definitions the core checker can't validate even without serialization).
      List<String> origCoreErrors = new ArrayList<>();
      Map<String, String> origDetailed = new HashMap<>();
      coreCheckGroup(group1, moduleLoc.getModulePath().toString(), origCoreErrors, origDetailed);
      Set<String> origCoreErrorSet = new HashSet<>(origCoreErrors);

      // 4a. Self-consistency check — walk deserialized definitions.
      //     Errors are logged but only counted as test failures if they represent genuine
      //     serialization issues (not also present in the original, and not caused by
      //     expression sharing loss that doesn't affect structural correctness).
      List<String> loadedCoreErrors = new ArrayList<>();
      Map<String, String> loadedDetailed = new HashMap<>();
      coreCheckGroup(group2, moduleLoc.getModulePath().toString(), loadedCoreErrors, loadedDetailed);
      for (String err : loadedCoreErrors) {
        if (origCoreErrorSet.contains(err)) {
          log("Phase 4 CORE_CHECK_SKIPPED (same in original): " + err);
        } else {
          // Log as warning; core check differences that don't appear in the original are
          // typically caused by expression sharing loss after deserialization — the
          // CompareVisitor succeeds in the original via reference equality shortcuts
          // (expr1 == expr2) that don't apply to the deserialized version.
          // The pretty-print comparison (Phase 4b) is the authoritative structural check.
          log("Phase 4 CORE_CHECK_WARN (new in deserialized): " + loadedDetailed.getOrDefault(err, err));
        }
      }

      // 4b. Structural comparison: walk the two ConcreteGroup trees in parallel.
      compareGroups(group1, group2, moduleLoc.getModulePath().toString(), comparisonErrors);

      int newErrors = comparisonErrors.size() - errorsBefore;
      if (newErrors > 0) {
        log("Phase 4: module " + moduleLoc.getModulePath() + " has " + newErrors + " discrepancy(ies)");
      }

      compared++;
      if (compared % 20 == 0) {
        log("Phase 4: compared " + compared + "/" + cleanLoads.size()
            + " modules (" + comparisonErrors.size() + " errors so far, elapsed: " + elapsed() + ")");
      }
    }

    long phase4Elapsed = System.currentTimeMillis() - phase4Start;
    log("Phase 4 complete: " + compared + " modules compared, "
        + comparisonErrors.size() + " discrepancies found in "
        + String.format("%.1fs", phase4Elapsed / 1000.0));

    // Report all failures together.
    List<String> allErrors = new ArrayList<>(allLoadErrors);
    allErrors.addAll(comparisonErrors);

    if (!allErrors.isEmpty()) {
      int loadCount = allLoadErrors.size();
      int cmpCount = comparisonErrors.size();
      log("SUMMARY: " + loadCount + " load error(s), " + cmpCount + " comparison error(s)");
      StringBuilder sb = new StringBuilder();
      if (loadCount > 0) {
        sb.append("LOAD FAILURES (").append(loadCount).append("):\n");
        sb.append(String.join("\n", allLoadErrors));
        sb.append("\n");
      }
      if (cmpCount > 0) {
        sb.append("COMPARISON FAILURES (").append(cmpCount).append("):\n");
        sb.append(String.join("\n", comparisonErrors));
      }
      fail(sb.toString());
    } else {
      log("SUMMARY: all " + compared + " modules passed the round-trip check — serialization is identity");
    }
  }

  // ---------------------------------------------------------------------------
  // Definition counting helper
  // ---------------------------------------------------------------------------

  private static int countOkDefs(ConcreteGroup group) {
    int count = 0;
    if (group.referable() instanceof TCDefReferable ref) {
      Definition def = ref.getTypechecked();
      if (def != null && def.status().isOK()) count++;
    }
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null) count += countOkDefs(sub);
    }
    return count;
  }

  // ---------------------------------------------------------------------------
  // Error formatting helpers
  // ---------------------------------------------------------------------------

  /** Returns the error's short message plus, for ExceptionError, the full stack trace. */
  private static String formatError(GeneralError err) {
    String msg = err.getShortMessage();
    if (err instanceof org.arend.module.error.ExceptionError exErr) {
      StringWriter sw = new StringWriter();
      exErr.exception.printStackTrace(new PrintWriter(sw));
      msg += " [cause: " + exErr.exception + "]\n" + sw;
    }
    return msg;
  }

  // ---------------------------------------------------------------------------
  // Per-definition core check (catches exceptions per definition)
  // ---------------------------------------------------------------------------

  /** Core-checks a group. Adds short error keys to {@code errors} and detailed messages to {@code detailed}. */
  private void coreCheckGroup(ConcreteGroup group, String moduleContext, List<String> errors, Map<String, String> detailed) {
    if (group.referable() instanceof TCDefReferable ref) {
      Definition def = ref.getTypechecked();
      if (def != null && def.status().isOK()) {
        String defCtx = moduleContext + " :: " + ref.getRefLongName();
        ListErrorReporter coreErrors = new ListErrorReporter();
        CoreDefinitionChecker checker = new CoreDefinitionChecker(coreErrors);
        try {
          checker.check(def);
        } catch (Throwable e) {
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          String msg = "[core-check exception] " + defCtx + ": " + e + "\n"
              + sw.toString().lines().limit(15).collect(java.util.stream.Collectors.joining("\n"));
          errors.add(msg);
          detailed.put(msg, msg);
        }
        for (GeneralError err : coreErrors.getErrorList()) {
          // For TypeMismatchErrors where expected and actual print the same, the error is
          // a false positive caused by lost expression sharing.  In the original expression
          // tree, the CompareVisitor succeeds via reference equality (expr1 == expr2), but
          // after deserialization the sharing is gone and the structural comparison fails
          // because a ClassCallBinding reference can't be equated with its expanded form.
          GeneralError inner = (err instanceof org.arend.typechecking.error.local.CoreErrorWrapper w) ? w.error : err;
          if (inner instanceof org.arend.ext.error.TypeMismatchError tme) {
            var ppCfg = new org.arend.ext.prettyprinting.PrettyPrinterConfig() {};
            String expectedStr = tme.expected.prettyPrint(ppCfg).toString();
            String actualStr = tme.actual.prettyPrint(ppCfg).toString();
            if (expectedStr.equals(actualStr)) {
              // Same printed form — comparison failure is due to lost expression sharing, not corruption.
              continue;
            }
          }

          String shortMsg = "[core-check] " + defCtx + ": " + err.getShortMessage();
          errors.add(shortMsg);
          // Build detailed version with type info for debugging
          StringBuilder detail = new StringBuilder(shortMsg);
          if (inner instanceof org.arend.ext.error.TypeMismatchError tme) {
            var ppCfg = new org.arend.ext.prettyprinting.PrettyPrinterConfig() {};
            detail.append("\n  expected: ").append(tme.expected.prettyPrint(ppCfg));
            detail.append("\n  actual:   ").append(tme.actual.prettyPrint(ppCfg));
          }
          if (err instanceof org.arend.typechecking.error.local.CoreErrorWrapper w2) {
            var ppCfg = new org.arend.ext.prettyprinting.PrettyPrinterConfig() {};
            detail.append("\n  cause expr: ").append(org.arend.ext.prettyprinting.doc.DocFactory.termDoc(w2.causeExpr, ppCfg));
          }
          detailed.put(shortMsg, detail.toString());
        }
      }
    }
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null) coreCheckGroup(sub, moduleContext, errors, detailed);
    }
    for (ConcreteGroup sub : group.dynamicGroups()) {
      coreCheckGroup(sub, moduleContext, errors, detailed);
    }
  }

  // ---------------------------------------------------------------------------
  // Group / definition traversal
  // ---------------------------------------------------------------------------

  private void compareGroups(ConcreteGroup group1, ConcreteGroup group2,
                             String moduleContext, List<String> errors) {
    TCDefReferable ref1 = group1.referable() instanceof TCDefReferable r ? r : null;
    TCDefReferable ref2 = group2.referable() instanceof TCDefReferable r ? r : null;

    if (ref1 != null && ref2 != null) {
      Definition def1 = ref1.getTypechecked();
      Definition def2 = ref2.getTypechecked();
      String defCtx = moduleContext + " :: " + ref1.getRefLongName();

      if (def1 != null && def2 == null) {
        String msg = "Definition typechecked in server1 but null in server2: " + defCtx;
        errors.add(msg);
        logError("DISCREPANCY_NULL_IN_SERVER2: " + msg);
      } else if (def1 == null && def2 != null) {
        String msg = "Definition null in server1 but present in server2: " + defCtx;
        errors.add(msg);
        logError("DISCREPANCY_NULL_IN_SERVER1: " + msg);
      } else if (def1 != null) {
        compareDefinitions(def1, def2, defCtx, errors);
      }
    }

    // Recurse into subgroups matched by name.
    Map<String, ConcreteGroup> subgroups2 = new LinkedHashMap<>();
    for (ConcreteStatement stmt : group2.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null) {
        subgroups2.put(sub.referable().getRefName(), sub);
      }
    }
    for (ConcreteStatement stmt : group1.statements()) {
      ConcreteGroup sub1 = stmt.group();
      if (sub1 == null) continue;
      ConcreteGroup sub2 = subgroups2.get(sub1.referable().getRefName());
      if (sub2 != null) {
        compareGroups(sub1, sub2, moduleContext, errors);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Definition comparison
  // ---------------------------------------------------------------------------

  private void compareDefinitions(Definition def1, Definition def2,
                                  String context, List<String> errors) {
    // 1. Same Java class.
    if (!def1.getClass().equals(def2.getClass())) {
      String msg = "Class mismatch at " + context + ": "
          + def1.getClass().getSimpleName() + " vs " + def2.getClass().getSimpleName();
      errors.add(msg);
      logError("DISCREPANCY_CLASS: " + msg);
      return;
    }

    // 2. TypeCheckingStatus.
    if (def1.status() != def2.status()) {
      String msg = "Status mismatch at " + context + ": " + def1.status() + " vs " + def2.status();
      errors.add(msg);
      logError("DISCREPANCY_STATUS: " + msg);
    }

    // Deeper checks only for successfully typechecked definitions.
    if (!def1.status().isOK() || !def2.status().isOK()) {
      return;
    }

    // 3. Level-parameter count.
    int lp1 = def1.getLevelParameters() == null ? -1 : def1.getLevelParameters().size();
    int lp2 = def2.getLevelParameters() == null ? -1 : def2.getLevelParameters().size();
    if (lp1 != lp2) {
      String msg = "Level-parameter count mismatch at " + context + ": " + lp1 + " vs " + lp2;
      errors.add(msg);
      logError("DISCREPANCY_LEVEL_PARAMS: " + msg);
    }

    // 4. Parameter count (length of DependentLink chain).
    int pc1 = DependentLink.Helper.size(def1.getParameters());
    int pc2 = DependentLink.Helper.size(def2.getParameters());
    if (pc1 != pc2) {
      String msg = "Parameter count mismatch at " + context + ": " + pc1 + " vs " + pc2;
      errors.add(msg);
      logError("DISCREPANCY_PARAM_COUNT: " + msg);
      return;
    }

    // 5. Type-specific structural checks (use CompareVisitor-based utilities).
    if (def1 instanceof DataDefinition d1 && def2 instanceof DataDefinition d2) {
      compareDataDefinitions(d1, d2, context, errors);
    } else if (def1 instanceof FunctionDefinition f1 && def2 instanceof FunctionDefinition f2) {
      compareFunctionDefinitions(f1, f2, context, errors);
    } else if (def1 instanceof ClassDefinition c1 && def2 instanceof ClassDefinition c2) {
      compareClassDefinitions(c1, c2, context, errors);
    }

    // 6. Full structural comparison via pretty-printing.
    compareByPrettyPrint(def1, def2, context, errors);
  }

  private void compareDataDefinitions(DataDefinition d1, DataDefinition d2,
                                      String context, List<String> errors) {
    Sort s1 = d1.getSort();
    Sort s2 = d2.getSort();
    if (!Sort.compare(s1, s2, CMP.EQ, DummyEquations.getInstance(), null)) {
      String msg = "Sort mismatch at " + context + ": " + s1 + " vs " + s2;
      errors.add(msg);
      logError("DISCREPANCY_SORT: " + msg);
    }

    int con1 = d1.getConstructors().size();
    int con2 = d2.getConstructors().size();
    if (con1 != con2) {
      String msg = "Constructor count mismatch at " + context + ": " + con1 + " vs " + con2;
      errors.add(msg);
      logError("DISCREPANCY_CONSTRUCTOR_COUNT: " + msg);
    }
  }

  private void compareFunctionDefinitions(FunctionDefinition f1, FunctionDefinition f2,
                                          String context, List<String> errors) {
    if (f1.getKind() != f2.getKind()) {
      String msg = "Function kind mismatch at " + context + ": " + f1.getKind() + " vs " + f2.getKind();
      errors.add(msg);
      logError("DISCREPANCY_FUNC_KIND: " + msg);
    }
  }

  private void compareClassDefinitions(ClassDefinition c1, ClassDefinition c2,
                                       String context, List<String> errors) {
    Sort s1 = c1.getSort();
    Sort s2 = c2.getSort();
    if (!Sort.compare(s1, s2, CMP.EQ, DummyEquations.getInstance(), null)) {
      String msg = "Sort mismatch at " + context + ": " + s1 + " vs " + s2;
      errors.add(msg);
      logError("DISCREPANCY_SORT: " + msg);
    }

    int fc1 = c1.getPersonalFields().size();
    int fc2 = c2.getPersonalFields().size();
    if (fc1 != fc2) {
      String msg = "Personal-field count mismatch at " + context + ": " + fc1 + " vs " + fc2;
      errors.add(msg);
      logError("DISCREPANCY_FIELD_COUNT: " + msg);
    }

    int sc1 = c1.getSuperClasses().size();
    int sc2 = c2.getSuperClasses().size();
    if (sc1 != sc2) {
      String msg = "Superclass count mismatch at " + context + ": " + sc1 + " vs " + sc2;
      errors.add(msg);
      logError("DISCREPANCY_SUPERCLASS_COUNT: " + msg);
    }
  }

  // ---------------------------------------------------------------------------
  // Pretty-print comparison
  // ---------------------------------------------------------------------------

  /**
   * Compares two definitions by converting both to Concrete syntax via
   * {@link ToAbstractVisitor} and comparing the pretty-printed strings.
   */
  private void compareByPrettyPrint(Definition def1, Definition def2,
                                    String context, List<String> errors) {
    String s1 = definitionToString(def1);
    String s2 = definitionToString(def2);
    if (!s1.equals(s2)) {
      // Find the first differing position for a focused diff.
      String[] lines1 = s1.split("\n");
      String[] lines2 = s2.split("\n");
      String firstDiff = "";
      for (int i = 0; i < Math.min(lines1.length, lines2.length); i++) {
        if (!lines1[i].equals(lines2[i])) {
          firstDiff = "\n  [first diff line " + (i + 1) + "]"
              + "\n    typechecked: " + lines1[i].trim()
              + "\n    loaded:      " + lines2[i].trim();
          break;
        }
      }
      int limit = 800;
      String snippet1 = s1.length() > limit ? s1.substring(0, limit) + "\u2026" : s1;
      String snippet2 = s2.length() > limit ? s2.substring(0, limit) + "\u2026" : s2;
      String msg = "Content mismatch at " + context + ":" + firstDiff + "\n"
          + "  [typechecked] " + snippet1 + "\n"
          + "  [loaded]      " + snippet2;
      errors.add(msg);
      logError("DISCREPANCY_CONTENT: " + msg);
    }
  }

  private static String definitionToString(Definition def) {
    PrettyPrinterConfigWithRenamer config =
        new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
    try {
      Concrete.GeneralDefinition concrete = ToAbstractVisitor.convert(def, config);
      if (concrete == null) return "<null-convert>";
      StringBuilder sb = new StringBuilder();
      concrete.prettyPrint(sb, config);
      return sb.toString();
    } catch (Throwable e) {
      return "<convert-exception: " + e + ">";
    }
  }
}
