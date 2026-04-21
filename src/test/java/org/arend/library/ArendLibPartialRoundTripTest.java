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
 * {@code AG.Projective}.
 *
 * <p>The test is skipped automatically when {@code arend-lib/src} is absent.
 */
public class ArendLibPartialRoundTripTest {

  private static final Path AREND_LIB_DIR = Paths.get("arend-lib");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private static final String TARGETS_PROPERTY = "arend.partial_roundtrip.targets";
  private static final String DEFAULT_TARGET = "AG.Projective";

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

  // ---------------------------------------------------------------------------
  // Main test  (timeout = 30 minutes)
  // ---------------------------------------------------------------------------

  @Test(timeout = 1_800_000)
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
      return List.of(ModulePath.fromString(DEFAULT_TARGET));
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
    log("Phase 0: baseline full typecheck from sources");
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

    // Collect the set of modules that actually got loaded into server1 — this is the cone.
    Set<ModuleLocation> coneSet = new LinkedHashSet<>();
    for (ModuleLocation loc : server1.getModules()) {
      if ("arend-lib".equals(loc.getLibraryName())
          && loc.getLocationKind() == ModuleLocation.LocationKind.SOURCE) {
        coneSet.add(loc);
      }
    }
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
    server1.getCheckerFor(new ArrayList<>(coneSet))
        .typecheck(UnstoppableCancellationIndicator.INSTANCE, progress("Phase 1b"));
    log("Phase 1b complete in " + String.format("%.1fs",
        (System.currentTimeMillis() - phase1bStart) / 1000.0));

    // Probe: does server1 have Operations' HasProduct typechecked?
    {
      ModuleLocation opLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Operations"));
      ConcreteGroup mg = server1.getRawGroup(opLoc);
      if (mg != null) {
        StringBuilder sb = new StringBuilder();
        for (ConcreteStatement s : mg.statements()) {
          ConcreteGroup sg = s.group();
          if (sg == null) continue;
          String tcT = "<no-tc>";
          if (sg.referable() instanceof org.arend.naming.reference.TCDefReferable tc2) {
            Object d = tc2.getTypechecked();
            tcT = d == null ? "<null>" : d.getClass().getSimpleName() + "/status=" + ((org.arend.core.definition.Definition) d).status();
          }
          sb.append(sg.referable().getRefName()).append("(").append(tcT).append("),");
        }
        log("server1 Operations subgroups after Phase 1: [" + sb + "]");
      }
    }

    // ---- Phase 2: serialize the cone to a temp directory --------------------
    log("Phase 2: serialize " + coneSet.size() + " cone module(s)");
    long phase2Start = System.currentTimeMillis();
    Path targetBinDir = tempBinRoot.resolve(target.toString().replace('.', '_'));
    Files.createDirectories(targetBinDir);

