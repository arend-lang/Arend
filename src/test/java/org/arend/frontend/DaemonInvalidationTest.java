package org.arend.frontend;

import org.arend.core.definition.Definition;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.ArendExtension;
import org.arend.ext.DefinitionContributor;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.typechecking.meta.DependencyMetaTypechecker;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.PersistableBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for incremental invalidation across repeated typecheck passes on one warm
 * {@code ArendServer} — the daemon's situation. Two bugs are pinned here, both of which left a
 * definition's core replaced by a fresh object while dependents kept the old one, so
 * identity-based checks failed with diagnostics absent from the sources (on arend-lib, a bare
 * {@code touch} of one core module produced 226 broken modules and 18 688 errors): a blanket
 * {@code clearTypechecked} of an edited module, and restoring an invalidated module from its
 * own — now semantically stale — {@code .arc}.
 *
 * <p>Assertions are therefore about <em>core object identity</em> rather than error counts: an
 * error count only catches the incoherence if something happens to re-elaborate a dependent.
 * The fixture is a real on-disk library with a {@code binariesDir}, since both bugs live in the
 * {@code .arc} load path.
 */
public class DaemonInvalidationTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String LIB = "lib";

  // ───────── probe extension: a meta that captures a class, as arend-lib's do ─────────

  /**
   * Stands in for arend-lib's equation metas, which capture core objects in {@code @Dependency}
   * fields ({@code CoreClassDefinition CSemiring}, {@code CoreClassField Semigroup.*}, …). The
   * holding instance is built once, when the {@code \meta} is typechecked, and cached on its
   * {@code MetaReferable} — so unless re-binding happens the meta keeps using a dead
   * {@code ClassDefinition}, which in arend-lib surfaces as {@code IllegalArgumentException:
   * Expected an expression of type '…'} out of {@code linarith} / {@code equation}.
   */
  public static class ProbeMeta extends BaseMetaDefinition {
    /** The most recently built instance — i.e. the one currently cached on the MetaReferable. */
    public static volatile ProbeMeta lastInstance;
    /** How many times a fresh instance was built; one per meta-definition typecheck. */
    public static volatile int instances;
    /** How many times a use site elaborated through this meta. */
    public static volatile int invocations;
    /** What {@link #invokeMeta} saw in its captured field, most recently. */
    public static volatile CoreClassDefinition lastInvokedWith;

    @Dependency public CoreClassDefinition ProbeClass;

    public ProbeMeta() {
      instances++;
      lastInstance = this;
    }

    @Override
    public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker,
                                                @NotNull ContextData contextData) {
      invocations++;
      lastInvokedWith = ProbeClass;
      // Trivial by design; the point is the capture, not the result. A use site is still needed
      // because a meta nothing references is never typechecked, and so never captures.
      return typechecker.typecheck(ProbeExtension.factory.number(0), null);
    }
  }

  /** Declares {@link ProbeMeta} as {@code ProbeMetaModule.probeMeta} in the test library. */
  public static class ProbeExtension implements ArendExtension {
    static ConcreteFactory factory;

    @Override
    public void setConcreteFactory(@NotNull ConcreteFactory concreteFactory) {
      factory = concreteFactory;
    }

    @Override
    public void declareDefinitions(@NotNull DefinitionContributor contributor) {
      ModulePath metaModule = new ModulePath("ProbeMetaModule");
      // Without the import, the meta's `{?} (ProbeClass)` body cannot resolve ProbeClass.
      contributor.declare(metaModule, new ModulePath("Probe"));
      DependencyMetaTypechecker typechecker =
          new DependencyMetaTypechecker(ProbeMeta.class, ProbeMeta::new);
      MetaRef ref = factory.metaRef(factory.moduleRef(metaModule), "probeMeta",
          Precedence.DEFAULT, null, null, null, typechecker);
      contributor.declare(DocFactory.text("captures ProbeClass"),
          factory.metaDef(ref, Collections.emptyList(), typechecker.makeBody(factory)));
    }
  }

  private ArendServerImpl server;
  private CliServerRequester requester;
  private SourceLibrary library;
  private Path libRoot;
  private final List<GeneralError> errors = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    ProbeMeta.instances = 0;
    ProbeMeta.invocations = 0;
    ProbeMeta.lastInstance = null;
    ProbeMeta.lastInvokedWith = null;

    libRoot = tempFolder.newFolder(LIB).toPath();
    Files.createDirectories(libRoot.resolve("src"));
    // Must exist for a ClassLoaderDelegate to be created; the class is then found on the test
    // classpath by the parent loader (same trick as LibraryLoadOrderTest).
    Files.createDirectories(libRoot.resolve("ext"));
    Files.writeString(libRoot.resolve("arend.yaml"),
        "sourcesDir: src\nbinariesDir: bin\nextensionsDir: ext\nextensionMainClass: "
            + ProbeExtension.class.getName() + "\n", StandardCharsets.UTF_8);

    writeModule("Leaf", "\\func leaf : Nat => 0\n");
    writeModule("Mid", "\\import Leaf\n\\func mid : Nat => leaf\n");
    writeModule("Top", "\\import Mid\n\\func top : Nat => mid\n");
    writeModule("Probe", "\\class ProbeClass { | pf : Nat }\n");
    // Nothing typecheckable, like src/Debug.ard (only a \meta) or a wholly commented-out module:
    // never holds cores, so "no cores means invalidated" must not fire for it.
    writeModule("Empty", "{- nothing typecheckable here -}\n");
    writeModule("MetaUse", "\\import ProbeMetaModule\n\\func usesProbe : Nat => probeMeta\n");

    LibraryManager libraryManager = new LibraryManager(new ListErrorReporter(errors));
    requester = new CliServerRequester(libraryManager);
    server = new ArendServerImpl(requester, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server.addErrorReporter(new ListErrorReporter(errors));

    library = FileSourceLibrary.fromConfigFile(
        libRoot.resolve("arend.yaml"), false, DummyErrorReporter.INSTANCE);
    assertNotNull("failed to read the generated arend.yaml", library);
    libraryManager.updateLibrary(library, server);
  }

  // ───────── fixture helpers ─────────

  private void writeModule(String name, String body) throws IOException {
    Files.writeString(libRoot.resolve("src").resolve(name + ".ard"), body, StandardCharsets.UTF_8);
  }

  /**
   * Rewrite a module, stamping its mtime a second ahead so it is unambiguously newer than the
   * previous pass's {@code .arc} — otherwise the test rides on filesystem timer granularity.
   */
  private void editModule(String name, String body) throws IOException {
    Path file = libRoot.resolve("src").resolve(name + ".ard");
    long before = Files.getLastModifiedTime(file).toMillis();
    Files.writeString(file, body, StandardCharsets.UTF_8);
    Files.setLastModifiedTime(file, FileTime.fromMillis(Math.max(before, System.currentTimeMillis()) + 1000));
  }

  private ModuleLocation moduleLoc(String name) {
    return new ModuleLocation(LIB, ModuleLocation.LocationKind.SOURCE, new ModulePath(name));
  }

  /**
   * One CLI pass over the library, in {@code TypecheckPipeline.run}'s order: prelude, resolve,
   * preload binary caches, typecheck, persist.
   */
  private void pass() {
    errors.clear();
    server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
        .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    List<ModuleLocation> all = new ArrayList<>();
    for (ModulePath mp : library.findModules(false)) all.add(moduleLoc(mp.toString()));
    server.getCheckerFor(all).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    requester.loadBinaryCache(library, server);

    for (ModuleLocation module : all) {
      server.getCheckerFor(Collections.singletonList(module))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }

    for (ModuleLocation module : all) {
      PersistableBinarySource binary = library.getBinarySource(module.getModulePath());
      if (binary != null) binary.persist(server, DummyErrorReporter.INSTANCE);
    }
  }

  /** Every typecheckable definition of {@code module}, keyed by short name. */
  private Map<String, TCDefReferable> defsOf(String module) {
    Map<String, TCDefReferable> result = new LinkedHashMap<>();
    ConcreteGroup group = server.getRawGroup(moduleLoc(module));
    assertNotNull("module " + module + " is not loaded", group);
    collect(group, result);
    return result;
  }

  private static void collect(ConcreteGroup group, Map<String, TCDefReferable> out) {
    if (group.referable() instanceof TCDefReferable ref && ref.getKind().isTypecheckable()) {
      out.put(ref.getRefName(), ref);
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) collect(statement.group(), out);
    }
    for (ConcreteGroup dynamic : group.dynamicGroups()) collect(dynamic, out);
  }

  private Definition core(String module, String name) {
    TCDefReferable ref = defsOf(module).get(name);
    assertNotNull(name + " not found in " + module, ref);
    Definition def = ref.getTypechecked();
    assertNotNull(name + " in " + module + " is not typechecked", def);
    return def;
  }

  /**
   * No ERROR-level diagnostic, and every definition holds a clean core. ERROR-only on purpose:
   * re-typechecking a module legitimately makes its dependents' {@code .arc} unloadable, and
   * that WARNING-level {@code BinaryCacheError} is expected noise here.
   */
  private void assertNoErrors(String phase) {
    List<GeneralError> hard = errors.stream()
        .filter(e -> e.level == GeneralError.Level.ERROR).toList();
    assertTrue(phase + ": expected no errors, got " + hard, hard.isEmpty());
    for (String module : List.of("Leaf", "Mid", "Top")) {
      for (Map.Entry<String, TCDefReferable> entry : defsOf(module).entrySet()) {
        Definition def = entry.getValue().getTypechecked();
        assertNotNull(phase + ": " + module + "." + entry.getKey() + " lost its core", def);
        assertEquals(phase + ": " + module + "." + entry.getKey() + " is not clean",
            Definition.TypeCheckingStatus.NO_ERRORS, def.status());
      }
    }
  }

  // ───────── tests ─────────

  /** Baseline: the fixture typechecks and persists cleanly on a cold pass. */
  @Test
  public void coldPassIsClean() {
    pass();
    assertNoErrors("cold pass");
  }

  /** A second pass with no edits must not rebuild anything. */
  @Test
  public void repeatedPassPreservesCoreIdentity() {
    pass();
    Definition leaf = core("Leaf", "leaf"), mid = core("Mid", "mid"), top = core("Top", "top");

    pass();

    assertNoErrors("second pass");
    assertSame("leaf must not be rebuilt when nothing changed", leaf, core("Leaf", "leaf"));
    assertSame("mid must not be rebuilt when nothing changed", mid, core("Mid", "mid"));
    assertSame("top must not be rebuilt when nothing changed", top, core("Top", "top"));
  }

  /**
   * The discriminating case: appending leaves every existing definition unchanged, so none may
   * be rebuilt. Rebuilding {@code leaf} here is what stranded dependents on a dead object.
   */
  @Test
  public void appendingADefinitionRebuildsNothingElse() throws IOException {
    pass();
    Definition leaf = core("Leaf", "leaf"), mid = core("Mid", "mid"), top = core("Top", "top");

    editModule("Leaf", "\\func leaf : Nat => 0\n\\func extra : Nat => 1\n");
    pass();

    assertNoErrors("after appending to Leaf");
    assertSame("appending a sibling must not rebuild leaf", leaf, core("Leaf", "leaf"));
    assertSame("appending to a dependency must not rebuild mid", mid, core("Mid", "mid"));
    assertSame("appending to a dependency must not rebuild top", top, core("Top", "top"));
    assertNotNull("the appended definition must be typechecked", core("Leaf", "extra"));
  }

  /** A no-op rewrite (same bytes, newer mtime) is the `touch` case; nothing may be rebuilt. */
  @Test
  public void touchingASourceRebuildsNothing() throws IOException {
    pass();
    Definition leaf = core("Leaf", "leaf"), mid = core("Mid", "mid"), top = core("Top", "top");

    editModule("Leaf", "\\func leaf : Nat => 0\n");
    pass();

    assertNoErrors("after touching Leaf");
    assertSame("a touch must not rebuild leaf", leaf, core("Leaf", "leaf"));
    assertSame("a touch must not rebuild mid", mid, core("Mid", "mid"));
    assertSame("a touch must not rebuild top", top, core("Top", "top"));
  }

  /**
   * The other direction: a real body change must propagate. Both dependents hold the old core in
   * their expression trees, so both have to be rebuilt or the server is left incoherent.
   */
  @Test
  public void changingABodyRebuildsTheDependencyCone() throws IOException {
    pass();
    Definition leaf = core("Leaf", "leaf"), mid = core("Mid", "mid"), top = core("Top", "top");

    editModule("Leaf", "\\func leaf : Nat => 1\n");
    pass();

    assertNoErrors("after changing leaf's body");
    assertNotSame("leaf changed, so it must be rebuilt", leaf, core("Leaf", "leaf"));
    assertNotSame("mid depends on leaf, so it must be rebuilt", mid, core("Mid", "mid"));
    assertNotSame("top transitively depends on leaf, so it must be rebuilt", top, core("Top", "top"));
  }

  /**
   * The meta case: re-typechecking a captured class must also re-bind the meta that captured it,
   * or the meta keeps using a dead {@code ClassDefinition}. See {@link ProbeMeta}.
   */
  @Test
  public void editingACapturedClassRebindsTheMeta() throws IOException {
    pass();
    assertNoErrors("cold pass with the probe meta");

    Definition classV1 = core("Probe", "ProbeClass");
    Definition useV1 = core("MetaUse", "usesProbe");
    ProbeMeta boundV1 = ProbeMeta.lastInstance;
    assertNotNull("the meta must have been typechecked (nothing captured it otherwise)", boundV1);
    assertSame("the meta must capture the live class on the first pass",
        classV1, boundV1.ProbeClass);

    // Changes ProbeClass's own concrete, so it is genuinely rebuilt; nothing constructs a
    // ProbeClass, so no other module needs touching.
    editModule("Probe", "\\class ProbeClass { | pf : Nat | pg : Nat }\n");
    pass();

    assertNoErrors("after adding a field to the captured class");
    Definition classV2 = core("Probe", "ProbeClass");
    // Cause-first along usesProbe -> probeMeta -> ProbeClass, so a failure names the earliest
    // broken link rather than the final symptom.
    assertNotSame("ProbeClass changed, so it must be rebuilt", classV1, classV2);
    assertNotSame("the invalidation cone must reach the meta's use site",
        useV1, core("MetaUse", "usesProbe"));
    assertTrue("the meta must have been re-typechecked, not reused from cache",
        ProbeMeta.instances > 1);
    assertSame("the meta must be re-bound to the current ProbeClass, not the dead one",
        classV2, ProbeMeta.lastInstance.ProbeClass);
  }

  /** The meta must not be needlessly re-bound when the class it captures did not change. */
  @Test
  public void editingAnUnrelatedModuleLeavesTheMetaBindingAlone() throws IOException {
    pass();
    Definition classV1 = core("Probe", "ProbeClass");
    ProbeMeta boundV1 = ProbeMeta.lastInstance;
    int instancesAfterFirstPass = ProbeMeta.instances;

    editModule("Leaf", "\\func leaf : Nat => 1\n");
    pass();

    assertNoErrors("after editing an unrelated module");
    assertSame("ProbeClass is untouched by a Leaf edit", classV1, core("Probe", "ProbeClass"));
    assertEquals("the meta must not be re-typechecked when its dependencies are untouched",
        instancesAfterFirstPass, ProbeMeta.instances);
    assertSame("and its binding must be the same instance", boundV1, ProbeMeta.lastInstance);
  }

  /**
   * A module with nothing typecheckable must stay in the persist skip set: it never has cores, so
   * a naive "no cores means invalidated" rule re-persists it forever (measured on arend-lib as
   * two {@code .arc} rewrites per no-op pass, {@code Debug} and {@code OuterMeasureRing}).
   */
  @Test
  public void aModuleWithNothingTypecheckableStaysSkippable() {
    pass();
    pass();

    assertTrue("a module with no typecheckable definitions must not be re-derived every pass",
        requester.getBinaryCacheLoaded().contains(moduleLoc("Empty")));
  }

  /** A changed signature must propagate the same way, and must not leave stale errors behind. */
  @Test
  public void changingASignatureRebuildsTheDependencyConeAndStaysClean() throws IOException {
    pass();
    Definition mid = core("Mid", "mid");

    // mid : Nat => leaf must be re-elaborated against the new signature.
    editModule("Leaf", "\\func leaf : Fin 3 => 0\n");
    pass();

    assertNotSame("mid must be rebuilt after its dependency's signature changed", mid, core("Mid", "mid"));

    // Put it back: the server must converge, not accumulate stale state.
    editModule("Leaf", "\\func leaf : Nat => 0\n");
    pass();
    assertNoErrors("after reverting leaf's signature");
  }
}
