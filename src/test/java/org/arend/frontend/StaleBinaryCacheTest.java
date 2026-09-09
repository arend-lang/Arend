package org.arend.frontend;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.BinaryLoader;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.module.error.BinaryCacheError;
import org.arend.prelude.Prelude;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.PersistableBinarySource;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@code BinaryLoader.loadBinaryCache}: that a warm {@code bin/} reloads whole,
 * that the set of caches it uses is closed under "calls into", and that phase 2 deserializes a
 * module only after everything it imports.
 *
 * <p>The ordering is load-bearing rather than cosmetic. Filling a definition in inspects its
 * callees, and a callee that is still a shell answers wrongly instead of failing, so a module
 * deserialized too early loads without complaint and surfaces later as a type error somewhere
 * else entirely. There is no order that satisfies an import cycle, which is why
 * {@code DeferredBoxFixes} exists and why the cycle has its own test here.
 *
 * <p>The pass that reads the caches always runs against a <em>fresh</em> server, which is what a
 * new CLI process hands the loader: every module parsed from source, no core anywhere, and a
 * populated {@code bin/}. A cold JVM over a warm {@code bin/} is the loader's habitat.
 */
public class StaleBinaryCacheTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String LIB = "lib";

  private Path libRoot;
  private ArendServerImpl server;
  private CliServerRequester requester;
  private BinaryLoader binaryLoader;
  private SourceLibrary library;
  private final List<GeneralError> errors = new ArrayList<>();

  // ───────── fixture ─────────

  /**
   * {@code Leaf <- Mid <- Top} is the chain the skip set has to close over, and the order phase 2
   * has to respect; {@code Unrelated} checks the closure does not over-close, and {@code Importer}
   * that it follows call targets rather than {@code \import}s — it imports {@code Leaf} without
   * referring to anything in it, so its {@code .arc} names no call target there and stays loadable.
   */
  private void writeLibrary() throws IOException {
    libRoot = tempFolder.newFolder(LIB).toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"),
        "sourcesDir: src\nbinariesDir: bin\n", StandardCharsets.UTF_8);

    writeModule("Leaf", "\\func leaf : Nat => 0\n");
    writeModule("Mid", "\\import Leaf\n\\func mid : Nat => leaf\n");
    writeModule("Top", "\\import Mid\n\\func top : Nat => mid\n");
    writeModule("Unrelated", "\\func unrelated : Nat => 7\n");
    writeModule("Importer", "\\import Leaf\n\\func importer : Nat => 8\n");
  }

  private void writeModule(String name, String body) throws IOException {
    Files.writeString(libRoot.resolve("src").resolve(name + ".ard"), body, StandardCharsets.UTF_8);
  }


  /**
   * Rewrite a module and age <em>its</em> {@code .arc} a second below the new source, so the
   * staleness comparison does not ride on filesystem timer granularity. Aging the cache rather
   * than stamping the source into the future matters for
   * {@link #theRunRepairsTheCacheForTheNextOne}: a source dated ahead of the {@code .arc} the
   * repairing pass writes would look stale forever.
   */
  private void editModule(String name, String body) throws IOException {
    Path file = libRoot.resolve("src").resolve(name + ".ard");
    Files.writeString(file, body, StandardCharsets.UTF_8);
    Path arc = libRoot.resolve("bin").resolve(name + ".arc");
    if (Files.exists(arc)) {
      Files.setLastModifiedTime(arc,
          FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() - 1000));
    }
  }

  /** Discards all in-memory state, as starting a new CLI process does, and keeps {@code bin/}. */
  private void newServer() {
    LibraryManager libraryManager = new LibraryManager(new ListErrorReporter(errors));
    requester = new CliServerRequester(libraryManager);
    binaryLoader = new BinaryLoader(libraryManager);
    server = new ArendServerImpl(requester, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server.addErrorReporter(new ListErrorReporter(errors));

    library = FileSourceLibrary.fromConfigFile(
        libRoot.resolve("arend.yaml"), false, DummyErrorReporter.INSTANCE);
    assertNotNull("failed to read the generated arend.yaml", library);
    libraryManager.updateLibrary(library, server);
  }

  private ModuleLocation moduleLoc(String name) {
    return new ModuleLocation(LIB, ModuleLocation.LocationKind.SOURCE, new ModulePath(name));
  }

  /** One CLI pass, in {@code TypecheckPipeline.run}'s order: prelude, resolve, load, check, persist. */
  private void pass() {
    errors.clear();
    server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
        .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    List<ModuleLocation> all = new ArrayList<>();
    for (ModulePath mp : library.findModules(false)) all.add(moduleLoc(mp.toString()));
    server.getCheckerFor(all).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    binaryLoader.loadBinaryCache(library, server);

    for (ModuleLocation module : all) {
      server.getCheckerFor(Collections.singletonList(module))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }

    for (ModuleLocation module : all) {
      PersistableBinarySource binary = library.getBinarySource(module.getModulePath());
      if (binary != null) binary.persist(server, DummyErrorReporter.INSTANCE);
    }
  }

  /** A cold pass over a fully populated {@code bin/}, which is the state under test. */
  private void buildCachesThenRestart() throws IOException {
    writeLibrary();
    newServer();
    pass();
    assertNoBinaryCacheErrors("cold build");
    for (String module : List.of("Leaf", "Mid", "Top", "Unrelated", "Importer")) {
      assertNotNull("cold build did not persist " + module,
          library.getBinarySource(new ModulePath(module)));
    }
    newServer();
  }

  // ───────── assertions ─────────

  private void assertNoBinaryCacheErrors(String phase) {
    List<GeneralError> cacheErrors = errors.stream().filter(e -> e instanceof BinaryCacheError).toList();
    assertTrue(phase + ": binary cache load must not fail, got " + cacheErrors, cacheErrors.isEmpty());
  }

  private void assertNoErrors(String phase) {
    List<GeneralError> hard = errors.stream()
        .filter(e -> e.level == GeneralError.Level.ERROR).toList();
    assertTrue(phase + ": expected no errors, got " + hard, hard.isEmpty());
  }

  private void assertLoadedFromCache(String phase, String module) {
    assertTrue(phase + ": " + module + " should have been loaded from its .arc",
        binaryLoader.getBinaryCacheLoaded().contains(moduleLoc(module)));
  }


  private void assertNotLoadedFromCache(String phase, String module) {
    assertFalse(phase + ": " + module + "'s .arc is not usable and must not be loaded",
        binaryLoader.getBinaryCacheLoaded().contains(moduleLoc(module)));
  }

  // ───────── tests ─────────

  /** Baseline: an untouched {@code bin/} loads whole, so the tests below start from a known state. */
  @Test
  public void anUntouchedCacheLoadsCompletely() throws IOException {
    buildCachesThenRestart();
    pass();

    assertNoBinaryCacheErrors("reload with no edits");
    assertNoErrors("reload with no edits");
    for (String module : List.of("Leaf", "Mid", "Top", "Unrelated", "Importer")) {
      assertLoadedFromCache("reload with no edits", module);
    }
  }

  /**
   * The reported bug: {@code Leaf} is dropped for being older than its source, so {@code Mid}'s
   * {@code .arc} — newer than <em>its own</em> source, hence a phase-1 candidate — asks for a
   * call target nobody filled in.
   */
  @Test
  public void editingADependencyDoesNotBreakItsDependentsLoad() throws IOException {
    buildCachesThenRestart();
    editModule("Leaf", "\\func leaf : Nat => 1\n");
    pass();

    assertNoBinaryCacheErrors("after editing Leaf");
    assertNoErrors("after editing Leaf");
    assertNotLoadedFromCache("after editing Leaf", "Leaf");
    assertNotLoadedFromCache("after editing Leaf", "Mid");
  }

  /** The skip set has to be a fixed point: {@code Top} reaches {@code Leaf} only through {@code Mid}. */
  @Test
  public void theSkipSetIsClosedTransitively() throws IOException {
    buildCachesThenRestart();
    editModule("Leaf", "\\func leaf : Nat => 1\n");
    pass();

    assertNoBinaryCacheErrors("after editing Leaf");
    assertNotLoadedFromCache("after editing Leaf", "Top");
  }

  /** Closing the skip set must not swallow modules that do not depend on the edited one. */
  @Test
  public void anIndependentModuleStillLoadsFromCache() throws IOException {
    buildCachesThenRestart();
    editModule("Leaf", "\\func leaf : Nat => 1\n");
    pass();

    assertLoadedFromCache("after editing Leaf", "Unrelated");
  }

  /**
   * Closure follows the serialized call targets, not the {@code \import} graph: {@code Importer}
   * imports {@code Leaf} but refers to nothing in it, so its {@code .arc} is still consistent.
   * Walking imports instead would needlessly re-typecheck it — on arend-lib that would drag in
   * every module importing a hub like {@code Algebra.Monoid}.
   */
  @Test
  public void anImportWithoutAReferenceDoesNotInvalidateTheCache() throws IOException {
    buildCachesThenRestart();
    editModule("Leaf", "\\func leaf : Nat => 1\n");
    pass();

    assertLoadedFromCache("after editing Leaf", "Importer");
  }

  /** A missing {@code .arc} makes dependents' caches unusable just as a stale one does. */
  @Test
  public void deletingADependencysCacheInvalidatesItsDependents() throws IOException {
    buildCachesThenRestart();
    Files.delete(libRoot.resolve("bin").resolve("Leaf.arc"));
    pass();

    assertNoBinaryCacheErrors("after deleting Leaf.arc");
    assertNoErrors("after deleting Leaf.arc");
    assertNotLoadedFromCache("after deleting Leaf.arc", "Mid");
    assertNotLoadedFromCache("after deleting Leaf.arc", "Top");
    assertLoadedFromCache("after deleting Leaf.arc", "Unrelated");
  }

  /**
   * The pass that skipped a module has to re-persist it, or the inconsistency survives the run and
   * the next cold start pays for it again. This is what {@code arend -r} used to be needed for.
   */
  @Test
  public void theRunRepairsTheCacheForTheNextOne() throws IOException {
    buildCachesThenRestart();
    editModule("Leaf", "\\func leaf : Nat => 1\n");
    pass();
    assertNoErrors("after editing Leaf");

    newServer();
    pass();

    assertNoBinaryCacheErrors("second cold start");
    assertNoErrors("second cold start");
    for (String module : List.of("Leaf", "Mid", "Top", "Unrelated", "Importer")) {
      assertLoadedFromCache("second cold start", module);
    }
  }

  /** A changed signature must reach dependents through the cache load too, not just a body change. */
  @Test
  public void aChangedSignatureIsPropagatedThroughTheCache() throws IOException {
    buildCachesThenRestart();
    // mid : Nat => leaf no longer typechecks as written, so it must be re-elaborated against the
    // new signature rather than restored from an .arc that predates it.
    editModule("Leaf", "\\func leaf : Fin 3 => 0\n");
    editModule("Mid", "\\import Leaf\n\\func mid : Fin 3 => leaf\n");
    editModule("Top", "\\import Mid\n\\func top : Fin 3 => mid\n");
    pass();

    assertNoBinaryCacheErrors("after changing leaf's signature");
    assertNoErrors("after changing leaf's signature");
  }

  /**
   * Filling a definition in inspects its callees, and a callee that is still a shell answers
   * wrongly instead of failing — {@code FieldCallExpression.make} unfolds a property field it is
   * told is not one, {@code fixBoxes} boxes nothing when the parameter list comes back empty.
   * Nothing throws, so the module loads and the damage surfaces much later as a type error in an
   * unrelated definition typechecked from source. Deserializing dependencies first is what keeps
   * that from happening, so the order is part of the loader's contract rather than an incidental
   * property of whatever order {@code server.getModules()} happens to return.
   */
  @Test
  public void modulesAreDeserializedDependenciesFirst() throws IOException {
    buildCachesThenRestart();
    pass();

    List<ModuleLocation> order = binaryLoader.getLoadOrder();
    assertTrue("Leaf must be filled in before Mid, which links against it",
        order.indexOf(moduleLoc("Leaf")) < order.indexOf(moduleLoc("Mid")));
    assertTrue("Mid must be filled in before Top",
        order.indexOf(moduleLoc("Mid")) < order.indexOf(moduleLoc("Top")));
    assertTrue("Importer imports Leaf, so it comes after it too",
        order.indexOf(moduleLoc("Leaf")) < order.indexOf(moduleLoc("Importer")));
    assertEquals("every candidate has to be in the order, not just the ones with imports",
        5, order.size());
  }

  /**
   * An import cycle admits no dependencies-first order at all. The loader has to break it and
   * carry on rather than drop a module or spin, since arend-lib has such cycles
   * ({@code Algebra.StrictlyOrdered} ↔ {@code Arith.Nat}).
   */
  @Test
  public void anImportCycleStillLoads() throws IOException {
    writeLibrary();
    // The imports form a cycle; the definitions do not (base <- cb <- ca), since mutual
    // recursion across modules is rejected outright and would test nothing about loading.
    writeModule("CycleA", "\\import CycleB()\n\\func base : Nat => 3\n\\func ca : Nat => CycleB.cb\n");
    writeModule("CycleB", "\\import CycleA()\n\\func cb : Nat => CycleA.base\n");
    newServer();
    pass();
    assertNoErrors("cold build with an import cycle");
    newServer();
    pass();

    assertNoBinaryCacheErrors("reload with an import cycle");
    assertNoErrors("reload with an import cycle");
    assertLoadedFromCache("reload with an import cycle", "CycleA");
    assertLoadedFromCache("reload with an import cycle", "CycleB");
  }

  /** With no cache at all there are no candidates, so nothing may be skipped or reported. */
  @Test
  public void aColdBuildWithNoCacheIsSilent() throws IOException {
    writeLibrary();
    newServer();
    pass();

    assertNoBinaryCacheErrors("cold build");
    assertNoErrors("cold build");
    assertEquals("nothing can be loaded from a cache that does not exist yet",
        Collections.emptySet(), binaryLoader.getBinaryCacheLoaded());
  }
}