    List<String> persistErrors = new ArrayList<>();
    int persistOk = 0;
    for (ModuleLocation loc : coneSet) {
      FileBinarySource binSource = new FileBinarySource(targetBinDir, loc);
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

    // Hybrid requester: loads cone modules from ARC, others from source (via CliServerRequester).
    final Path targetBinDirFinal = targetBinDir;
    final List<String> deserErrors = new ArrayList<>();
    ArendServerRequester hybridRequester = new ArendServerRequester() {
      @Override
      public void requestModuleUpdate(@org.jetbrains.annotations.NotNull ArendServer server,
                                      @org.jetbrains.annotations.NotNull ModuleLocation module) {
        if (server.getRawGroup(module) != null) return;

        if (coneSet.contains(module)) {
          Path arcFile = FileUtils.binaryFile(targetBinDirFinal, module.getModulePath());
          if (!Files.isRegularFile(arcFile)) {
            logError("Phase 3 ARC_MISSING for cone module: " + arcFile);
            return;
          }
          FileBinarySource binSource = new FileBinarySource(targetBinDirFinal, module);
          ListErrorReporter reqErr = new ListErrorReporter();
          ConcreteGroup loaded = binSource.load(server, reqErr);
          if (loaded == null || !reqErr.getErrorList().isEmpty()) {
            for (GeneralError err : reqErr.getErrorList()) {
              String msg = "deserialize error in " + module.getModulePath() + ": " + formatError(err);
              deserErrors.add(msg);
              logError("Phase 3 DESERIALIZE_ERROR: " + msg);
            }
            if (loaded == null && reqErr.getErrorList().isEmpty()) {
              String msg = "deserialize returned null for " + module.getModulePath();
              deserErrors.add(msg);
              logError("Phase 3 DESERIALIZE_NULL: " + msg);
            }
          }
        } else {
          sourceRequester.requestModuleUpdate(server, module);
        }
      }
    };

    ArendServer server2 = new ArendServerImpl(hybridRequester, false, false, false);
    server2.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    FileSourceLibrary arendLib2 = buildArendLibrary();
    libManager2.updateLibrary(arendLib2, server2);

    // Pre-load cone in dependency order so that readModule's cross-module call target
    // lookups find already-typechecked classes/fields. Without this, loading a module
    // before its deps leaves its class fields with null types, which then cascades
    // during downstream typechecking as NPEs or scope-resolution failures.
    List<ModuleLocation> coneOrdered = topoSortByImports(coneSet, server1);
    int prePhase3LoadFails = 0;
    for (ModuleLocation loc : coneOrdered) {
      if (server2.getRawGroup(loc) != null) continue;
      FileBinarySource bin = new FileBinarySource(targetBinDir, loc);
      ListErrorReporter rep = new ListErrorReporter();
      ConcreteGroup g = bin.load(server2, rep);
      if (g == null || !rep.getErrorList().isEmpty()) {
        prePhase3LoadFails++;
        for (GeneralError err : rep.getErrorList()) {
          String m = "pre-Phase-3 load error in " + loc.getModulePath() + ": " + formatError(err);
          deserErrors.add(m);
          logError(m);
        }
      }
    }
    log("Pre-Phase-3: loaded " + (coneSet.size() - prePhase3LoadFails) + "/" + coneSet.size()
        + " cone modules (" + prePhase3LoadFails + " failed)");

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
          if (t instanceof org.arend.core.subst.SubstVisitor.SubstDepthExceeded sde) {
            msg.append("\nCycling PiExpression: ").append(describePi(sde.cyclingPi));
          }
          if (t instanceof org.arend.typechecking.instance.pool.GlobalInstancePool.InstanceDepthExceeded ide) {
            msg.append("\nInstance-search cycle on class: ")
                .append(ide.searchClass == null ? "<null>" : ide.searchClass.getName())
                .append(" classifying=").append(ide.classifyingExpression)
                .append(ide.chainInfo);
          }
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
    log("InstanceCache debug: source-path adds=" + org.arend.server.impl.InstanceCacheImpl.debugAddedSource
        + ", deserialized-fallback adds=" + org.arend.server.impl.InstanceCacheImpl.debugAddedDeserialized);
    // Duplicate-import probe: for Arith.Real.Field, resolve \import Order.PartialOrder
    // and \import Algebra.Group and compare the Preorder.op referable returned by each.
    log("Starting duplicate-import probe...");
    try {
      ModuleLocation srfLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Arith.Real.Field"));
      ConcreteGroup srfGroup = server2.getRawGroup(srfLoc);
      log("Duplicate-import probe: srfGroup=" + (srfGroup != null));
      if (srfGroup != null) {
        org.arend.naming.scope.Scope moduleScope = org.arend.naming.scope.LexicalScope.opened(srfGroup);
        // find its \import statements
        org.arend.term.group.ConcreteNamespaceCommand orderCmd = null, groupCmd = null;
        for (ConcreteStatement s : srfGroup.statements()) {
          var cmd = s.command();
          if (cmd == null || !cmd.isImport()) continue;
          String path = String.join(".", cmd.module().getPath());
          if (path.equals("Order.PartialOrder")) orderCmd = cmd;
          if (path.equals("Algebra.Group")) groupCmd = cmd;
        }
        log("Duplicate-import probe cmds: orderCmd=" + (orderCmd != null) + " groupCmd=" + (groupCmd != null));
        if (orderCmd != null && groupCmd != null) {
          // Directly resolve each module's scope via server's ModuleScopeProvider.
          var msp = server2.getModuleScopeProvider("arend-lib", false);
          org.arend.naming.scope.Scope orderScope = msp.forModule(org.arend.ext.module.ModulePath.fromString("Order.PartialOrder"));
          org.arend.naming.scope.Scope groupScope = msp.forModule(org.arend.ext.module.ModulePath.fromString("Algebra.Group"));
          log("Duplicate-import probe scopes: orderScope=" + (orderScope != null) + " groupScope=" + (groupScope != null));
          if (orderScope != null && groupScope != null) {
            // List ALL DYNAMIC-context elements named "op" from each scope, to see if each scope
            // exposes Preorder.op (or another class's op) as distinct Java objects.
            StringBuilder orderOps = new StringBuilder();
            for (var ref : orderScope.getElements(org.arend.naming.scope.Scope.ScopeContext.DYNAMIC)) {
              if ("op".equals(ref.getRefName())) {
                orderOps.append(ref.getClass().getSimpleName()).append("@")
                  .append(System.identityHashCode(ref))
                  .append(" parent=").append(ref instanceof org.arend.naming.reference.LocatedReferable lr ? lr.getLocatedReferableParent() : "?")
                  .append("; ");
              }
            }
            StringBuilder groupOps = new StringBuilder();
            for (var ref : groupScope.getElements(org.arend.naming.scope.Scope.ScopeContext.DYNAMIC)) {
              if ("op".equals(ref.getRefName())) {
                groupOps.append(ref.getClass().getSimpleName()).append("@")
                  .append(System.identityHashCode(ref))
                  .append(" parent=").append(ref instanceof org.arend.naming.reference.LocatedReferable lr ? lr.getLocatedReferableParent() : "?")
                  .append("; ");
              }
            }
            log("Duplicate-import probe Arith.Real.Field: ALL 'op' elements:\n"
                + "  from Order.PartialOrder: " + orderOps + "\n"
                + "  from Algebra.Group:       " + groupOps);
          }
        }
      }
    } catch (Throwable t) {
      log("Duplicate-import probe failed: " + t);
    }
    // Probe DOF's DynamicScopeProvider via server2 (which has typingInfo populated after Phase 3).
    {
      ModuleLocation soLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Algebra.StrictlyOrdered"));
      ConcreteGroup soGroup = server2.getRawGroup(soLoc);
      if (soGroup != null) {
        org.arend.core.definition.ClassDefinition dof = null;
        for (ConcreteStatement s : soGroup.statements()) {
          ConcreteGroup sg = s.group();
          if (sg != null && "DiscreteOrderedField".equals(sg.referable().getRefName())
              && sg.referable() instanceof org.arend.naming.reference.TCDefReferable tc
              && tc.getTypechecked() instanceof org.arend.core.definition.ClassDefinition cd) {
            dof = cd; break;
          }
        }
        if (dof != null) {
          var ti = server2.getTypingInfo();
          var provider = ti.getDynamicScopeProvider(dof.getReferable());
          if (provider == null) {
            log("Phase 3 DOF provider probe: server2 has NO DynamicScopeProvider for DOF");
          } else {
            int finvCount = 0;
            for (var gr : provider.getDynamicContent()) {
              if ("finv>0".equals(gr.getRefName())) finvCount++;
            }
            log("Phase 3 DOF provider probe: dynamicContent.size=" + provider.getDynamicContent().size()
                + " finv>0 present=" + finvCount);
          }
        }
      }
    }
    // Dump up to 10 match-failure entries for the debug class (set via -Darend.instance.debugMatchClass)
    var matchLog = org.arend.typechecking.instance.pool.GlobalInstancePool.MATCH_DEBUG_LOG;
    synchronized (matchLog) {
      log("InstanceCache match-fail log: " + matchLog.size() + " failures");
      for (int i = 0; i < Math.min(10, matchLog.size()); i++) log("  " + matchLog.get(i));
    }

    // ---- Phase 4: diff errors against baseline ------------------------------
    Map<String, Set<String>> phase3Errors = collectErrorKeys(server2);
    int phase3Total = phase3Errors.values().stream().mapToInt(Set::size).sum();
    log("Phase 3 error total: " + phase3Total + " across " + phase3Errors.size() + " module(s)");

    List<String> secondaryErrors = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : phase3Errors.entrySet()) {
      Set<String> base = baselineErrors.getOrDefault(entry.getKey(), Collections.emptySet());
      for (String err : entry.getValue()) {
        if (!base.contains(err)) {
          String msg = entry.getKey() + " :: " + err;
          secondaryErrors.add(msg);
          logError("SECONDARY: " + msg);
        }
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
            FileBinarySource bin = new FileBinarySource(targetBinDir, module);
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
      FileBinarySource bin = new FileBinarySource(targetBinDir, loc);
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

    // Probe: does the TypingInfo's DynamicScopeProvider for DOF expose finv>0?
    {
      org.arend.core.definition.ClassDefinition dof = findClassInServer(serverV, "Algebra.StrictlyOrdered.DiscreteOrderedField");
      if (dof != null) {
        var typingInfo = serverV.getTypingInfo();
        var provider = typingInfo.getDynamicScopeProvider(dof.getReferable());
        if (provider != null) {
          int finvCount = 0;
          StringBuilder names = new StringBuilder();
          for (org.arend.naming.reference.GlobalReferable gr : provider.getDynamicContent()) {
            if ("finv>0".equals(gr.getRefName())) finvCount++;
            if (names.length() < 200) names.append(gr.getRefName()).append(",");
          }
          log("Phase 2.5 DOF dynamic scope probe: provider="
              + (provider == null ? "<null>" : "present")
              + " dynamicContent.size=" + provider.getDynamicContent().size()
              + " finvCount=" + finvCount + " sample=[" + names + "]");
        } else {
          log("Phase 2.5 DOF dynamic scope probe: no DynamicScopeProvider registered!");
        }
      }
    }

    // Probe DiscreteOrderedField's dynamic subgroups — error says `finv>0` can't be found there.
    {
      ModuleLocation loc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Algebra.StrictlyOrdered"));
      ConcreteGroup mg = serverV.getRawGroup(loc);
      if (mg != null) {
        ConcreteGroup dofGroup = null;
        for (ConcreteStatement s : mg.statements()) {
          ConcreteGroup sg = s.group();
          if (sg != null && "DiscreteOrderedField".equals(sg.referable().getRefName())) { dofGroup = sg; break; }
        }
        if (dofGroup != null) {
          StringBuilder sb = new StringBuilder();
          for (ConcreteGroup dyn : dofGroup.dynamicGroups()) {
            sb.append(dyn.referable().getRefName()).append(",");
          }
          log("Phase 2.5 DOF probe: DiscreteOrderedField.dynamicGroups count=" + dofGroup.dynamicGroups().size()
              + " sample=[" + (sb.length() > 300 ? sb.substring(0, 300) + "…" : sb) + "]");
        }
      }
    }

    // Hypothesis 1: probe Preorder's dynamic subgroups — `op` should be visible
    // via the DynamicScope's getElements. Check whether two scope resolutions from
    // different entry points (Order.PartialOrder module scope vs a subclass like
    // Algebra.Ordered.PosetAddMonoid) return the SAME LocatedReferable for `op`.
    {
      ModuleLocation orderLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Order.PartialOrder"));
      ConcreteGroup orderModule = serverV.getRawGroup(orderLoc);
      ConcreteGroup preorderGroup = null;
      if (orderModule != null) {
        for (ConcreteStatement s : orderModule.statements()) {
          ConcreteGroup sg = s.group();
          if (sg != null && "Preorder".equals(sg.referable().getRefName())) { preorderGroup = sg; break; }
        }
      }
      if (preorderGroup != null) {
        StringBuilder sb = new StringBuilder();
        for (ConcreteGroup dyn : preorderGroup.dynamicGroups()) {
          sb.append(dyn.referable().getRefName()).append("@")
            .append(System.identityHashCode(dyn.referable())).append(",");
        }
        log("Phase 2.5 hypothesis 1 probe: Preorder.dynamicGroups=[" + sb + "] count=" + preorderGroup.dynamicGroups().size());
        // Also: list its static statements (where-clause etc.)
        StringBuilder sb2 = new StringBuilder();
        for (ConcreteStatement s : preorderGroup.statements()) {
          ConcreteGroup sg = s.group();
          if (sg != null) sb2.append(sg.referable().getRefName()).append("@")
              .append(System.identityHashCode(sg.referable())).append(",");
        }
        log("Phase 2.5 hypothesis 1 probe: Preorder.statements subgroups=[" + sb2 + "]");
      }
    }

    // Identity probe: same class reached via different modules — do we get identical Java objects?
    // If not, `isSubClassOf` breaks and instance inference fails even though the instance is registered.
    {
      org.arend.core.definition.ClassDefinition preorderViaOrder = findClassInServer(serverV, "Order.PartialOrder.Preorder");
      // Also, pick a class in a DIFFERENT module that re-exposes Preorder (e.g., AbMonoid extends Semigroup extends Preorder... actually we need a direct case)
      // Look up Preorder via TypingInfo cross-module reference instead:
      ModuleLocation groupLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Algebra.Ordered"));
      ConcreteGroup groupModule = serverV.getRawGroup(groupLoc);
      org.arend.core.definition.ClassDefinition preorderViaGroup = null;
      // Walk Group's superclass chains to find Preorder
      if (groupModule != null) {
        for (ConcreteStatement s : groupModule.statements()) {
          ConcreteGroup sg = s.group();
          if (sg == null) continue;
          if (sg.referable() instanceof org.arend.naming.reference.TCDefReferable tc
              && tc.getTypechecked() instanceof org.arend.core.definition.ClassDefinition cd) {
            Set<org.arend.core.definition.ClassDefinition> visited = new HashSet<>();
            Deque<org.arend.core.definition.ClassDefinition> todo = new ArrayDeque<>();
            todo.add(cd);
            while (!todo.isEmpty()) {
              var c = todo.pop();
              if (!visited.add(c)) continue;
              if ("Preorder".equals(c.getName())) { preorderViaGroup = c; break; }
              todo.addAll(c.getSuperClasses());
            }
            if (preorderViaGroup != null) break;
          }
        }
      }
      if (preorderViaOrder != null && preorderViaGroup != null) {
        log("Phase 2.5 identity probe: Preorder via Order.Preorder=@"
            + System.identityHashCode(preorderViaOrder)
            + " via Group-super=@" + System.identityHashCode(preorderViaGroup)
            + " same=" + (preorderViaOrder == preorderViaGroup));
      } else {
        log("Phase 2.5 identity probe: couldn't find both (viaOrder="
            + (preorderViaOrder != null) + ", viaGroup=" + (preorderViaGroup != null) + ")");
      }
    }

    // Hypothesis 2 probe: can scope.getElements() of Operations module's scope see TypeHasProduct as INSTANCE?
    {
      ModuleLocation opLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Operations"));
      ConcreteGroup mg = serverV.getRawGroup(opLoc);
      if (mg != null) {
        org.arend.naming.scope.Scope moduleScope = org.arend.naming.scope.LexicalScope.opened(mg);
        int instanceCount = 0;
        StringBuilder sb = new StringBuilder();
        for (org.arend.naming.reference.Referable r : moduleScope.getElements()) {
          if (r instanceof org.arend.naming.reference.TCDefReferable tr
              && tr.getKind() == org.arend.naming.reference.GlobalReferable.Kind.INSTANCE) {
            instanceCount++;
            if (sb.length() < 200) sb.append(r.getRefName()).append(",");
          }
        }
        log("Phase 2.5 scope probe: Operations.scope.getElements INSTANCE count=" + instanceCount
            + " sample=[" + sb + "]");
      }
    }

    // Probe: for Operations, is TypeHasProduct's referable.getKind() == INSTANCE?
    {
      ModuleLocation opLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Operations"));
      ConcreteGroup mg = serverV.getRawGroup(opLoc);
      if (mg != null) {
        for (ConcreteStatement s : mg.statements()) {
          ConcreteGroup sg = s.group();
          if (sg == null) continue;
          if ("TypeHasProduct".equals(sg.referable().getRefName())) {
            log("Phase 2.5 instance kind probe: TypeHasProduct.kind=" + sg.referable().getKind()
                + " refClass=" + sg.referable().getClass().getSimpleName());
            break;
          }
        }
      }
    }

    // Dump subgroup tc status for a set of modules to compare
    for (String modName : new String[] {"Set", "Operations", "Logic", "Algebra.Pointed", "Algebra.Ring"}) {
      ModuleLocation loc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString(modName));
      ConcreteGroup mg = serverV.getRawGroup(loc);
      if (mg == null) { log("Phase 2.5 probe " + modName + ": <not-loaded>"); continue; }
      StringBuilder sb = new StringBuilder();
      for (ConcreteStatement s : mg.statements()) {
        ConcreteGroup sg = s.group();
        if (sg == null) continue;
        String tcT = "<no-tc>";
        if (sg.referable() instanceof org.arend.naming.reference.TCDefReferable tc2) {
          Object d = tc2.getTypechecked();
          tcT = d == null ? "<null>" : d.getClass().getSimpleName();
        }
        sb.append(sg.referable().getRefName()).append("(").append(tcT).append("),");
      }
      log("Phase 2.5 probe " + modName + " subgroups: [" + sb + "]");
    }

    // Probe field alias preservation for HasProduct.Product (alias ⨯)
    {
      ModuleLocation opLoc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString("Operations"));
      ConcreteGroup moduleGroup = serverV.getRawGroup(opLoc);
      if (moduleGroup != null) {
        StringBuilder modDump = new StringBuilder();
        for (ConcreteStatement s : moduleGroup.statements()) {
          ConcreteGroup sg = s.group();
          if (sg == null) continue;
          String name = sg.referable().getRefName();
          String tcT = "<no-tc>";
          if (sg.referable() instanceof org.arend.naming.reference.TCDefReferable tc2) {
            Object d = tc2.getTypechecked();
            tcT = d == null ? "<null>" : d.getClass().getSimpleName();
          }
          modDump.append(name).append("(").append(tcT).append("),");
        }
        log("Phase 2.5 Operations subgroups: [" + modDump + "]");

        for (ConcreteStatement s : moduleGroup.statements()) {
          ConcreteGroup sg = s.group();
          if (sg == null) continue;
          if ("HasProduct".equals(sg.referable().getRefName())) {
            StringBuilder sb = new StringBuilder();
            String refTy = sg.referable().getClass().getSimpleName();
            String tcTy = "<no-tc>";
            int pfCount = -1;
            if (sg.referable() instanceof org.arend.naming.reference.TCDefReferable tc2) {
              Object d = tc2.getTypechecked();
              tcTy = d == null ? "<null>" : d.getClass().getSimpleName();
              if (d instanceof org.arend.core.definition.ClassDefinition cd2) {
                pfCount = cd2.getPersonalFields().size();
                for (org.arend.core.definition.ClassField f : cd2.getPersonalFields()) {
                  sb.append(f.getName())
                    .append("(alias=").append(f.getReferable() == null ? "<null>" : f.getReferable().getAliasName())
                    .append(",refType=").append(f.getReferable() == null ? "<null>" : f.getReferable().getClass().getSimpleName())
                    .append(",isIR=").append(f.getReferable() instanceof org.arend.naming.reference.InternalReferable)
                    .append("), ");
                }
              }
            }
            log("Phase 2.5 alias probe: HasProduct refType=" + refTy + " tc=" + tcTy
                + " pfCount=" + pfCount + " fields=[" + sb + "]");

            // Also check what getInternalReferables returns:
            var irs = sg.getInternalReferables();
            log("Phase 2.5 alias probe: HasProduct getInternalReferables().size()=" + irs.size());
            break;
          }
        }
      }
    }

