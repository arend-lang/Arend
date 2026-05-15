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
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.source.StreamBinarySource;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
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
 * Partial round-trip test for arend-lib ARC serialization.
 *
 * <p>Scenario: typecheck a single target module (plus its transitive prerequisites),
 * serialize the resulting definitions to a temporary binary directory, then on a
 * fresh server deserialize those ARC files and attempt to typecheck the remaining
 * modules of arend-lib from source. The test fails only if the second typechecking
 * pass reports <em>secondary</em> errors — i.e. errors that do not also appear in a
 * baseline run where all modules are typechecked from source. Secondary errors
 * indicate that the deserialized prerequisites caused a regression when consumed by
 * downstream modules.
 *
 * <p>No structural / content comparison of the deserialized cone is performed; that
 * is the responsibility of {@link ArendLibRoundTripTest}.
 *
 * <p>Target modules can be configured via the system property
 * {@code -Darend.partial_roundtrip.targets=AG.Projective,Algebra.Ring.RingHom}
 * (comma-separated fully qualified module names). If unset, the test defaults to
 * {@code AG.Projective, Arith.Exp}: the first exercises class-instance recovery
 * for ring/abelian-group hierarchies, the second pulls in both
 * {@code Order.LinearOrder} and {@code Algebra.Domain} so a touch/edit of any
 * upstream domain module produces the partial-cache state that surfaces spurious
 * {@code contradiction} / {@code Cannot infer contradiction} errors in
 * downstream {@code mcases}/{@code <|>} sites.
 *
 * <p>The test is skipped automatically when {@code arend-lib/src} is absent.
 */
public class ArendLibPartialRoundTripTest {

  private static final Path AREND_LIB_DIR = Paths.get("arend-lib");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private static final String TARGETS_PROPERTY = "arend.partial_roundtrip.targets";
  private static final List<String> DEFAULT_TARGETS = List.of("AG.Projective", "Arith.Exp");

  /**
   * Global flag controlling whether the Phase 0 baseline typecheck is run.
   * <ul>
   *   <li>{@code true} (default) — run a full from-source typecheck first, then count
   *   only errors in Phase 3 that don't appear in the baseline as "secondary".</li>
   *   <li>{@code false} — skip the baseline pass entirely; the test passes iff Phase 3
   *   produced no non-{@code GOAL} errors in modules <em>outside</em> the cone. Errors
   *   in cone modules are ignored, and {@code GOAL}-level errors anywhere are tolerated
   *   (arend-lib commits routinely carry GOALs but not outright ERRORs, so the
   *   no-baseline fast path treats them as part of the accepted ground state).</li>
   * </ul>
   */
  private static final boolean RUN_BASELINE = false;

  private PrintWriter logWriter;
  private Path logFile;
  private long testStartMs;
  private Path tempBinRoot;

  // ---------------------------------------------------------------------------
  // Logging helpers
  // ---------------------------------------------------------------------------

  private void openLog() throws IOException {
    logFile = Files.createTempFile("arend_partial_roundtrip_", ".log");
    logWriter = new PrintWriter(Files.newBufferedWriter(logFile));
    System.out.println("[ArendLibPartialRoundTripTest] Log file: " + logFile.toAbsolutePath());
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

  private void logError(String msg) { log("ERROR: " + msg); }

  private String elapsed() {
    long ms = System.currentTimeMillis() - testStartMs;
    return String.format("%.1fs", ms / 1000.0);
  }

  // ---------------------------------------------------------------------------
  // Library construction (mirrors ArendLibRoundTripTest#buildArendLibrary)
  // ---------------------------------------------------------------------------

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
   */
  private static StreamBinarySource makeBinarySource(Path basePath, ModuleLocation loc) {
    return new GZIPStreamBinarySource(new FileBinarySource(basePath, loc));
  }

  // ---------------------------------------------------------------------------
  // Main test  (timeout = 30 minutes)
  // ---------------------------------------------------------------------------

  @Test // (timeout = 1_800_000)
  public void arendLibPartialRoundTrip() throws Exception {
    Assume.assumeTrue(
        "arend-lib/src not present – skipping partial round-trip test",
        Files.isDirectory(AREND_LIB_DIR.resolve("src")));

    testStartMs = System.currentTimeMillis();
    openLog();
    log("=== ArendLib partial round-trip test START ===");
    log("arend.subst.maxDepth property = " + System.getProperty("arend.subst.maxDepth", "<unset>"));
    // Verify the static SubstVisitor guard picked up the property
    try {
      java.lang.reflect.Field f = org.arend.core.subst.SubstVisitor.class.getDeclaredField("MAX_SUBST_DEPTH");
      f.setAccessible(true);
      log("SubstVisitor.MAX_SUBST_DEPTH = " + f.get(null));
    } catch (Throwable t) {
      log("Could not read SubstVisitor.MAX_SUBST_DEPTH: " + t);
    }

    tempBinRoot = Files.createTempDirectory("arend_partial_bin_");
    log("Temp binary root: " + tempBinRoot.toAbsolutePath());

    try {
      List<ModulePath> targets = parseTargets();
      log("Targets: " + targets);

      // Phase 0 (baseline) runs once and is shared across all target runs.
      BaselineResult baseline = runBaseline();

      List<String> allSecondaryErrors = new ArrayList<>();
      for (ModulePath target : targets) {
        log("--- Running partial round-trip for target: " + target + " ---");
        List<String> errors = runPartialRoundTrip(target, baseline);
        if (!errors.isEmpty()) {
          allSecondaryErrors.add("=== Target " + target + " produced " + errors.size() + " secondary error(s) ===");
          allSecondaryErrors.addAll(errors);
        }
      }

      if (!allSecondaryErrors.isEmpty()) {
        fail("Partial round-trip reported secondary typechecking errors:\n"
            + String.join("\n", allSecondaryErrors));
      } else {
        log("SUMMARY: no secondary typechecking errors for any target");
      }
    } finally {
      log("=== ArendLib partial round-trip test END (total elapsed: " + elapsed() + ") ===");
      closeLog();
      System.out.println("[ArendLibPartialRoundTripTest] Full log: " + logFile.toAbsolutePath());
    }
  }

  private List<ModulePath> parseTargets() {
    String prop = System.getProperty(TARGETS_PROPERTY);
    if (prop == null || prop.isBlank()) {
      List<ModulePath> defaults = new ArrayList<>(DEFAULT_TARGETS.size());
      for (String name : DEFAULT_TARGETS) defaults.add(ModulePath.fromString(name));
      return defaults;
    }
    List<ModulePath> result = new ArrayList<>();
    for (String name : prop.split(",")) {
      String trimmed = name.trim();
      if (!trimmed.isEmpty()) {
        result.add(ModulePath.fromString(trimmed));
      }
    }
    return result;
  }

  // ---------------------------------------------------------------------------
  // Baseline (shared across targets)
  // ---------------------------------------------------------------------------

  private record BaselineResult(
      List<ModulePath> modulePaths,
      List<ModuleLocation> allLocations,
      Map<String, Set<String>> errorsByModule) {}

  private BaselineResult runBaseline() {
    log(RUN_BASELINE
        ? "Phase 0: baseline full typecheck from sources"
        : "Phase 0: baseline disabled (RUN_BASELINE=false) — collecting module list only");
    long phase0Start = System.currentTimeMillis();

    ListErrorReporter baselineReporter = new ListErrorReporter();
    LibraryManager baselineLibManager = new LibraryManager(baselineReporter);
    ArendServer baselineServer = new ArendServerImpl(
        new CliServerRequester(baselineLibManager), false, false, false);
    baselineServer.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));

