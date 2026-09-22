package org.arend.server.imports;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.library.MemoryLibrary;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * What {@link ImportUsageTracer} reports for the shapes that
 * {@code org.arend.intention.OptimizeImportsTest} covers on the IDE side, minus the ones that turn
 * on instances the typechecker picked -- those are not visible to a resolver and are not the
 * tracer's job.
 */
public class ImportUsageTracerTest {
  private static final String LIB_NAME = "test_library";

  private ArendServer server;
  private long modStamp = 1;

  @Before
  public void setUp() {
    server = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server.updateLibrary(MemoryLibrary.INSTANCE, DummyErrorReporter.INSTANCE);
  }

  private static ModuleLocation moduleLoc(String name) {
    return new ModuleLocation(LIB_NAME, ModuleLocation.LocationKind.SOURCE, new ModulePath(name.split("\\.")));
  }

  private void addModule(String name, String text) {
    ModuleLocation module = moduleLoc(name);
    ListErrorReporter errorReporter = new ListErrorReporter();
    ArendParser.StatementsContext tree = CommonCliRepl.createParser(text, module, errorReporter).statements();
    assertEquals("parse errors in " + name, List.of(), errorReporter.getErrorList());
    ConcreteGroup group = new BuildVisitor(module, errorReporter).visitStatements(tree);
    assertNotNull("failed to parse " + name, group);
    server.updateModule(modStamp++, module, () -> group);
  }

  /**
   * Resolves everything and reports the parts of the namespace commands of {@param name} that
   * nothing needed, sorted so that a failure reads the same way every run.
   */
  private String unused(String name) {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLibraryName().equals(LIB_NAME)) modules.add(module);
    }
    server.getCheckerFor(modules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    // warnings are fine: shadowing one import with another is a shape worth tracing, and it warns
    assertEquals("resolver errors", List.of(), server.getErrorMap().values().stream().flatMap(List::stream)
      .filter(error -> error.level.ordinal() >= GeneralError.Level.ERROR.ordinal()).toList());

    ModuleLocation module = moduleLoc(name);
    NamespaceCommandUsage usage = ImportUsageTracer.trace((ArendServerImpl) server, module);
    assertNotNull("module " + name + " is not resolved", usage);
    ConcreteGroup group = server.getRawGroup(module);
    assertNotNull(group);

    TreeSet<String> result = new TreeSet<>();
    for (NamespaceCommandUsage.Part part : usage.getUnused(ImportUsageTracer.collectCommands(group))) {
      result.add(part.toString());
    }
    return String.join(", ", result);
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

  @Test
  public void anImportUsedOnlyByADefinitionInAWhereBlockIsKept() {
    addModule("Foo", "\\data Bar");
    addModule("Main", "\\import Foo\n\n\\func f => 1 \\where \\func g : Bar => {?}");
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
    addModule("Foo", "\\class R (rr : Nat)");
    addModule("Main", "\\import Foo (R)\n\\open R\n\n\\func f {r : R} : Nat => rr");
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
}
