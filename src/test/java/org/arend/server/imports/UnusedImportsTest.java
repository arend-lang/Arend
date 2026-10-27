package org.arend.server.imports;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
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
import org.arend.core.expr.Expression;
import org.arend.naming.reference.TCDefReferable;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.visitor.ArendCheckerFactory;
import org.arend.typechecking.visitor.FindDefCallVisitor;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class UnusedImportsTest {
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

  private static String render(ImportUsageData usage, ConcreteGroup group) {
    // a list rather than a set: two identical commands are two results, and a test that collapsed
    // them could not tell "the second one is superfluous" from "both are"
    List<String> result = new ArrayList<>();
    for (ImportUsageData.Part part : usage.getUnused(ImportUsageTracer.collectCommands(group))) {
      result.add(part.toString());
    }
    Collections.sort(result);
    return String.join(", ", result);
  }

  /** What the resolution half alone reports, which ignores instances. */
  private String unusedByResolution(String name) {
    resolve();
    ImportUsageData usage = ImportUsageTracer.traceResolution((ArendServerImpl) server, moduleLoc(name));
    assertNotNull(usage);
    return render(usage, server.getRawGroup(moduleLoc(name)));
  }

  /** What the whole answer is, once the module has been typechecked. */
  private String unused(String name) {
    typecheck();
    ImportUsageData usage = server.getNamespaceCommandUsage(moduleLoc(name));
    assertNotNull("no usage for " + name, usage);
    return render(usage, server.getRawGroup(moduleLoc(name)));
  }

  // ------------------------------------------------------------------ plain imports

  @Test
  public void anImportNothingRefersToIsUnused() {
    addModule("Foo", "\\func f => 1");
    addModule("Main", "\\import Foo\n\n\\func g => 2");
    assertEquals("\\import Foo", unused("Main"));
  }

  @Test
  public void anImportThatBringsInAUsedNameIsKept() {
    addModule("Foo", "\\func f => 1");
    addModule("Main", "\\import Foo\n\n\\func g => f");
    assertEquals("", unused("Main"));
  }

  @Test
  public void onlyTheNamesOfAnExplicitImportThatAreUnusedAreReported() {
    addModule("Foo", "\\func f => 1\n\\func g => 2\n\\func h => 3");
    addModule("Main", "\\import Foo (f, g, h)\n\n\\func r => f Nat.+ h");
    assertEquals("\\import Foo (g)", unused("Main"));
  }

  /** When a command is needed for nothing at all, it is reported once, not once per renaming. */
  @Test
  public void anExplicitImportWhoseNamesAreAllUnusedIsReportedAsAWhole() {
    addModule("Foo", "\\func f => 1\n\\func g => 2");
    addModule("Main", "\\import Foo (f, g)\n\n\\func r => 3");
    assertEquals("\\import Foo (f, g)", unused("Main"));
  }

  /** A using-command keeps bringing in the rest of the module, so only the renaming is reported. */
  @Test
  public void aUsingCommandSurvivesTheLossOfAllItsRenamings() {
    addModule("Foo", "\\func f => 1\n\\func g => 2");
    addModule("Main", "\\import Foo \\using (f \\as ff)\n\n\\func r => g");
    assertEquals("\\import Foo (f \\as ff)", unused("Main"));
  }

  @Test
  public void anOpenInAWhereBlockIsChargedByTheDefinitionItServes() {
    addModule("Main", """
        \\module M \\where { \\func f : Nat => 1 }

        \\func g : Nat => f
          \\where \\open M
        """);
    assertEquals("", unused("Main"));
  }

  @Test
  public void anUnusedOpenInAWhereBlockIsReported() {
    addModule("Main", """
        \\module M \\where { \\func f : Nat => 1 }

        \\func g : Nat => 1
          \\where \\open M
        """);
    assertEquals("\\open M", unused("Main"));
  }

  @Test
  public void aRenamedImportIsKeptWhenTheNewNameIsUsed() {
    addModule("Foo", "\\func f => 1");
    addModule("Main", "\\import Foo (f \\as g)\n\n\\func h => g");
    assertEquals("", unused("Main"));
  }

  @Test
  public void aRenamedImportIsReportedWhenOnlyTheOldNameWouldMatch() {
    addModule("Foo", "\\func f => 1\n\\func g => 2");
    addModule("Main", "\\import Foo (f \\as ff, g)\n\n\\func h => g");
    assertEquals("\\import Foo (f \\as ff)", unused("Main"));
  }

  @Test
  public void hidingLeavesTheRestOfTheImportInPlace() {
    addModule("Foo", "\\func f => 1\n\\func g => 2");
    addModule("Main", "\\import Foo \\hiding (f)\n\n\\func h => g");
    assertEquals("", unused("Main"));
  }

  // ------------------------------------------------------------------ qualified references

  /** {@code Foo.f} needs the import, because only an import puts {@code Foo} in the file's scope. */
  @Test
  public void aQualifiedReferenceKeepsTheImportItGoesThrough() {
    addModule("Foo", "\\func f => 1");
    addModule("Main", "\\import Foo\n\n\\func g => Foo.f");
    assertEquals("", unused("Main"));
  }

  @Test
  public void aQualifiedReferenceIsChargedToTheLongestImportItGoesThrough() {
    addModule("Bar.Baz", "\\func foo => 0");
    addModule("Bar.Baz.Qux", "\\func bar => 1");
    addModule("Main", "\\import Bar.Baz\n\\import Bar.Baz.Qux\n\n\\func g => Bar.Baz.Qux.bar");
    assertEquals("\\import Bar.Baz", unused("Main"));
  }

  // ------------------------------------------------------------------ shadowing

  /** Two commands bringing in the same name: the first one binds it, the second never answers. */
  @Test
  public void theSecondOfTwoIdenticalOpensIsUnused() {
    addModule("Main", "\\module M \\where { \\func f => 1 }\n\n\\open M\n\\open M\n\n\\func g => f");
    assertEquals("\\open M", unused("Main"));
  }

  @Test
  public void anOpenShadowedByALocalDefinitionIsUnused() {
    addModule("Main", "\\module M \\where { \\func f => 1 }\n\n\\open M\n\n\\func f => 2\n\n\\func g => f");
    assertEquals("\\open M", unused("Main"));
  }

  @Test
  public void anOpenIsKeptWhenItIsTheOneThatWinsTheName() {
    addModule("A", "\\func f => 1");
    addModule("B", "\\func f => 2");
    addModule("Main", "\\import A\n\\import B\n\n\\func g => f");
    assertEquals("\\import B", unused("Main"));
  }

  // ------------------------------------------------------------------ nesting and paths

  /** An {@code \open} whose own path needs another command keeps that command alive. */
  @Test
  public void anOpenKeepsTheCommandItsPathGoesThrough() {
    addModule("Main", """
        \\open Outer (Inner)

        \\module Bar \\where {
          \\open Inner

          \\func bar => foobar
        }

        \\module Outer \\where {
          \\module Inner \\where {
            \\func foobar => 100
          }
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** The mirror image: an unused {@code \open} must not keep its own path alive. */
  @Test
  public void anUnusedOpenDoesNotKeepTheCommandItsPathGoesThrough() {
    addModule("Main", """
        \\open Outer (Inner)

        \\module Bar \\where {
          \\open Inner
        }

        \\module Outer \\where {
          \\module Inner \\where {
            \\func foobar => 100
          }
        }
        """);
    assertEquals("\\open Inner, \\open Outer (Inner)", unused("Main"));
  }

  @Test
  public void anOpenUsedOnlyInsideASubgroupIsKept() {
    addModule("Foo", "\\func f => 1");
    addModule("Main", "\\import Foo\n\n\\func g => 1 \\where \\func h => f");
    assertEquals("", unused("Main"));
  }

  // ------------------------------------------------------------------ aliases, constructors, fields

  /** An alias is a name of its own; opening under it is a use. */
  @Test
  public void openingADefinitionUnderItsAliasIsAUse() {
    addModule("Main", """
        \\module M \\where {
          \\func foo \\alias fu (a : Nat) => a
        }

        \\module M1 \\where {
          \\open M (fu)

          \\func lol => 1 Nat.+ fu 2
        }
        """);
    assertEquals("", unused("Main"));
  }

  @Test
  public void aConstructorUsedInAPatternKeepsItsImport() {
    addModule("Foo", "\\data D | d Nat");
    addModule("Main", "\\import Foo (D, d)\n\n\\func foo (x : D) : Nat \\elim x\n  | d n => 1");
    assertEquals("", unused("Main"));
  }

  @Test
  public void aFieldReferredToOutsideItsClassKeepsItsOpen() {
    // R is classified by T, so the implicit {r : R Nat} is a local instance the field can be
    // resolved against; without the \open, the bare `rr` would not resolve at all
    addModule("Foo", "\\class R (T : \\Type) | rr : T");
    addModule("Main", "\\import Foo (R)\n\\open R\n\n\\func f {r : R Nat} : Nat => rr");
    assertEquals("", unused("Main"));
  }

  /** A field reached through the dynamic scope of the enclosing class needs no command. */
  @Test
  public void aFieldReachedThroughTheEnclosingClassNeedsNoOpen() {
    addModule("Main", """
        \\record R {
          | rr : Nat

          \\func f : Nat => rr
        }

        \\open R

        \\func g : Nat => 1
        """);
    assertEquals("\\open R", unused("Main"));
  }

  /** A definition inside a class body resolves through the merged dynamic scope of that class. */
  @Test
  public void anImportUsedFromADynamicSubgroupIsKept() {
    addModule("Foo", "\\class A { | a : Nat }");
    addModule("Main", """
        \\import Foo

        \\class C {
          \\data D \\where {
            \\func g {x : A} : Nat => 1
          }
        }
        """);
    assertEquals("", unused("Main"));
  }

  // ------------------------------------------------------------------ prelude

  @Test
  public void preludeNeedsNoImport() {
    addModule("Main", "\\func f : Nat => 1");
    assertEquals("", unused("Main"));
  }

  @Test
  public void anExplicitPreludeImportIsChargedLikeAnyOther() {
    addModule("Main", "\\import Prelude (Nat)\n\n\\func f : Nat => 0");
    assertEquals("", unused("Main"));
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
  public void anUnknownModuleHasNoAnswer() {
    addModule("Main", "\\func p : Nat => 1");
    typecheck();
    assertNull(server.getNamespaceCommandUsage(moduleLoc("Nowhere")));
  }

  /**
   * A core that carries no record of its instances cannot be told apart from one that used none,
   * so it gets no answer rather than one that may drop the import of an instance it needs.
   */
  @Test
  public void aCoreWithoutARecordOfItsInstancesHasNoAnswer() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\func p : Nat => t");
    typecheck();
    // the server records every definition it typechecks itself; a binary without the set is the
    // way such a core arises, which this stands in for
    //noinspection DataFlowIssue
    definitionOf(server, "Main", "p").setUsedInstances(null);
    assertNull(server.getNamespaceCommandUsage(moduleLoc("Main")));
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

  /**
   * The instance is needed, yet leaves no call to itself in the core: the ascription is dropped
   * and the implicit arguments of {@code f} are solved from the normalized type, so {@code p}
   * elaborates to {@code f {1} (idp {Nat} {1})}. Reading instances off the core misses it; only
   * recording the pick does not.
   */
  @Test
  public void anInstanceThatLeavesNoTraceInTheCoreKeepsItsImport() {
    String body = "\\func f {n : Nat} (q : n = n) : Nat => n\n\\func p : Nat => f (idp : t = t)";
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n" + body);
    assertEquals("\\import Bar", unusedByResolution("Main"));
    assertEquals("", unused("Main"));

    FunctionDefinition p = definitionOf(server, "Main", "p");
    assertEquals("[Bar:nat]", instanceNames(p));
    FunctionDefinition nat = definitionOf(server, "Bar", "nat");
    assertTrue(p.getReallyActualBody() instanceof Expression);
    assertNull("the core is expected to have lost the call to the instance",
      FindDefCallVisitor.findDefinition((Expression) p.getReallyActualBody(), Set.of(nat)));

    // and the import is really needed: without it, p does not typecheck
    addModule("Main", "\\import Foo\n\n" + body);
    server.getCheckerFor(allModules()).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    List<GeneralError> errors = server.getErrorMap().get(moduleLoc("Main"));
    assertNotNull("expected p to fail without \\import Bar", errors);
    assertFalse("expected p to fail without \\import Bar", errors.isEmpty());
  }

  /**
   * The IDE's expression actions typecheck a renamed copy of the definition through a custom
   * checker, and its not-yet-checked dependencies for good along the way. Those have to be
   * recorded: nothing typechecks them again, and without the record the import of the instance
   * {@code p} picked is lost.
   */
  @Test
  public void aDependencyTypecheckedForACustomCheckerRecordsItsInstances() {
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", "\\import Foo\n\\import Bar\n\n\\func p : Nat => t\n\\func q : Nat => p");
    resolve();
    server.getCheckerFor(allModules()).typecheck(new FullName(moduleLoc("Main"), new LongName("q")), ArendCheckerFactory.DEFAULT,
      new HashMap<>(), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertEquals("[Bar:nat]", instanceNames(definitionOf(server, "Main", "p")));
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
    ImportUsageData usage = typecheckedUsage("Main");
    assertEquals("", render(usage, server.getRawGroup(moduleLoc("Main"))));
  }

  /** The answer does not depend on whether the server was built to drop lemma bodies. */
  @Test
  public void aLemmaReadsTheSameWhicheverWayTheServerIsBuilt() {
    server = createServer(true);
    addModule("Foo", CLASS_MODULE);
    addModule("Bar", INSTANCE_MODULE);
    addModule("Main", LEMMA_MAIN);
    ImportUsageData usage = typecheckedUsage("Main");
    assertNull("the body is expected to be gone", lemmaBody("Main"));
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

  private ImportUsageData typecheckedUsage(String name) {
    typecheck();
    ImportUsageData usage = server.getNamespaceCommandUsage(moduleLoc(name));
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
    ImportUsageData usage = typecheckedUsage("Main");

    FunctionDefinition axiom = definitionOf(server, "Main", "ax");
    assertTrue("expected an axiom", axiom.isAxiom());
    assertNull("an axiom has no body to read instances off", axiom.getReallyActualBody());
    assertEquals("[Bar:nat]", instanceNames(axiom));

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

  // ------------------------------------------- shapes migrated from the IDE OptimizeImportsTest

  /** Everything is in one file and reached by qualifier, so no command is involved at all. */
  @Test
  public void aQualifierInsideTheSameFileNeedsNothing() {
    addModule("Main", """
        \\data Bar \\where {
          \\data R \\where {
            \\func f : Nat => 1
          }
        }

        \\func g => Bar.R.f
        """);
    assertEquals("", unused("Main"));
  }

  /** Prelude's Array is in scope unimported, so a \\new over it charges nothing. */
  @Test
  public void aPreludeRecordUsedInANewExpressionNeedsNothing() {
    addModule("Main", "\\func f => \\new Array { | A => Nat | len => 1 | at (0) => 1 }");
    assertEquals("", unused("Main"));
  }

  /** A field used by a definition inside its own record resolves through the dynamic scope. */
  @Test
  public void aFieldUsedByADynamicDefinitionOfItsOwnRecordNeedsNothing() {
    addModule("Main", """
        \\record R {
          | r : Nat

          \\func rrr : Fin r => {?}
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** Two classes exporting the same name: each use goes through its own receiver, not a command. */
  @Test
  public void aFieldReachedThroughItsReceiverNeedsNoCommand() {
    addModule("Main", """
        \\class A { | n : Nat  \\func f : Nat => n }

        \\class B { | n : Nat  \\func f : Nat => n }

        \\func h {a : A} => a.f
        \\func g {b : B} => b.f
        """);
    assertEquals("", unused("Main"));
  }

  /** A field inherited from a superclass is reached without opening anything. */
  @Test
  public void anInheritedFieldNeedsNoCommand() {
    addModule("Main", "\\class R (rr : Nat)\n\n\\class E \\extends R\n  | ee : Fin rr");
    assertEquals("", unused("Main"));
  }

  /** The \\open is what makes B writable bare in the body of a class that extends A. */
  @Test
  public void anOpenIsKeptWhenItsNameIsUsedInsideAnExtension() {
    addModule("Main", """
        \\open A (B)

        \\class A \\where \\record B

        \\class E \\extends A {
          | f : B
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** The \\open is inside the instance's own \\where and serves its coclause. */
  @Test
  public void anOpenInAnInstanceWhereBlockIsKept() {
    addModule("Main", """
        \\class A (E : \\Type) {
          | + : E -> E -> E
        }

        \\instance a : A Nat
          | + => +
        \\where {
          \\open Nat (+)
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** A qualified \\open of a record nested in a module, from a definition's \\where block. */
  @Test
  public void aQualifiedOpenOfANestedRecordIsKept() {
    addModule("Main", """
        \\module M \\where {
          \\record R
            | field : Nat
        }

        \\func asdzxc {r : M.R} => field
          \\where
            \\open M.R(field)
        """);
    assertEquals("", unused("Main"));
  }

  /** An \\open whose path names a definition rather than a module. */
  @Test
  public void anOpenOfTheWhereBlockOfADefinitionIsKept() {
    addModule("Main", """
        \\module A \\where {
          \\module B \\where {
            \\func f => 1
            \\where {
              \\func g => {?}
            }
            \\open B.f

            \\func h => g
          }
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** The name the command renames to is then used as a qualifier. */
  @Test
  public void aRenamedNameUsedAsAQualifierIsAUse() {
    addModule("Main", """
        \\module M \\where {
          \\func foo \\alias fu (a : Nat) => a \\where
            \\func lol => 101
        }

        \\module M1 \\where {
          \\open M (fu \\as foobar)

          \\func lol => 1 Nat.+ foobar.lol
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** Opening the module being edited, to bring one of its own instances into the pool. */
  @Test
  public void anOpenOfTheOwnModuleForAnInstanceIsKept() {
    addModule("Main", """
        \\class R | n : Nat

        \\func ff {_ : R} => 10

        \\instance G' : R | n => 2

        \\module M \\where {
          \\open Main (G')

          \\func f : Nat => ff
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** The same name reached through a file in one group and a local module in another. */
  @Test
  public void theSameNameFromAFileAndFromALocalModuleKeepsBoth() {
    addModule("Foo", "\\func f => 1");
    addModule("Main", """
        \\import Foo

        \\func g => f
        \\module M \\where { \\func f => 2 }

        \\module N \\where {
          \\open M
          \\func h => f
        }
        """);
    assertEquals("", unused("Main"));
  }

  /** A renamed field opened inside the \\where block of a lemma. */
  @Test
  public void aRenamedFieldOpenedForALemmaIsKept() {
    addModule("Main", """
        \\data Unit | unit

        \\class Op {
          | f : Unit
        }

        \\lemma foo {o : Op} : Unit => f'
        \\where {
          \\open Op(f \\as f')
        }
        """);
    assertEquals("", unused("Main"));
  }
}