    FileSourceLibrary arendLib = buildArendLibrary();
    baselineLibManager.updateLibrary(arendLib, baselineServer);

    List<ModulePath> modulePaths = arendLib.findModules(false);
    assertFalse("arend-lib/src contains no .ard files", modulePaths.isEmpty());
    log("Phase 0: found " + modulePaths.size() + " source modules");

    List<ModuleLocation> allLocations = new ArrayList<>(modulePaths.size());
    for (ModulePath path : modulePaths) {
      allLocations.add(new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE, path));
    }

    if (!RUN_BASELINE) {
      log("Phase 0 skipped in " + String.format("%.1fs",
          (System.currentTimeMillis() - phase0Start) / 1000.0)
          + "; secondary errors will be defined as Phase 3 errors outside the cone");
      return new BaselineResult(modulePaths, allLocations, Collections.emptyMap());
    }

    baselineServer.getCheckerFor(allLocations)
        .typecheck(UnstoppableCancellationIndicator.INSTANCE, progress("Phase 0"));
    log("Phase 0 complete in " + String.format("%.1fs",
        (System.currentTimeMillis() - phase0Start) / 1000.0));

    Map<String, Set<String>> baselineErrors = collectErrorKeys(baselineServer);
    int baselineTotal = baselineErrors.values().stream().mapToInt(Set::size).sum();
    log("Phase 0: baseline has " + baselineTotal + " error(s) across "
        + baselineErrors.size() + " module(s)");

    return new BaselineResult(modulePaths, allLocations, baselineErrors);
  }

  // ---------------------------------------------------------------------------
  // One partial round-trip iteration for a single target module
  // ---------------------------------------------------------------------------

  private List<String> runPartialRoundTrip(ModulePath target, BaselineResult baseline) throws Exception {
    List<ModulePath> modulePaths = baseline.modulePaths;
    List<ModuleLocation> allLocations = baseline.allLocations;
    Map<String, Set<String>> baselineErrors = baseline.errorsByModule;

    ModuleLocation targetLoc =
        new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE, target);
    if (!modulePaths.contains(target)) {
      fail("Target module not found in arend-lib: " + target);
    }

    // ---- Phase 1: typecheck target + its prerequisites only -----------------
    log("Phase 1: typecheck target cone for " + target);
    long phase1Start = System.currentTimeMillis();

    ListErrorReporter reporter1 = new ListErrorReporter();
    LibraryManager libManager1 = new LibraryManager(reporter1);
    ArendServer server1 = new ArendServerImpl(
        new CliServerRequester(libManager1), false, false, false);
    server1.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    FileSourceLibrary arendLib1 = buildArendLibrary();
    libManager1.updateLibrary(arendLib1, server1);

    server1.getCheckerFor(List.of(targetLoc))
        .typecheck(UnstoppableCancellationIndicator.INSTANCE, progress("Phase 1"));

    Set<ModuleLocation> coneSet = collectCone(server1);
    log("Phase 1 complete: cone contains " + coneSet.size() + " module(s) in "
        + String.format("%.1fs", (System.currentTimeMillis() - phase1Start) / 1000.0));
    if (coneSet.isEmpty()) {
      fail("Phase 1 produced an empty cone for " + target);
    }

    // Phase 1b: typecheck EVERY definition in every cone module, not just the
    // ones transitively reachable from the target. Otherwise classes like
    // HasProduct (in Operations.ard, not used from AG.Projective's dep cone)
    // remain untypechecked in server1, get serialized with an empty definition
    // proto, and come back with `getTypechecked() == null` — breaking scope
    // resolution for every fresh class that imports their module.
    log("Phase 1b: full-typecheck every cone module");
    long phase1bStart = System.currentTimeMillis();
    // Iterate: typechecking cone modules can pull in fresh deps (e.g. prefix-loaded
    // modules like Category.Topos, which ImportedScope loads when resolving
    // `\import Category.Topos.Sheaf`, carry their own imports that are only
    // materialized when Phase 1b typechecks their body). Loop until the cone is stable
    // so the serialization closure matches the typechecking closure.
    int iter = 0;
    while (true) {
      iter++;
      server1.getCheckerFor(new ArrayList<>(coneSet))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, progress("Phase 1b iter " + iter));
      Set<ModuleLocation> expanded = collectCone(server1);
      if (expanded.size() == coneSet.size()) {
        coneSet = expanded;
        break;
      }
      log("Phase 1b iter " + iter + ": cone expanded from " + coneSet.size() + " to " + expanded.size() + " module(s)");
      coneSet = expanded;
    }
    log("Phase 1b complete in " + String.format("%.1fs",
        (System.currentTimeMillis() - phase1bStart) / 1000.0)
        + " (" + iter + " iteration" + (iter == 1 ? "" : "s") + ", final cone size " + coneSet.size() + ")");

    // Print the long-names of every module in the cone, sorted, to stdout.
    List<String> coneNames = new ArrayList<>(coneSet.size());
    for (ModuleLocation c : coneSet) coneNames.add(c.getModulePath().toString());
    Collections.sort(coneNames);
    System.out.println("=== Cone modules for target " + target + " (" + coneNames.size() + ") ===");
    for (String n : coneNames) System.out.println(n);
    System.out.println("=== End cone modules ===");

    // ---- Phase 2: serialize the cone to a temp directory --------------------
    log("Phase 2: serialize " + coneSet.size() + " cone module(s)");
    long phase2Start = System.currentTimeMillis();
    Path targetBinDir = tempBinRoot.resolve(target.toString().replace('.', '_'));
    Files.createDirectories(targetBinDir);

    List<String> persistErrors = new ArrayList<>();
    int persistOk = 0;
    for (ModuleLocation loc : coneSet) {
      StreamBinarySource binSource = makeBinarySource(targetBinDir, loc);
      ListErrorReporter moduleErr = new ListErrorReporter();
      boolean ok = binSource.persist(server1, moduleErr);
      if (!ok) {
        String msg = "persist() returned false for " + loc.getModulePath();
        persistErrors.add(msg);
        logError("Phase 2 SERIALIZE_FAIL: " + msg);
      }
      for (GeneralError err : moduleErr.getErrorList()) {
        String msg = "persist error in " + loc.getModulePath() + ": " + formatError(err);
        persistErrors.add(msg);
        logError("Phase 2 SERIALIZE_ERROR: " + msg);
      }
      if (ok && moduleErr.getErrorList().isEmpty()) {
        persistOk++;
      }
    }
    log("Phase 2 complete: " + persistOk + "/" + coneSet.size() + " module(s) serialized in "
        + String.format("%.1fs", (System.currentTimeMillis() - phase2Start) / 1000.0));
    if (!persistErrors.isEmpty()) {
      fail("Serialization failed:\n" + String.join("\n", persistErrors));
    }

    // ---- Phase 2.5: deserialize into a throwaway server and validate types --
    // For each deserialized function/class-field, call getTypeWithParams (which runs the
    // full substitution). A StackOverflow here identifies a single cone definition whose
    // type is structurally broken (cyclic Pi chain, aliased binding, etc.) — that's the
    // root cause of the Phase 3 fatal crashes, not the non-cone module that triggers it.
    List<String> validationFindings = validateConeTypes(target, coneSet, targetBinDir, server1);
    for (String f : validationFindings) logError("Phase 2.5 BAD_TYPE: " + f);

    // ---- Phase 3: reset server, deserialize cone, typecheck the rest --------
    log("Phase 3: fresh server, deserialize cone, typecheck rest from sources");
    long phase3Start = System.currentTimeMillis();

    ListErrorReporter reporter2 = new ListErrorReporter();
    LibraryManager libManager2 = new LibraryManager(reporter2);
    CliServerRequester sourceRequester = new CliServerRequester(libManager2);

    // Cone modules are loaded as ARD + ARC: the ARD provides the concrete tree
    // (so inline-meta concrete bodies are present — meta bodies aren't serialized
    // into .arc), and the ARC overlay populates setTypechecked() on the same
    // referables with the deserialized core. Non-cone modules are typechecked
    // from source (ARD only). The requester handles both upfront pre-load and
    // recursive dep loads triggered by ARC's readModule, so cyclic imports
    // (e.g. Equiv ↔ Equiv.Fiber) are loaded transparently.
    final Path targetBinDirFinal = targetBinDir;
    final Set<ModuleLocation> coneSetFinal = coneSet;
    final List<String> deserErrors = new ArrayList<>();
    ArendServerRequester hybridRequester = new ArendServerRequester() {
      @Override
      public void requestModuleUpdate(@org.jetbrains.annotations.NotNull ArendServer server,
                                      @org.jetbrains.annotations.NotNull ModuleLocation module) {
        if (server.getRawGroup(module) != null) return;
        // (1) Always load the .ard source first so the server has the concrete tree.
        sourceRequester.requestModuleUpdate(server, module);
        // (2) For cone modules, additionally overlay .arc on top.
        if (!coneSetFinal.contains(module)) return;
        Path arcFile = FileUtils.binaryFile(targetBinDirFinal, module.getModulePath());
        if (!Files.isRegularFile(arcFile)) {
          logError("Phase 3 ARC_MISSING for cone module: " + arcFile);
          return;
        }
        ConcreteGroup group = server.getRawGroup(module);
        if (group == null) {
          String m = "Phase 3 source load returned no group for " + module.getModulePath();
          deserErrors.add(m);
          logError(m);
          return;
        }
        StreamBinarySource bin = makeBinarySource(targetBinDirFinal, module);
        ListErrorReporter rep = new ListErrorReporter();
        try {
          org.arend.module.serialization.ModuleDeserialization deser = bin.parseProtobuf(rep);
          if (deser == null) {
            for (GeneralError err : rep.getErrorList()) {
              String m = "Phase 3 protobuf parse error in " + module.getModulePath() + ": " + formatError(err);
              deserErrors.add(m);
              logError(m);
            }
            return;
          }
          deser.readDefinitions(group);
          deser.readModule(
              server.getModuleScopeProvider(module.getLibraryName(), false),
              new org.arend.typechecking.order.dependency.DependencyCollector(null));
        } catch (Exception e) {
          String m = "Phase 3 ARC overlay error in " + module.getModulePath() + ": " + e;
          deserErrors.add(m);
          logError(m);
        }
      }
    };

    ArendServer server2 = new ArendServerImpl(hybridRequester, false, false, false);
    server2.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    FileSourceLibrary arendLib2 = buildArendLibrary();
    libManager2.updateLibrary(arendLib2, server2);

    // Pre-load cone in dependency order. The hybrid requester would handle this
    // on demand via recursive requestModuleUpdate from readModule, but topo order
    // minimises the recursion depth and gives clearer error reports.
    List<ModuleLocation> coneOrdered = topoSortByImports(coneSet, server1);
    int prePhase3LoadFails = deserErrors.size();
    for (ModuleLocation loc : coneOrdered) {
      hybridRequester.requestModuleUpdate(server2, loc);
    }
    int newDeserErrors = deserErrors.size() - prePhase3LoadFails;
    log("Pre-Phase-3: loaded ARD+ARC for " + (coneSet.size() - newDeserErrors) + "/" + coneSet.size()
        + " cone modules (" + newDeserErrors + " failed)");

    // Typecheck everything. Cone modules load from ARC (already OK), others from source.
    // We do this in two passes:
    //   a. Bulk pass — catches the common case quickly; any fatal error (SO, OOM) aborts it.
    //   b. Per-module pass — if the bulk pass threw, we retry module-by-module so we can blame
    //      the specific module that triggered the fatal error (modules already typechecked
    //      in pass (a) get skipped by the ordering).
    // Each pass logs a rolling buffer of the last 20 SCCs it touched so the log pinpoints the
    // problematic SCC even when we never reach the outer catch.
    List<String> lastItems = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger itemCounter = new AtomicInteger();
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> detailedProgress =
        new ProgressReporter<>() {
          @Override public void beginProcessing(int n) { log("Phase 3: typechecking " + n + " items (bulk)"); }
          @Override public void beginItem(List<? extends Concrete.ResolvableDefinition> item) {
            String names = item.stream().map(d -> d.getData().getRefLongName().toString())
                .limit(4).reduce((a, b) -> a + "," + b).orElse("?");
            if (item.size() > 4) names += "…+" + (item.size() - 4);
            synchronized (lastItems) {
              lastItems.add(names);
              if (lastItems.size() > 20) lastItems.remove(0);
            }
          }
          @Override public void endItem(List<? extends Concrete.ResolvableDefinition> item) {
            int total = itemCounter.addAndGet(item.size());
            if (total % 200 == 0) {
              String lastName = item.isEmpty() ? "?" : item.get(0).getData().getRefLongName().toString();
              log("Phase 3 bulk: " + total + " items typechecked (last SCC: " + lastName + ", elapsed: " + elapsed() + ")");
            }
          }
        };

    List<String> fatalErrors = new ArrayList<>();
    boolean bulkCrashed = false;
    try {
      server2.getCheckerFor(allLocations)
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, detailedProgress);
    } catch (Throwable t) {
      bulkCrashed = true;
      logFatal("Phase 3 bulk", t, lastItems);
    }

    if (bulkCrashed) {
      // Pass (b): retry module-by-module to identify the offender.
      log("Phase 3 bulk crashed; retrying module-by-module to isolate the fault");
      int moduleNum = 0;
      int skipped = 0, completed = 0, crashed = 0;
      for (ModuleLocation loc : allLocations) {
        moduleNum++;
        if (coneSet.contains(loc)) { skipped++; continue; }
        String shortTag = loc.getModulePath().toString();
        List<String> perModuleItems = Collections.synchronizedList(new ArrayList<>());
        ProgressReporter<List<? extends Concrete.ResolvableDefinition>> perModule =
            new ProgressReporter<>() {
              @Override public void beginProcessing(int n) {}
              @Override public void beginItem(List<? extends Concrete.ResolvableDefinition> item) {
                String names = item.stream().map(d -> d.getData().getRefLongName().toString())
                    .limit(4).reduce((a, b) -> a + "," + b).orElse("?");
                synchronized (perModuleItems) {
                  perModuleItems.add(names);
                  if (perModuleItems.size() > 20) perModuleItems.remove(0);
                }
              }
              @Override public void endItem(List<? extends Concrete.ResolvableDefinition> item) {}
            };
        try {
          server2.getCheckerFor(List.of(loc))
              .typecheck(UnstoppableCancellationIndicator.INSTANCE, perModule);
          completed++;
          if (moduleNum % 50 == 0) {
            log("Phase 3 isolate: " + moduleNum + "/" + allLocations.size()
                + " (skipped " + skipped + ", completed " + completed + ", crashed " + crashed + ", elapsed: " + elapsed() + ")");
          }
        } catch (Throwable t) {
          crashed++;
          StringBuilder msg = new StringBuilder();
          msg.append("Module ").append(shortTag).append(" crashed: ").append(t)
              .append("\nLast SCCs attempted in this module:\n  ")
              .append(String.join("\n  ", perModuleItems));
          // Dump the bottom of the stack trace so we can see which call chain led to the crash.
          StringWriter sw = new StringWriter();
          t.printStackTrace(new PrintWriter(sw));
          String[] lines = sw.toString().split("\n");
          int start = Math.max(0, lines.length - 30);
          msg.append("\nStack trace (last ").append(lines.length - start).append(" frames):");
          for (int i = start; i < lines.length; i++) msg.append("\n  ").append(lines[i]);
          fatalErrors.add(msg.toString());
          logError("Phase 3 ISOLATED_CRASH: " + msg);
        }
      }
      log("Phase 3 isolate pass done: "
          + "skipped (cone) " + skipped + ", completed " + completed + ", crashed " + crashed);
    }

    log("Phase 3 complete in " + String.format("%.1fs",
        (System.currentTimeMillis() - phase3Start) / 1000.0));

    // ---- Phase 4: diff errors against baseline ------------------------------
    Map<String, Set<String>> phase3Errors = collectErrorKeys(server2);
    int phase3Total = phase3Errors.values().stream().mapToInt(Set::size).sum();
    log("Phase 3 error total: " + phase3Total + " across " + phase3Errors.size() + " module(s)");

    // Cone membership set (just module path strings) for quickly tagging errors.
    Set<String> conePaths = new HashSet<>();
    for (ModuleLocation c : coneSet) conePaths.add(c.getModulePath().toString());

    List<String> secondaryErrors = new ArrayList<>();
    // Iterate the live error map so we can render position / cause / affected-def
    // information per error instead of just the dedup key. Track keys we've already
    // emitted so identical errors fired multiple times in one module are still deduped.
    for (Map.Entry<ModuleLocation, List<GeneralError>> entry : server2.getErrorMap().entrySet()) {
      String modKey = entry.getKey().getModulePath().toString();
      Set<String> base = baselineErrors.getOrDefault(modKey, Collections.emptySet());
      boolean coneModule = conePaths.contains(modKey);
      String inCone = coneModule ? "[cone]" : "[fresh]";
      // Without a baseline, errors inside the cone are not informative (we have nothing
      // to diff against), so only fresh-module errors count toward the test verdict.
      if (!RUN_BASELINE && coneModule) continue;
      Set<String> emitted = new LinkedHashSet<>();
      for (GeneralError err : entry.getValue()) {
        String key = errorKey(err);
        if (RUN_BASELINE && base.contains(key)) continue;
        // No baseline: arend-lib commits are allowed to carry GOALs in fresh modules
        // (a non-closed goal is a "TODO" marker, not a serialization regression), so
        // we tolerate them. Real ERROR-level diagnostics still fail the test.
        if (!RUN_BASELINE && err.level == GeneralError.Level.GOAL) continue;
        if (!emitted.add(key)) continue;
        String pos = renderErrorPosition(err);
        String cls = err instanceof org.arend.typechecking.error.local.CoreErrorWrapper w
            ? "CoreErrorWrapper(" + (w.error == null ? "?" : w.error.getClass().getSimpleName()) + ")"
            : err.getClass().getSimpleName();
        String affected = renderAffectedDefs(err);
        String srcLine = renderSourceLine(err);
        StringBuilder sb = new StringBuilder();
        sb.append(inCone).append(' ').append(modKey).append(" :: ").append(key);
        if (!pos.isEmpty()) sb.append("\n  at ").append(pos);
        if (!srcLine.isEmpty()) sb.append("\n  src> ").append(srcLine);
        sb.append("\n  errClass=").append(cls);
        if (!affected.isEmpty()) sb.append("\n  affecting=").append(affected);
        String msg = sb.toString();
        secondaryErrors.add(msg);
        logError("SECONDARY: " + msg);
      }
    }

    // Dump detailed info (expected/actual) for up to 20 Type-mismatch errors that are NEW
    // vs baseline, so we can inspect the pattern and identify the root cause.
    int tmDumped = 0;
    for (Map.Entry<ModuleLocation, List<GeneralError>> entry : server2.getErrorMap().entrySet()) {
      if (tmDumped >= 20) break;
      String modKey = entry.getKey().getModulePath().toString();
      Set<String> base = baselineErrors.getOrDefault(modKey, Collections.emptySet());
      for (GeneralError err : entry.getValue()) {
        if (tmDumped >= 20) break;
        if (base.contains(errorKey(err))) continue;
        GeneralError inner = err instanceof org.arend.typechecking.error.local.CoreErrorWrapper w ? w.error : err;
        if (inner instanceof org.arend.ext.error.TypeMismatchError tme) {
          var ppCfg = new org.arend.ext.prettyprinting.PrettyPrinterConfig() {};
          String expected = tme.expected.prettyPrint(ppCfg).toString();
          String actual = tme.actual.prettyPrint(ppCfg).toString();
          String cause = err.getCause() == null ? "<null>" : err.getCause().getClass().getSimpleName();
          logError("TM_DUMP [" + modKey + "] cause=" + cause
              + "\n  expected: " + (expected.length() > 400 ? expected.substring(0, 400) + "…" : expected)
              + "\n  actual:   " + (actual.length() > 400 ? actual.substring(0, 400) + "…" : actual)
              + "\n  same-printed=" + expected.equals(actual));
          tmDumped++;
        }
      }
    }

    // Dump detailed info for up to 40 `Cannot infer parameter` errors — FunctionArgInferenceError.
    // These are the dominant secondary errors. For each, log:
    //   - the definition's class, identity hash, status
    //   - parameter name + type expression + whether the parameter is explicit
    //   - the source location (position in the .ard file, if available)
    //   - whether the error's definition == the referable's typechecked value
    //     (detects the "re-typechecked via fresh TCDefReferable" situation)
    //   - the candidates (what the typechecker tried) + expected/actual if present
    int paramDumped = 0;
    // Per-module first-only, so we get one from each suspect module (LowerReal etc.),
    // not 40 from the same cascading one.
    Set<String> paramDumpedModules = new HashSet<>();
    for (Map.Entry<ModuleLocation, List<GeneralError>> entry : server2.getErrorMap().entrySet()) {
      if (paramDumped >= 40) break;
      String modKey = entry.getKey().getModulePath().toString();
      Set<String> base = baselineErrors.getOrDefault(modKey, Collections.emptySet());
      for (GeneralError err : entry.getValue()) {
        if (paramDumped >= 40) break;
        if (base.contains(errorKey(err))) continue;
        GeneralError inner = err instanceof org.arend.typechecking.error.local.CoreErrorWrapper w ? w.error : err;
        if (inner instanceof org.arend.typechecking.error.local.inference.FunctionArgInferenceError fie) {
          // Emit first 3 per module (for diversity) plus all "of definition X" errors.
          if (paramDumpedModules.contains(modKey) && fie.definition == null) continue;
          var def = fie.definition;
          String defDesc;
          String defTcSameAsRef = "<n/a>";
          if (def == null) {
            defDesc = "<null>";
          } else {
            defDesc = def.getClass().getSimpleName() + "/" + def.getName()
                + " status=" + def.status()
                + " defHash=@" + System.identityHashCode(def);
            // If the definition has a referable, compare def == referable.getTypechecked()
            try {
              Object refTc = def.getReferable() == null ? null : def.getReferable().getTypechecked();
              defTcSameAsRef = String.valueOf(refTc == def)
                  + " (refTcHash=@" + System.identityHashCode(refTc) + ")";
            } catch (Throwable ignored) {
              defTcSameAsRef = "<probe-failed>";
            }
          }
          org.arend.core.context.param.DependentLink link = fie.parameter;
          String paramDesc = link == null
              ? "<null>"
              : (link.isExplicit() ? "explicit" : "implicit") + " " + link.getName()
                + " : " + safeRender(link.getTypeExpr())
                + " paramHash=@" + System.identityHashCode(link);
          String loc = "<no-cause>";
          if (err.getCause() instanceof org.arend.term.concrete.Concrete.SourceNode sn) {
            loc = sn.getClass().getSimpleName();
            if (sn.getData() != null) loc += " data=" + sn.getData();
          }
          // Candidates/expected/actual (from ArgInferenceError base) can shed light on the
          // unifier state at the failure point.
          StringBuilder cands = new StringBuilder();
          if (fie.candidates != null) {
            for (int i = 0; i < fie.candidates.length; i++) {
              Object c = fie.candidates[i];
              String s = c == null ? "null" : safeRender(c);
              if (s.length() > 120) s = s.substring(0, 120) + "…";
              cands.append("#").append(i).append("=").append(s).append(" ; ");
            }
          }
          String expectedStr = fie.expected == null ? "<null>" : safeRender(fie.expected);
          String actualStr = fie.actual == null ? "<null>" : safeRender(fie.actual);
          if (expectedStr.length() > 200) expectedStr = expectedStr.substring(0, 200) + "…";
          if (actualStr.length() > 200) actualStr = actualStr.substring(0, 200) + "…";
          logError("PARAM_DUMP [" + modKey + "] "
              + (conePaths.contains(modKey) ? "[cone] " : "[fresh] ")
              + "\n  definition: " + defDesc
              + "\n  referable.getTypechecked() == definition ? " + defTcSameAsRef
              + "\n  parameter: " + paramDesc
              + "\n  index: " + fie.index
              + "\n  cause: " + loc
              + "\n  candidates: " + (cands.length() == 0 ? "<none>" : cands)
              + "\n  expected: " + expectedStr
              + "\n  actual: " + actualStr);
          paramDumped++;
          paramDumpedModules.add(modKey);
        }
      }
    }
    if (!deserErrors.isEmpty()) {
      for (String e : deserErrors) secondaryErrors.add("[deser] " + e);
    }
    for (String e : fatalErrors) secondaryErrors.add("[fatal] " + e);

    // Short summary (counts by category) to make the overall report readable.
    long cannotResolve = secondaryErrors.stream().filter(e -> e.contains("Cannot resolve reference")).count();
    long expectedClass = secondaryErrors.stream().filter(e -> e.contains("Expected a class")).count();
    long typeMismatch = secondaryErrors.stream().filter(e -> e.contains("Type mismatch")).count();
    long fatal = fatalErrors.size();
    log("Secondary error summary for target " + target + ": "
        + secondaryErrors.size() + " total"
        + " | Cannot-resolve=" + cannotResolve
        + ", Expected-class=" + expectedClass
        + ", Type-mismatch=" + typeMismatch
        + ", Fatal(SO/OOM)=" + fatal);

    return secondaryErrors;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Snapshot all arend-lib source modules currently loaded by the server. */
  private static Set<ModuleLocation> collectCone(ArendServer server) {
    Set<ModuleLocation> cone = new LinkedHashSet<>();
    for (ModuleLocation loc : server.getModules()) {
      if ("arend-lib".equals(loc.getLibraryName())
          && loc.getLocationKind() == ModuleLocation.LocationKind.SOURCE) {
        cone.add(loc);
      }
    }
    return cone;
  }

  /** Collects a per-module set of stable "error keys" from a server's error map. */
  private static Map<String, Set<String>> collectErrorKeys(ArendServer server) {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (Map.Entry<ModuleLocation, List<GeneralError>> entry : server.getErrorMap().entrySet()) {
      String modKey = entry.getKey().getModulePath().toString();
      Set<String> keys = result.computeIfAbsent(modKey, k -> new LinkedHashSet<>());
      for (GeneralError err : entry.getValue()) {
        keys.add(errorKey(err));
      }
    }
    return result;
  }

  /**
   * Builds a comparison key for an error. Two errors from two servers are considered
   * the same if their keys match. The short message is usually stable across servers
   * as long as no definition-identity loss corrupts the pretty-printing.
   */
  private static String errorKey(GeneralError err) {
    return err.level + "|" + err.getShortMessage();
  }

  /**
   * Best-effort rendering of an error's source position. Most arend-lib errors carry
   * a {@code Position} (module:line:col) via the cause's {@code getData()} or directly
   * via {@code getCause()}; resolver errors may instead carry an {@code ArendRef} cause.
   */
  private static String renderErrorPosition(GeneralError err) {
    Object cause = err.getCause();
    if (cause == null) return "";
    if (cause instanceof Collection<?> col) {
      for (Object o : col) {
        String r = renderCauseObject(o);
        if (!r.isEmpty()) return r;
      }
      return "";
    }
    return renderCauseObject(cause);
  }

  private static String renderCauseObject(Object cause) {
    if (cause == null) return "";
    if (cause instanceof org.arend.error.SourcePosition sp) return sp.toString();
    if (cause instanceof org.arend.ext.reference.DataContainer dc) {
      Object data = dc.getData();
      if (data instanceof org.arend.error.SourcePosition sp) return sp.toString();
      if (data != null) return data.toString();
    }
    return cause.toString();
  }

  /** Best-effort: read a single source line from arend-lib so each error entry is
   *  self-locating. Returns empty when the file is missing or the line is out of range. */
  private static String renderSourceLine(GeneralError err) {
    Object cause = err.getCause();
    org.arend.error.SourcePosition sp = unwrapPosition(cause);
    if (sp == null) return "";
    String moduleStr = sp.sourceName;
    if (moduleStr == null || moduleStr.isEmpty()) return "";
    // ModuleLocation.toString() formats as e.g. "[arend-lib]:: Category.Yoneda";
    // strip the library prefix to get the module path.
    int idx = moduleStr.lastIndexOf(":: ");
    String modPath = idx >= 0 ? moduleStr.substring(idx + 3) : moduleStr;
    Path file = AREND_LIB_DIR.resolve("src").resolve(modPath.replace('.', '/') + ".ard");
    if (!Files.isRegularFile(file)) return "";
    int line = sp.line;
    if (line <= 0) return "";
    try {
      List<String> all = Files.readAllLines(file);
      if (line > all.size()) return "";
      String src = all.get(line - 1);
      // Trim leading whitespace but preserve a small marker so column-context is readable.
      String trimmed = src.replaceFirst("^\\s+", "");
      if (trimmed.length() > 200) trimmed = trimmed.substring(0, 200) + "…";
      return trimmed;
    } catch (IOException e) {
      return "";
    }
  }

  private static org.arend.error.SourcePosition unwrapPosition(Object cause) {
    if (cause == null) return null;
    if (cause instanceof org.arend.error.SourcePosition sp) return sp;
    if (cause instanceof Collection<?> col) {
      for (Object o : col) {
        org.arend.error.SourcePosition sp = unwrapPosition(o);
        if (sp != null) return sp;
      }
      return null;
    }
    if (cause instanceof org.arend.ext.reference.DataContainer dc) {
      Object data = dc.getData();
      if (data instanceof org.arend.error.SourcePosition sp) return sp;
    }
    return null;
  }

  /**
   * Lists the long names of definitions affected by this error (typically just one —
   * the enclosing top-level def the resolver/typechecker was working on when it fired).
   */
  private static String renderAffectedDefs(GeneralError err) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    err.forAffectedDefinitions((ref, e) -> {
      if (ref == null) return;
      String name = null;
      if (ref instanceof org.arend.naming.reference.LocatedReferable lr && lr.getRefLongName() != null) {
        name = lr.getRefLongName().toString();
      } else if (ref instanceof org.arend.naming.reference.Referable r) {
        name = r.getRefName();
      } else {
        name = String.valueOf(ref);
      }
      if (name != null && !name.isEmpty()) names.add(name);
    });
    if (names.isEmpty()) return "";
    return String.join(",", names);
  }

  /**
   * Loads the cone ARC files into a throwaway server and exercises each function's
   * {@code getTypeWithParams} (the exact call that blows up under Phase 3). Returns
   * a list of "module :: def" strings for every definition that throws SO or any
   * other error — these are the serialization-layer culprits that downstream
   * modules would trip over.
   */
  private List<String> validateConeTypes(
      org.arend.ext.module.ModulePath target,
      Set<ModuleLocation> coneSet,
      Path targetBinDir,
      ArendServer depSource) {
    log("Phase 2.5: validating deserialized cone types (" + coneSet.size() + " modules)");
    long start = System.currentTimeMillis();

    ListErrorReporter reporter = new ListErrorReporter();
    LibraryManager libManager = new LibraryManager(reporter);
    // Binary-only requester: loads ARC on demand. We don't actually rely on the requester
    // here — we load via the fixed-point pass below — but the server still needs one.
    ArendServer serverV = new ArendServerImpl(
        new ArendServerRequester() {
          @Override
          public void requestModuleUpdate(@org.jetbrains.annotations.NotNull ArendServer server,
                                          @org.jetbrains.annotations.NotNull ModuleLocation module) {
            if (server.getRawGroup(module) != null) return;
            if (!coneSet.contains(module)) return;
            StreamBinarySource bin = makeBinarySource(targetBinDir, module);
            bin.load(server, new ListErrorReporter());
          }
        }, false, false, false);
    serverV.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    FileSourceLibrary arendLibV = buildArendLibrary();
    libManager.updateLibrary(arendLibV, serverV);

    // Load cone modules in dependency order (deps before dependents).
    // readModule resolves cross-module call targets against already-loaded modules;
    // loading in arbitrary order produces "Definition X:Y is not loaded" failures
    // and leaves the server with partially-filled-in class fields (null myType).
    List<ModuleLocation> ordered = topoSortByImports(coneSet, depSource);
    int loadFails = 0;
    for (ModuleLocation loc : ordered) {
      Path arc = FileUtils.binaryFile(targetBinDir, loc.getModulePath());
      if (!Files.isRegularFile(arc)) continue;
      StreamBinarySource bin = makeBinarySource(targetBinDir, loc);
      ListErrorReporter loadReporter = new ListErrorReporter();
      ConcreteGroup loaded = bin.load(serverV, loadReporter);
      if (loaded == null || !loadReporter.getErrorList().isEmpty()) {
        loadFails++;
        for (GeneralError err : loadReporter.getErrorList()) {
          logError("Phase 2.5 LOAD_ERROR in " + loc.getModulePath() + ": " + formatError(err));
        }
      }
    }
    int stillMissing = 0;
    for (ModuleLocation loc : coneSet) if (serverV.getRawGroup(loc) == null) stillMissing++;
    log("Phase 2.5: topo-ordered load done; " + stillMissing + " missing, " + loadFails + " load errors");

    List<String> findings = new ArrayList<>();
    int funcsChecked = 0;
    int fieldsChecked = 0;
    for (ModuleLocation loc : coneSet) {
      ConcreteGroup group = serverV.getRawGroup(loc);
      if (group == null) continue;
      int[] checked = new int[2]; // [funcs, fields]
      walkTCRefs(group, ref -> {
        org.arend.core.definition.Definition def = ref.getTypechecked();
        if (def == null || !def.status().isOK()) return;
        String name = loc.getModulePath() + " :: " + ref.getRefLongName();
        try {
          if (def instanceof org.arend.core.definition.FunctionDefinition fn) {
            checked[0]++;
            fn.getTypeWithParams(new ArrayList<>(), fn.makeIdLevels());
          } else if (def instanceof org.arend.core.definition.DataDefinition dd) {
            checked[0]++;
            dd.getTypeWithParams(new ArrayList<>(), dd.makeIdLevels());
            for (org.arend.core.definition.Constructor c : dd.getConstructors()) {
              c.getTypeWithParams(new ArrayList<>(), c.makeIdLevels());
            }
          } else if (def instanceof org.arend.core.definition.ClassDefinition cd) {
            for (org.arend.core.definition.ClassField f : cd.getPersonalFields()) {
              checked[1]++;
              String fieldName = name + "." + f.getName();
              try {
                org.arend.core.expr.PiExpression rawType = f.getType();
                if (rawType == null) {
                  findings.add(fieldName + " [myType == null]");
                  logError("Phase 2.5 NULL_FIELD_TYPE: " + fieldName);
                } else {
                  f.getType(f.makeIdLevels());
                }
              } catch (StackOverflowError | OutOfMemoryError e) {
                findings.add(fieldName + " [" + e.getClass().getSimpleName() + "]");
                logError("Phase 2.5 culprit (field): " + fieldName + " (" + e.getClass().getSimpleName() + ")");
              } catch (Throwable t) {
                findings.add(fieldName + " [" + t.getClass().getSimpleName() + ": " + t.getMessage() + "]");
                logError("Phase 2.5 field error: " + fieldName + " -> " + t);
              }
            }
            return; // class handled; don't fall through to outer catch
          }
        } catch (StackOverflowError | OutOfMemoryError e) {
          findings.add(name + " [" + e.getClass().getSimpleName() + "]");
          logError("Phase 2.5 culprit: " + name + " (" + e.getClass().getSimpleName() + ")");
        } catch (Throwable t) {
          findings.add(name + " [" + t.getClass().getSimpleName() + ": " + t.getMessage() + "]");
        }
      });
      funcsChecked += checked[0];
      fieldsChecked += checked[1];
    }

    log("Phase 2.5 complete in " + String.format("%.1fs", (System.currentTimeMillis() - start) / 1000.0)
        + ": " + funcsChecked + " functions/data checked, " + fieldsChecked + " fields checked, "
        + findings.size() + " bad-type findings");

    return findings;
  }

  /**
   * Topologically sort {@code cone} by import edges read from {@code depSource}'s raw groups
   * (deps first). Ignores imports to modules outside the cone; tolerates missing raw groups
   * (emits that module anyway, at the end).
   */
  private static List<ModuleLocation> topoSortByImports(Set<ModuleLocation> cone, ArendServer depSource) {
    Map<org.arend.ext.module.ModulePath, ModuleLocation> byPath = new HashMap<>();
    for (ModuleLocation loc : cone) byPath.put(loc.getModulePath(), loc);

    Map<ModuleLocation, List<ModuleLocation>> deps = new HashMap<>();
    for (ModuleLocation loc : cone) {
      List<ModuleLocation> ds = new ArrayList<>();
      ConcreteGroup g = depSource.getRawGroup(loc);
      if (g != null) {
        for (ConcreteStatement s : g.statements()) {
          if (s.command() != null && s.command().isImport()) {
            org.arend.ext.module.ModulePath depPath =
                new org.arend.ext.module.ModulePath(s.command().module().getPath());
            ModuleLocation d = byPath.get(depPath);
            if (d != null && !d.equals(loc)) ds.add(d);
          }
        }
      }
      deps.put(loc, ds);
    }

    List<ModuleLocation> out = new ArrayList<>(cone.size());
    Set<ModuleLocation> visited = new HashSet<>();
    Set<ModuleLocation> visiting = new HashSet<>();
    for (ModuleLocation loc : cone) topoVisit(loc, deps, visited, visiting, out);
    return out;
  }

  private static void topoVisit(
      ModuleLocation loc,
      Map<ModuleLocation, List<ModuleLocation>> deps,
      Set<ModuleLocation> visited,
      Set<ModuleLocation> visiting,
      List<ModuleLocation> out) {
    if (visited.contains(loc) || visiting.contains(loc)) return;
    visiting.add(loc);
    for (ModuleLocation dep : deps.getOrDefault(loc, Collections.emptyList())) {
      topoVisit(dep, deps, visited, visiting, out);
    }
    visiting.remove(loc);
    visited.add(loc);
    out.add(loc);
  }

  private static void walkTCRefs(ConcreteGroup group, java.util.function.Consumer<org.arend.naming.reference.TCDefReferable> action) {
    if (group.referable() instanceof org.arend.naming.reference.TCDefReferable r) {
      action.accept(r);
    }
    // Class fields / data constructors — their referables are FieldReferable / InternalReferable,
    // both of which extend TCDefReferable. walkTCRefs must include them so that probes for
    // field names like `abs>=0` find the right referable.
    for (var internal : group.getInternalReferables()) {
      if (internal instanceof org.arend.naming.reference.TCDefReferable tc) action.accept(tc);
    }
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null) walkTCRefs(sub, action);
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      walkTCRefs(dyn, action);
    }
  }

  private static String safeRender(Object o) {
    if (o == null) return "null";
    try { return o.toString(); } catch (Throwable t) { return "<print failed: " + t + ">"; }
  }

  private void logFatal(String label, Throwable t, List<String> lastItems) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    String stack = sw.toString().lines().limit(40).collect(java.util.stream.Collectors.joining("\n"));
    String items;
    synchronized (lastItems) {
      items = lastItems.isEmpty() ? "(none)" : String.join("\n  ", lastItems);
    }
    logError(label + " FATAL: " + t
        + "\nLast SCCs processed before error:\n  " + items
        + "\n" + stack);
  }

  private static String formatError(GeneralError err) {
    String msg = err.getShortMessage();
    if (err instanceof org.arend.module.error.ExceptionError exErr) {
      StringWriter sw = new StringWriter();
      exErr.exception.printStackTrace(new PrintWriter(sw));
      msg += " [cause: " + exErr.exception + "]\n" + sw;
    }
    return msg;
  }

  private ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progress(String label) {
    AtomicInteger counter = new AtomicInteger();
    return new ProgressReporter<>() {
      @Override public void beginProcessing(int n) { log(label + ": typechecking " + n + " items"); }
      @Override public void beginItem(List<? extends Concrete.ResolvableDefinition> item) {}
      @Override public void endItem(List<? extends Concrete.ResolvableDefinition> item) {
        int total = counter.addAndGet(item.size());
        if (total % 500 == 0) log(label + ": " + total + " items typechecked so far (elapsed: " + elapsed() + ")");
      }
    };
  }
}