    // Probe constructor visibility via getInternalReferables for a few deserialized data types.
    for (String qn : new String[] {"Logic.TruncP", "Logic.||", "Data.Or.Or", "Data.Bool.Bool", "Arith.Nat.<"}) {
      int dot = qn.lastIndexOf('.');
      if (dot < 0) continue;
      String modPath = qn.substring(0, dot);
      String simpleName = qn.substring(dot + 1);
      ModuleLocation loc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
          org.arend.ext.module.ModulePath.fromString(modPath));
      ConcreteGroup moduleGroup = serverV.getRawGroup(loc);
      if (moduleGroup == null) continue;
      ConcreteGroup found = null;
      for (ConcreteStatement s : moduleGroup.statements()) {
        ConcreteGroup sg = s.group();
        if (sg != null && sg.referable().getRefName().equals(simpleName)) { found = sg; break; }
      }
      if (found == null) { log("Phase 2.5 probe: " + qn + " subgroup not found"); continue; }
      // Diagnose the fallback condition
      var refType = found.referable().getClass().getSimpleName();
      String tcType = "<not-TCDef>";
      int dataCtors = -1, classFields = -1;
      if (found.referable() instanceof org.arend.naming.reference.TCDefReferable tcRef2) {
        Object tc = tcRef2.getTypechecked();
        tcType = tc == null ? "<null>" : tc.getClass().getSimpleName();
        if (tc instanceof org.arend.core.definition.DataDefinition dd2) dataCtors = dd2.getConstructors().size();
        if (tc instanceof org.arend.core.definition.ClassDefinition cd2) classFields = cd2.getPersonalFields().size();
      }
      List<? extends org.arend.naming.reference.InternalReferable> irs = found.getInternalReferables();
      StringBuilder sb = new StringBuilder();
      for (var ir : irs) sb.append(ir.getRefName()).append(ir.isVisible() ? "" : "(hidden)").append(",");
      log("Phase 2.5 constructor probe: " + qn
          + " refType=" + refType + " tc=" + tcType
          + " dataCtors=" + dataCtors + " classFields=" + classFields
          + " internalRefs=[" + sb + "] size=" + irs.size());
    }

    // Probe classifying-field preservation for well-known classes in the cone.
    // RegularPreuniformSpace ultimately inherits classifying from BaseSet.E via TopSpace →
    // PrecoverSpace → CoverSpace. If BaseSet lost its classifying through serialization, all
    // downstream fresh-typechecked classes get null classifying and instance search cycles.
    for (String qn : new String[] {"Set.BaseSet", "Algebra.Pointed.Pointed", "Order.PartialOrder.Poset",
                                    "Algebra.Monoid.Monoid", "Algebra.Group.Group", "Algebra.Ring.Ring"}) {
      org.arend.core.definition.ClassDefinition cd = findClassInServer(serverV, qn);
      if (cd != null) {
        org.arend.core.definition.ClassField cf = cd.getClassifyingField();
        log("Phase 2.5 classifying probe: " + qn + " classifyingField="
            + (cf == null ? "<null>" : cf.getName()));
      } else {
        log("Phase 2.5 classifying probe: " + qn + " not found");
      }
    }
    return findings;
  }

  private static org.arend.core.definition.ClassDefinition findClassInServer(ArendServer server, String longName) {
    int dot = longName.lastIndexOf('.');
    if (dot < 0) return null;
    String modPath = longName.substring(0, dot);
    String simpleName = longName.substring(dot + 1);
    ModuleLocation loc = new ModuleLocation("arend-lib", ModuleLocation.LocationKind.SOURCE,
        org.arend.ext.module.ModulePath.fromString(modPath));
    ConcreteGroup group = server.getRawGroup(loc);
    if (group == null) return null;
    return walkForClass(group, simpleName);
  }

  private static org.arend.core.definition.ClassDefinition walkForClass(ConcreteGroup group, String name) {
    if (group.referable() instanceof org.arend.naming.reference.TCDefReferable r
        && name.equals(r.getRefName())
        && r.getTypechecked() instanceof org.arend.core.definition.ClassDefinition cd) {
      return cd;
    }
    for (ConcreteStatement s : group.statements()) {
      ConcreteGroup sub = s.group();
      if (sub != null) {
        org.arend.core.definition.ClassDefinition r = walkForClass(sub, name);
        if (r != null) return r;
      }
    }
    return null;
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
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null) walkTCRefs(sub, action);
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      walkTCRefs(dyn, action);
    }
  }

  private static String describePi(org.arend.core.expr.PiExpression pi) {
    if (pi == null) return "null";
    StringBuilder sb = new StringBuilder();
    sb.append("hash=@").append(System.identityHashCode(pi))
        .append(" params=").append(pi.getParameters())
        .append(" codomain.class=").append(pi.getCodomain() == null ? "null" : pi.getCodomain().getClass().getSimpleName());
    if (pi.getCodomain() instanceof org.arend.core.expr.PiExpression nested) {
      sb.append(" codomain.hash=@").append(System.identityHashCode(nested));
      sb.append(" SAME=").append(nested == pi);
    }
    try {
      var cfg = new org.arend.ext.prettyprinting.PrettyPrinterConfig() {};
      String rendered = pi.prettyPrint(cfg).toString();
      if (rendered.length() > 300) rendered = rendered.substring(0, 300) + "…";
      sb.append(" printed=").append(rendered);
    } catch (Throwable ignored) {
      sb.append(" printed=<print-failed>");
    }
    return sb.toString();
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
