package org.arend.server.imports;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.MemoryLibrary;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.elimtree.Body;
import org.arend.naming.reference.TCDefReferable;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * An instance is never written down, so nothing about it shows up when a module is resolved. These
 * check the other half of the answer: the namespace command that put into the instance pool an
 * instance the typechecker turned out to pick is charged too, and until the module is typechecked
 * there is no answer to give at all.
 */
public class UsedInstancesTest {
  private static final String LIB_NAME = "test_library";

  /** The class and the instance live apart, so the import of the instance is needed for it alone. */
  private static final String CLASS_MODULE = "\\class A (T : \\Type) { | t : T }";
  private static final String INSTANCE_MODULE = "\\import Foo\n\n\\instance nat : A Nat 1";
  private static final String LEMMA_MAIN = "\\import Foo\n\\import Bar\n\n\\lemma p {P : \\Prop} (f : Nat -> P) : P => f t";

  private ArendServer server;
  private long modStamp = 1;
  private Path tempDir;

  private ArendServer createServer(boolean clearLemmas) {
    ArendServer result = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, clearLemmas);
    result.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    result.updateLibrary(MemoryLibrary.INSTANCE, DummyErrorReporter.INSTANCE);
    return result;
  }

  private static ModuleLocation moduleLoc(String name) {
    return new ModuleLocation(LIB_NAME, ModuleLocation.LocationKind.SOURCE, new ModulePath(name.split("\\.")));
  }

  private void addModule(String name, String text) {
    if (server == null) server = createServer(false);
    ModuleLocation module = moduleLoc(name);
    ListErrorReporter errorReporter = new ListErrorReporter();
    ArendParser.StatementsContext tree = CommonCliRepl.createParser(text, module, errorReporter).statements();
    assertEquals("parse errors in " + name, List.of(), errorReporter.getErrorList());
    ConcreteGroup group = new BuildVisitor(module, errorReporter).visitStatements(tree);
    assertNotNull("failed to parse " + name, group);
    server.updateModule(modStamp++, module, () -> group);
  }

  private List<ModuleLocation> allModules() {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLibraryName().equals(LIB_NAME)) modules.add(module);
    }
    return modules;
  }

  private void resolve() {
    server.getCheckerFor(allModules()).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  private void typecheck() {
    server.getCheckerFor(allModules()).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertEquals("typechecking errors", List.of(), server.getErrorMap().values().stream().flatMap(List::stream)
      .filter(error -> error.level.ordinal() >= GeneralError.Level.ERROR.ordinal()).toList());
  }

  private static String render(NamespaceCommandUsage usage, ConcreteGroup group) {
    // a list rather than a set: two identical commands are two results, and a test that collapsed
    // them could not tell "the second one is superfluous" from "both are"
    List<String> result = new ArrayList<>();
    for (NamespaceCommandUsage.Part part : usage.getUnused(ImportUsageTracer.collectCommands(group))) {
      result.add(part.toString());
    }
    Collections.sort(result);
    return String.join(", ", result);
  }

  /** What the resolution half alone reports, which ignores instances. */
  private String unusedByResolution(String name) {
    resolve();
    NamespaceCommandUsage usage = ImportUsageTracer.traceResolution((ArendServerImpl) server, moduleLoc(name));
    assertNotNull(usage);
    return render(usage, server.getRawGroup(moduleLoc(name)));
  }

  /** What the whole answer is, once the module has been typechecked. */
  private String unused(String name) {
    typecheck();
    NamespaceCommandUsage usage = server.getNamespaceCommandUsage(moduleLoc(name));
    assertNotNull("no usage for " + name, usage);
    return render(usage, server.getRawGroup(moduleLoc(name)));
  }

  // ------------------------------------------------------------------ the core is required

  @Test
  public void aModuleThatIsOnlyResolvedHasNoAnswer() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\func p : Nat => t");
    resolve();
    assertNull(server.getNamespaceCommandUsage(moduleLoc("Main")));
  }

  @Test
  public void aTypecheckedModuleHasAnAnswer() {
    addModule("Foo", CLASS_MODULE);
    addModule("Main", "\\import Foo\n\n\\func p (a : A Nat) : Nat => 0");
    typecheck();
    assertNotNull(server.getNamespaceCommandUsage(moduleLoc("Main")));
  }

  @Test
  public void anUnknownModuleHasNoAnswer() {
    addModule("Main", "\\func p : Nat => 1");
    typecheck();
    assertNull(server.getNamespaceCommandUsage(moduleLoc("Nowhere")));
  }

  // ------------------------------------------------------------------ instances keep their import

  /**
   * Nothing in Main names anything from Bar; the only thing that needs it is the instance the
   * typechecker picked for {@code t}. Resolution alone calls the import superfluous.
   */
  @Test
  public void anImportNeededOnlyForAnInstanceIsKept() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\func p : Nat => t");
    assertEquals("\\import Bar", unusedByResolution("Main"));
    assertEquals("", unused("Main"));
  }

  /** An instance that is merely available is not a use. */
  @Test
  public void anImportOfAnInstanceThatIsNotPickedIsReported() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\func p (a : A Nat) : Nat => 0");
    assertEquals("\\import Bar", unused("Main"));
  }

  @Test
  public void onlyTheNameOfAnExplicitImportThatSuppliesTheInstanceIsKept() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", "\\import Foo\n\n\\instance nat : A Nat 1\n\n\\func unrelated => 7");
    addModule("Main", "\\import Foo (A, t)\n\\import Bar (nat, unrelated)\n\n\\func p : Nat => t");
    assertEquals("\\import Bar (unrelated), \\import Foo (A)", unused("Main"));
  }

  @Test
  public void aRenamedImportOfAnInstanceIsKept() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar (nat \\as n)\n\n\\func p : Nat => t");
    assertEquals("", unused("Main"));
  }

  @Test
  public void anOpenNeededOnlyForAnInstanceIsKept() {
    addModule("Foo", CLASS_MODULE);
    addModule("Main", """
        \\import Foo

        \\module M \\where {
          \\instance nat : A Nat 1
        }

        \\open M

        \\func p : Nat => t
        """);
    assertEquals("", unused("Main"));
  }

  /** An instance declared in the module itself reaches the pool without any command. */
  @Test
  public void anInstanceDeclaredInTheModuleChargesNothing() {
    addModule("Foo", CLASS_MODULE);
    addModule("Main", """
        \\import Foo

        \\instance nat : A Nat 1

        \\func p : Nat => t
        """);
    assertEquals("", unused("Main"));
  }

  /** Of two commands supplying the same instance, the one the pool search reaches is kept. */
  @Test
  public void onlyOneOfTwoCommandsSupplyingTheSameInstanceIsKept() {
    addModule("Foo", CLASS_MODULE);
    addModule("Main", """
        \\import Foo

        \\module M \\where {
          \\instance nat : A Nat 1
        }

        \\open M
        \\open M

        \\func p : Nat => t
        """);
    assertEquals("\\open M", unused("Main"));
  }

  // ------------------------------------------------------------------ what a lemma hides

  /**
   * A {@code \lemma} loses its body, so the instances it used are recorded on it instead. Nothing
   * in Main names anything from Bar; the only thing needing it is the instance the proof used.
   */
  @Test
  public void anImportNeededOnlyForAnInstanceInALemmaIsKept() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", LEMMA_MAIN);
    assertEquals("\\import Bar", unusedByResolution("Main"));
    NamespaceCommandUsage usage = typecheckedUsage("Main");
    assertEquals("[]", usage.getDefinitionsHidingInstances().toString());
    assertEquals("", render(usage, server.getRawGroup(moduleLoc("Main"))));
  }

  /** The answer does not depend on whether the server was built to drop lemma bodies. */
  @Test
  public void aLemmaReadsTheSameWhicheverWayTheServerIsBuilt() {
    server = createServer(true);
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", LEMMA_MAIN);
    NamespaceCommandUsage usage = typecheckedUsage("Main");
    assertNull("the body is expected to be gone", lemmaBody("Main"));
    assertEquals("[]", usage.getDefinitionsHidingInstances().toString());
    assertEquals("", render(usage, server.getRawGroup(moduleLoc("Main"))));
  }

  /**
   * The point of writing the instances to the binary: a lemma read back from a cache has no body
   * to read them off, and has to answer the same as the one that was just typechecked.
   */
  @Test
  public void theInstancesOfALemmaSurviveABinaryRoundTrip() throws IOException {
    server = createServer(true);
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", LEMMA_MAIN);
    typecheck();
    assertEquals("[Bar:nat]", instanceNames(definitionOf(server, "Main", "p")));

    FunctionDefinition lemma = definitionOf(roundTrip(), "Main", "p");
    assertNull("a deserialized lemma has no body", lemma.getReallyActualBody());
    assertEquals("[Bar:nat]", instanceNames(lemma));
  }

  private GZIPStreamBinarySource binarySource(ModuleLocation module) {
    return new GZIPStreamBinarySource(new FileBinarySource(tempDir, module));
  }

  private static FunctionDefinition definitionOf(ArendServer srv, String module, String name) {
    ConcreteGroup group = srv.getRawGroup(moduleLoc(module));
    assertNotNull(group);
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && statement.group().referable().getRefName().equals(name)) {
        return (FunctionDefinition) ((TCDefReferable) statement.group().referable()).getTypechecked();
      }
    }
    throw new AssertionError("no definition " + name + " in " + module);
  }

  /** Persists every module and reads them back into a fresh server. */
  private ArendServer roundTrip() throws IOException {
    tempDir = Files.createTempDirectory("arend-instance-test");
    for (ModuleLocation module : allModules()) {
      assertTrue("failed to persist " + module, binarySource(module).persist(server, new ListErrorReporter()));
    }
    ArendServer reloaded = createServer(true);
    for (String name : List.of("Foo", "Bar", "Main")) {
      ListErrorReporter loadErrors = new ListErrorReporter();
      assertNotNull("failed to load " + name, binarySource(name).load(reloaded, loadErrors));
      assertEquals("load errors for " + name, List.of(), loadErrors.getErrorList());
    }
    return reloaded;
  }

  private GZIPStreamBinarySource binarySource(String name) {
    return binarySource(moduleLoc(name));
  }

  private static String instanceNames(Definition definition) {
    Set<TCDefReferable> instances = definition.getUsedInstances();
    assertNotNull("the definition carries no record of its instances", instances);
    return instances.stream().map(ref -> ref.getRefFullName().toString()).sorted().toList().toString();
  }

  private Body lemmaBody(String name) {
    return definitionOf(server, name, "p").getReallyActualBody();
  }

  private NamespaceCommandUsage typecheckedUsage(String name) {
    typecheck();
    NamespaceCommandUsage usage = server.getNamespaceCommandUsage(moduleLoc(name));
    assertNotNull(usage);
    return usage;
  }

  /**
   * An {@code \axiom} is core kind LEMMA too, so it takes the same path. It has no body at all,
   * which means the record covers its parameters and result type -- everything there is -- rather
   * than standing in for something lost.
   */
  @Test
  public void anAxiomCarriesTheInstancesOfItsType() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\axiom ax : t = 1");
    assertEquals("\\import Bar", unusedByResolution("Main"));
    NamespaceCommandUsage usage = typecheckedUsage("Main");

    FunctionDefinition axiom = definitionOf(server, "Main", "ax");
    assertTrue("expected an axiom", axiom.isAxiom());
    assertNull("an axiom has no body to read instances off", axiom.getReallyActualBody());
    assertEquals("[Bar:nat]", instanceNames(axiom));

    assertEquals("[]", usage.getDefinitionsHidingInstances().toString());
    assertEquals("", render(usage, server.getRawGroup(moduleLoc("Main"))));
  }

  @Test
  public void theInstancesOfAnAxiomSurviveABinaryRoundTrip() throws IOException {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\axiom ax : t = 1");
    typecheck();
    assertEquals("[Bar:nat]", instanceNames(definitionOf(server, "Main", "ax")));

    ArendServer reloaded = roundTrip();
    assertEquals("[Bar:nat]", instanceNames(definitionOf(reloaded, "Main", "ax")));
  }

  /** An ordinary definition keeps its body, so nothing is hidden. */
  @Test
  public void anOrdinaryDefinitionHidesNothing() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\func p : Nat => t");
    typecheck();
    NamespaceCommandUsage usage = server.getNamespaceCommandUsage(moduleLoc("Main"));
    assertNotNull(usage);
    assertEquals("[]", usage.getDefinitionsHidingInstances().toString());
  }
}
