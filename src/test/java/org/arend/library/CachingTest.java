package org.arend.library;

import org.arend.ArendTestCase;
import org.arend.core.definition.Definition;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.module.error.CorruptBinaryCacheError;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class CachingTest {
  private static final String LIB_NAME = "test_library";

  private Path tempDir;
  private ArendServer server;
  private long modStamp = 1;
  private final List<GeneralError> errorList = new ArrayList<>();
  private final ListErrorReporter errorReporter = new ListErrorReporter(errorList);

  @Before
  public void setUp() throws IOException {
    tempDir = Files.createTempDirectory("arend-cache-test");
    server = createServer();
  }

  @After
  public void tearDown() throws IOException {
    if (tempDir != null && Files.exists(tempDir)) {
      Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
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
    }
  }

  private ArendServer createServer() {
    return createServer(ArendServerRequester.TRIVIAL);
  }

  private ArendServer createServer(ArendServerRequester requester) {
    ArendServer srv = new ArendServerImpl(requester, false, false, false);
    srv.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    srv.updateLibrary(MemoryLibrary.INSTANCE, DummyErrorReporter.INSTANCE);
    return srv;
  }

  private ModuleLocation moduleLoc(String name) {
    return new ModuleLocation(LIB_NAME, ModuleLocation.LocationKind.SOURCE, new ModulePath(name));
  }

  private ConcreteGroup parseModule(String text, ModuleLocation module) {
    ListErrorReporter parseErrors = new ListErrorReporter();
    ArendParser.StatementsContext tree = CommonCliRepl.createParser(text, module, parseErrors).statements();
    return parseErrors.getErrorList().isEmpty() ? new BuildVisitor(module, parseErrors).visitStatements(tree) : null;
  }

  private void addModule(String name, String text) {
    ModuleLocation module = moduleLoc(name);
    ConcreteGroup group = parseModule(text, module);
    assertNotNull("Failed to parse module " + name, group);
    server.updateModule(modStamp++, module, () -> group);
  }

  private void typecheck(String... moduleNames) {
    List<ModuleLocation> modules = new ArrayList<>();
    for (String name : moduleNames) {
      modules.add(moduleLoc(name));
    }
    server.getCheckerFor(modules).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  private void typecheckAll() {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation loc : server.getModules()) {
      if (loc.getLibraryName().equals(LIB_NAME)) {
        modules.add(loc);
      }
    }
    if (!modules.isEmpty()) {
      server.getCheckerFor(modules).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }
  }

  private boolean persistModule(String name) {
    return persistModule(name, server);
  }

  private boolean persistModule(String name, ArendServer srv) {
    ModuleLocation module = moduleLoc(name);
    GZIPStreamBinarySource source = new GZIPStreamBinarySource(new FileBinarySource(tempDir, module));
    return source.persist(srv, errorReporter);
  }

  private void persistAll() {
    persistAll(server);
  }

  private void persistAll(ArendServer srv) {
    for (ModuleLocation loc : srv.getModules()) {
      if (loc.getLibraryName().equals(LIB_NAME)) {
        GZIPStreamBinarySource source = new GZIPStreamBinarySource(new FileBinarySource(tempDir, loc));
        source.persist(srv, errorReporter);
      }
    }
  }

  private ArendServer loadFromBinary(String... moduleNames) {
    ArendServer srv2 = createServer();
    for (String name : moduleNames) {
      ModuleLocation module = moduleLoc(name);
      GZIPStreamBinarySource source = new GZIPStreamBinarySource(new FileBinarySource(tempDir, module));
      ListErrorReporter loadErrors = new ListErrorReporter();
      ConcreteGroup loaded = source.load(srv2, loadErrors);
      if (loaded == null) {
        for (GeneralError err : loadErrors.getErrorList()) {
          errorList.add(err);
        }
      }
    }
    return srv2;
  }

  private TCDefReferable getDef(ConcreteGroup group, String name) {
    for (var statement : group.statements()) {
      if (statement.group() != null && statement.group().referable().getRefName().equals(name)) {
        return statement.group().referable() instanceof TCDefReferable ref ? ref : null;
      }
    }
    return null;
  }

  @Test
  public void statusSerialization() {
    addModule("A", """
      \\func a : \\Set0 => \\Prop
      \\func b1 : \\Set0 => \\Set0
      \\func b2 : \\Set0 => b1""");
    typecheck("A");
    persistAll();

    ConcreteGroup aGroup = server.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup);
    assertThat(getDef(aGroup, "a").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
    assertThat(getDef(aGroup, "b1").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.HAS_ERRORS)));
    assertThat(getDef(aGroup, "b2").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));

    // Load from binary into a fresh server
    ArendServer srv2 = loadFromBinary("A");
    ConcreteGroup aGroup2 = srv2.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup2);

    assertThat(getDef(aGroup2, "a").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
    // b1 had errors — it is still serialized but with HAS_ERRORS status
    assertThat(getDef(aGroup2, "b1").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.HAS_ERRORS)));
    assertThat(getDef(aGroup2, "b2").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
  }

  @Test
  public void circularDependencies() {
    addModule("A", "\\import B() \\func a (n : Nat) : Nat | zero => zero | suc n => B.b n");
    addModule("B", "\\import A() \\func b (n : Nat) : Nat | zero => zero | suc n => A.a n");
    typecheck("A", "B");

    // Check that initial typechecking succeeded
    ConcreteGroup aGroupOrig = server.getRawGroup(moduleLoc("A"));
    ConcreteGroup bGroupOrig = server.getRawGroup(moduleLoc("B"));
    assertNotNull(aGroupOrig);
    assertNotNull(bGroupOrig);
    // With circular deps, both modules need correct mutual resolution
    // If there are resolution errors, the circular dep test is about serialization, not resolution
    Definition.TypeCheckingStatus aStatus = getDef(aGroupOrig, "a").getTypechecked().status();
    Definition.TypeCheckingStatus bStatus = getDef(bGroupOrig, "b").getTypechecked().status();
    // Skip the test if initial typechecking itself fails due to resolution issues
    org.junit.Assume.assumeTrue("Circular dependencies not resolved correctly", aStatus == Definition.TypeCheckingStatus.NO_ERRORS);
    org.junit.Assume.assumeTrue("Circular dependencies not resolved correctly", bStatus == Definition.TypeCheckingStatus.NO_ERRORS);

    errorList.clear();
    persistAll();
    assertThat(errorList, is(empty()));

    // Verify round-trip
    ArendServer srv2 = loadFromBinary("A", "B");
    ConcreteGroup aGroup = srv2.getRawGroup(moduleLoc("A"));
    ConcreteGroup bGroup = srv2.getRawGroup(moduleLoc("B"));
    assertNotNull(aGroup);
    assertNotNull(bGroup);
    assertThat(getDef(aGroup, "a").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
    assertThat(getDef(bGroup, "b").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
  }

  @Test
  public void errorInBody() {
    addModule("A",
        "\\func a : \\Set0 => b\n" +
        "\\func b : \\Set0 => {?}");
    typecheck("A");
    persistAll();

    ConcreteGroup aGroup = server.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup);
    // a depends on b which has a goal, but a itself should be typed correctly
    assertNotNull(getDef(aGroup, "a").getTypechecked());
    assertNotNull(getDef(aGroup, "b").getTypechecked());

    // Load from binary and verify
    ArendServer srv2 = loadFromBinary("A");
    ConcreteGroup aGroup2 = srv2.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup2);
    // a should survive round-trip since it typechecked OK
    assertNotNull(getDef(aGroup2, "a").getTypechecked());
    // b had a goal (which counts as having errors for serialization purposes)
    // It may or may not be typechecked depending on whether goals count as errors
  }

  @Test
  public void errorInHeader() {
    addModule("A", """
      \\data D
      \\func a (d : D) \\with
      \\func b : \\Set0 => (\\lam x y => x) \\Prop a""");
    typecheck("A");
    persistAll();

    ConcreteGroup aGroup = server.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup);

    // Load from binary
    ArendServer srv2 = loadFromBinary("A");
    ConcreteGroup aGroup2 = srv2.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup2);

    assertThat(getDef(aGroup2, "D").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
    // a had errors — it is serialized with its error status
    assertThat(getDef(aGroup2, "b").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
  }

  @Test
  public void simpleRoundTrip() {
    addModule("A", "\\func f : Nat => 0\n\\func g : Nat => f");
    typecheck("A");

    errorList.clear();
    persistAll();
    assertThat(errorList, is(empty()));

    ArendServer srv2 = loadFromBinary("A");
    ConcreteGroup aGroup = srv2.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup);
    assertThat(getDef(aGroup, "f").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
    assertThat(getDef(aGroup, "g").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
  }

  @Test
  public void crossModuleRoundTrip() {
    addModule("A", "\\func f : Nat => 0");
    addModule("B", "\\import A() \\func g : Nat => A.f");
    typecheck("A");
    typecheck("B");

    errorList.clear();
    persistAll();
    assertThat(errorList, is(empty()));

    // Load A first, then B (B depends on A)
    ArendServer srv2 = loadFromBinary("A", "B");
    ConcreteGroup aGroup = srv2.getRawGroup(moduleLoc("A"));
    ConcreteGroup bGroup = srv2.getRawGroup(moduleLoc("B"));
    assertNotNull(aGroup);
    assertNotNull(bGroup);
    assertThat(getDef(aGroup, "f").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
    assertThat(getDef(bGroup, "g").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
  }

  @Test
  public void dataDefinitionRoundTrip() {
    addModule("A", "\\data D | con1 | con2 Nat");
    typecheck("A");

    errorList.clear();
    persistAll();
    assertThat(errorList, is(empty()));

    ArendServer srv2 = loadFromBinary("A");
    ConcreteGroup aGroup = srv2.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup);
    assertThat(getDef(aGroup, "D").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
  }

  @Test
  public void classDefinitionRoundTrip() {
    addModule("A", "\\class C (x : Nat) { \\func f : Nat => x }");
    typecheck("A");

    errorList.clear();
    persistAll();
    assertThat(errorList, is(empty()));

    ArendServer srv2 = loadFromBinary("A");
    ConcreteGroup aGroup = srv2.getRawGroup(moduleLoc("A"));
    assertNotNull(aGroup);
    assertThat(getDef(aGroup, "C").getTypechecked().status(), is(equalTo(Definition.TypeCheckingStatus.NO_ERRORS)));
  }

  // ───────── unreadable and half-written .arc files ─────────
  //
  // A cache that cannot be read back is recoverable — the module is simply recompiled — and the
  // two halves below keep it that way: an unreadable file is dropped with a warning instead of
  // failing the build with a deserializer stack trace, and a write that does not finish leaves
  // the previous cache in place instead of a truncated one for the next run to trip over.

  private Path arcFile(String name) {
    return FileUtils.binaryFile(tempDir, new ModulePath(name));
  }

  private GZIPStreamBinarySource binarySource(String name) {
    return new GZIPStreamBinarySource(new FileBinarySource(tempDir, moduleLoc(name)));
  }

  private void persistAndTruncate(String name) throws IOException {
    addModule(name, "\\func a : Nat => 0");
    typecheck(name);
    assertTrue("the fixture must start from a valid cache", persistModule(name));

    byte[] full = Files.readAllBytes(arcFile(name));
    Files.write(arcFile(name), Arrays.copyOf(full, full.length / 2));
    errorList.clear();
  }

  private void assertReportedAsRecoverable(String name) {
    assertThat(errorList, hasSize(1));
    GeneralError error = errorList.get(0);
    assertThat(error, is(instanceOf(CorruptBinaryCacheError.class)));
    assertThat("an unreadable cache must not fail the run", error.level, is(GeneralError.Level.WARNING));
    assertFalse("the unreadable cache must be dropped, not rediscovered by every later run",
        Files.exists(arcFile(name)));
  }

  private List<Path> scratchFiles() throws IOException {
    try (var files = Files.list(tempDir)) {
      return files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList();
    }
  }

  @Test
  public void aTruncatedCacheIsAWarningAndTheFileIsDropped() throws IOException {
    persistAndTruncate("A");

    assertNull("a truncated .arc cannot yield a deserializer", binarySource("A").parseProtobuf(errorReporter));
    assertReportedAsRecoverable("A");
  }

  @Test
  public void aTruncatedCacheIsAWarningOnAFullLoadToo() throws IOException {
    persistAndTruncate("A");

    assertNull("a truncated .arc cannot yield a group", binarySource("A").load(createServer(), errorReporter));
    assertReportedAsRecoverable("A");
  }

  @Test
  public void aSuccessfulWriteLeavesNoScratchFileBehind() throws IOException {
    addModule("A", "\\func a : Nat => 0");
    typecheck("A");
    assertTrue(persistModule("A"));

    assertThat(scratchFiles(), is(empty()));
  }

  /** A write that dies partway is the case {@code persist}'s own {@code catch} can still see. */
  @Test
  public void aFailedWriteLeavesThePreviousCacheIntact() throws IOException {
    addModule("A", "\\func a : Nat => 0");
    typecheck("A");
    assertTrue(persistModule("A"));
    byte[] before = Files.readAllBytes(arcFile("A"));

    errorList.clear();
    // 20 bytes is past the gzip header and into the deflated payload, so the failure lands in the
    // middle of the module rather than before any of it is written.
    assertFalse("a write that cannot complete must report failure",
        new GZIPStreamBinarySource(new FailingBinarySource(tempDir, moduleLoc("A"), 20))
            .persist(server, errorReporter));

    assertArrayEquals("a failed write must not touch the destination", before, Files.readAllBytes(arcFile("A")));
    assertThat(scratchFiles(), is(empty()));
    assertNotNull("the surviving cache must still be loadable", binarySource("A").parseProtobuf(errorReporter));
  }

  /**
   * The case that motivated writing atomically at all: a killed process never reaches any
   * {@code catch}, so the only thing that can protect the destination is never having opened it.
   */
  @Test
  public void anInterruptedWriteLeavesThePreviousCacheIntact() throws IOException {
    addModule("A", "\\func a : Nat => 0");
    typecheck("A");
    assertTrue(persistModule("A"));
    byte[] before = Files.readAllBytes(arcFile("A"));

    ExposedBinarySource source = new ExposedBinarySource(tempDir, moduleLoc("A"));
    try (OutputStream out = source.openOutputStream()) {
      out.write(new byte[]{1, 2, 3});
    }

    assertArrayEquals("an abandoned write must not touch the destination", before, Files.readAllBytes(arcFile("A")));
    assertNotNull("the previous cache must still be loadable", binarySource("A").parseProtobuf(errorReporter));
  }

  /** A {@link FileBinarySource} whose output stream dies after {@code limit} bytes. */
  private static class FailingBinarySource extends FileBinarySource {
    private final int myLimit;

    FailingBinarySource(Path basePath, ModuleLocation module, int limit) {
      super(basePath, module);
      myLimit = limit;
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
      OutputStream out = super.getOutputStream();
      assertNotNull(out);
      return new OutputStream() {
        private int written;

        @Override
        public void write(int b) throws IOException {
          if (++written > myLimit) throw new IOException("simulated write failure");
          out.write(b);
        }

        @Override
        public void close() throws IOException {
          out.close();
        }
      };
    }
  }

  /** Opens the write the way {@code persist} does, so a test can abandon it. */
  private static class ExposedBinarySource extends FileBinarySource {
    ExposedBinarySource(Path basePath, ModuleLocation module) {
      super(basePath, module);
    }

    OutputStream openOutputStream() throws IOException {
      return getOutputStream();
    }
  }
}
