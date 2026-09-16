package org.arend.server;

import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.library.MemoryLibrary;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class ImportAnalyzerTest extends TypeCheckingTestCase {
  private static final ModuleLocation FOO = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath("Foo"));
  private static final ModuleLocation BAR = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath("Bar"));

  private long myStamp = 0;
  private final List<ModuleLocation> myModules = new ArrayList<>();

  private void update(ModuleLocation module, String text) {
    ListErrorReporter errorReporter = new ListErrorReporter();
    ArendParser.StatementsContext tree = CommonCliRepl.createParser(text, module, errorReporter).statements();
    ConcreteGroup group = new BuildVisitor(module, errorReporter).visitStatements(tree);
    assertThat(errorReporter.getErrorList(), containsErrors(0));
    if (!myModules.contains(module)) myModules.add(module);
    server.updateModule(++myStamp, module, () -> group);
  }

  private void update(String text) {
    update(MODULE, text);
  }

  private void resolve() {
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertThat(getAllErrors(), containsErrors(0));
  }

  private void resolveAndTypecheck() {
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    server.getCheckerFor(myModules).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertThat(getAllErrors(), containsErrors(0));
  }

  private List<String> findings() {
    return render(new ImportAnalyzer(server).analyze(MODULE));
  }

  private List<String> displayFindings() {
    List<ImportFinding> findings = new ImportAnalyzer(server).findUnused(MODULE, true);
    return findings == null ? Collections.emptyList() : render(findings);
  }

  private static List<String> render(List<ImportFinding> findings) {
    List<String> result = new ArrayList<>();
    for (ImportFinding finding : findings) {
      result.add(finding.kind() + " " + finding.name());
    }
    return result;
  }

  private void assertFindings(String... expected) {
    assertEquals(List.of(expected), findings());
  }

  private void assertNoFindings() {
    assertEquals(Collections.emptyList(), findings());
  }

  @Test
  public void anImportNothingNeedsIsReported() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo

      \\func f => 0
      """);
    resolve();

    assertFindings("UNUSED_IMPORT Foo");
  }

  @Test
  public void aUsedImportIsNotReported() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo)

      \\func f => foo
      """);
    resolve();

    assertNoFindings();
  }

  /** A qualified reference is what makes the name of the imported module itself used. */
  @Test
  public void anImportUsedThroughAQualifiedNameIsNotReported() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo

      \\func f => Foo.foo
      """);
    resolve();

    assertNoFindings();
  }

  @Test
  public void oneUnusedNameInsideALiveImport() {
    update(FOO, """
      \\func used => 0
      \\func unused => 1
      """);
    update("""
      \\import Foo (used, unused)

      \\func f => used
      """);
    resolve();

    assertFindings("UNUSED_NAME unused");
  }

  @Test
  public void anUnusedNewNameIsReportedAsAnAlias() {
    update(FOO, """
      \\func foo => 0
      \\func bar => 1
      """);
    update("""
      \\import Foo (foo \\as foo', bar)

      \\func f => bar
      """);
    resolve();

    assertFindings("UNUSED_ALIAS foo'");
  }

  @Test
  public void aUsedNewNameIsNotReported() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo \\as foo')

      \\func f => foo'
      """);
    resolve();

    assertNoFindings();
  }

  /** An \open whose names all come from elsewhere is redundant even though its module resolves. */
  @Test
  public void ownDefinitionShadowsAnOpen() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (M)
      \\open M

      \\func foo => 1
      \\func f => foo
      """);
    resolve();

    assertFindings("UNUSED_IMPORT Foo", "UNUSED_OPEN M");
  }

  @Test
  public void anImportKeptAliveOnlyByADeadOpenIsReported() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (M)
      \\open M

      \\func f => 0
      """);
    resolve();

    assertFindings("UNUSED_IMPORT Foo", "UNUSED_OPEN M");
  }

  @Test
  public void aLiveOpenKeepsTheImportThatNamesItsModule() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (M)
      \\open M

      \\func f => foo
      """);
    resolve();

    assertNoFindings();
  }

  @Test
  public void anOpenInsideAWhereBlock() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (M)

      \\func f => g
        \\where {
          \\open M

          \\func g => 0
        }
      """);
    resolve();

    assertFindings("UNUSED_IMPORT Foo", "UNUSED_OPEN M");
  }

  @Test
  public void anOpenIsKeptByTheInstanceItBrings() {
    update(FOO, """
      \\module M \\where {
        \\class C (X : \\Type) {
          | op : X -> X
        }

        \\instance i : C Nat
          | op => \\lam x => x
      }
      """);
    update("""
      \\import Foo (M)
      \\open M (C, i, op)

      \\func f => op 0
      """);
    resolveAndTypecheck();

    assertNoFindings();
  }

  @Test
  public void aCommandProvidingAnInstanceIsKeptWhileInstancesAreUnknown() {
    update(FOO, """
      \\module M \\where {
        \\class C (X : \\Type) {
          | op : X -> X
        }

        \\instance i : C Nat
          | op => \\lam x => x
      }
      """);
    update("""
      \\import Foo (M)
      \\open M (C, i, op)

      \\func f => 0
      """);
    resolve();

    assertNoFindings();
    assertEquals(Collections.emptyList(), displayFindings());
  }

  @Test
  public void anOpenIsKeptByAProtectedNameUsedAsAQualifier() {
    update(FOO, """
      \\module M \\where {
        \\protected \\func prot => 0
          \\where \\func inner => 1
      }
      """);
    update("""
      \\import Foo
      \\open M

      \\func f => prot.inner
      """);
    resolve();

    assertNoFindings();
  }

  @Test
  public void anOpenProvidingOnlyAnUnusedProtectedNameIsReported() {
    update(FOO, """
      \\module M \\where {
        \\protected \\func prot => 0
          \\where \\func inner => 1
      }
      """);
    update("""
      \\import Foo
      \\open M

      \\func f => 0
      """);
    resolve();

    assertFindings("UNUSED_IMPORT Foo", "UNUSED_OPEN M");
  }

  @Test
  public void aCommandWithADynamicRenamingIsNeverReported() {
    update(FOO, """
      \\record R {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (R)
      \\open R (.foo)

      \\func f (r : R) => r.foo
      """);
    resolve();

    assertNoFindings();
  }

  private void updateFieldModules() {
    update(FOO, "\\record P { | fld : Nat }");
    update(BAR, """
      \\import Foo

      \\record Q \\extends P { | other : Nat }
      """);
  }

  @Test
  public void aFieldOfATypedReceiverNeedsNoImport() {
    updateFieldModules();
    update("""
      \\import Foo
      \\import Bar

      \\func f => \\lam (q : Q) => q.fld
      """);
    resolveAndTypecheck();

    assertFindings("UNUSED_IMPORT Foo");
  }

  @Test
  public void aGuessedFieldNameDoesNotKeepItsImportWhenTheReceiverTypeIsKnown() {
    updateFieldModules();
    update("""
      \\import Foo
      \\import Bar

      \\func use (g : \\Pi (q : Q) -> Nat) => g
      \\func f => use (\\lam q => q.fld)
      """);
    resolveAndTypecheck();

    assertFindings("UNUSED_IMPORT Foo");
  }

  @Test
  public void aGuessedDynamicFunctionKeepsItsImportEvenWhenTheReceiverTypeIsKnown() {
    update(FOO, """
      \\record P {
        | fld : Nat

        \\func twice : Nat => 2 Nat.* fld
      }
      """);
    update(BAR, """
      \\import Foo

      \\record Q \\extends P { | other : Nat }
      """);
    update("""
      \\import Foo
      \\import Bar

      \\func use (g : \\Pi (q : Q) -> Nat) => g
      \\func f => use (\\lam q => q.twice)
      """);
    resolveAndTypecheck();

    assertNoFindings();
  }

  @Test
  public void aGuessedFieldNameKeepsItsImportWhenItFixesTheReceiverType() {
    updateFieldModules();
    update("""
      \\import Foo

      \\func f => \\lam q => q.fld
      """);
    resolveAndTypecheck();

    assertNoFindings();
  }

  @Test
  public void aGuessedFieldNameKeepsItsImportWhileTheTypecheckerHasNotSpoken() {
    updateFieldModules();
    update("""
      \\import Foo
      \\import Bar

      \\func use (g : \\Pi (q : Q) -> Nat) => g
      \\func f => use (\\lam q => q.fld)
      """);
    resolve();

    assertNoFindings();
  }

  @Test
  public void nothingIsReportedForAModuleWithErrors() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo

      \\func f => unknownName
      """);
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    assertNoFindings();
  }

  @Test
  public void aModuleWithErrorsIsStillGreyed() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo

      \\func f => unknownName
      """);
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    assertNoFindings();
    assertEquals(List.of("UNUSED_IMPORT Foo"), displayFindings());
  }

  @Test
  public void anInstanceProvidingCommandIsGreyedWhileTheModuleHasErrors() {
    update(FOO, """
      \\module M \\where {
        \\class C (X : \\Type) {
          | op : X -> X
        }

        \\instance i : C Nat
          | op => \\lam x => x
      }
      """);
    update("""
      \\import Foo (M)
      \\open M (C, i, op)

      \\func f => unknownName
      """);
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    assertNoFindings();
    assertEquals(List.of("UNUSED_IMPORT Foo", "UNUSED_OPEN M"), displayFindings());
  }

  @Test
  public void aGuessedFieldKeepsItsCommandWhileTheModuleHasErrors() {
    update(FOO, """
      \\class C (X : \\Type) {
        | op : X -> X
      }
      """);
    update("""
      \\import Foo

      \\func g => \\lam x => x.op

      \\func f => unknownName
      """);
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    assertNoFindings();
    assertEquals(Collections.emptyList(), displayFindings());
  }

  @Test
  public void nothingIsReportedBeforeTheModuleIsResolved() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo

      \\func f => 0
      """);
    assertNoFindings();

    resolve();
    assertFindings("UNUSED_IMPORT Foo");

    update("""
      \\import Foo

      \\func f => 1
      """);
    assertNoFindings();
  }

  @Test
  public void anImportShadowedByAnEarlierOneIsReported() {
    update(FOO, """
      \\func foo => 0
      \\func bar => 1
      """);
    update("""
      \\import Foo
      \\import Foo (foo, bar)

      \\func f => foo
      """);
    resolve();

    assertFindings("UNUSED_IMPORT Foo");
    assertEquals(2, server.getRawGroup(MODULE).statements().stream().filter(st -> st.command() != null).count());
  }

  @Test
  public void aDeadOpenDoesNotKeepTheImportOfItsQualifier() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo
      \\open Foo (M)
      \\open M

      \\func f => 0
      """);
    resolve();

    assertFindings("UNUSED_IMPORT Foo", "UNUSED_OPEN Foo", "UNUSED_OPEN M");
  }

  @Test
  public void theUnknownInstancesOfAModuleDoNotKeepCommands() {
    update(FOO, """
      \\class C (X : \\Type) {
        | op : X -> X
      }

      \\instance i : C Nat
        | op => \\lam x => x
      """);
    update("""
      \\import Foo (C, i, op)

      \\module M \\where {
        \\func g => 0
      }
      """);
    resolveAndTypecheck();

    assertFindings("UNUSED_IMPORT Foo");
  }

  @Test
  public void anInheritedFieldDoesNotNeedTheImportThatAlsoProvidesIt() {
    update(FOO, """
      \\record P (Ob : \\Type) {
        | Hom : Ob -> Ob -> \\Type
      }
      """);
    update(BAR, """
      \\import Foo

      \\record Q \\extends P
      """);
    update("""
      \\import Foo
      \\import Bar

      \\record R \\extends Q {
        \\func f {X Y : Ob} (h : Hom X Y) => h
      }
      """);
    resolveAndTypecheck();

    assertFindings("UNUSED_IMPORT Foo");
  }

  @Test
  public void aNamespaceOfAnInheritedNameKeepsItsOpen() {
    update(FOO, """
      \\record P {
        \\func twice : Nat => 0
          \\where \\func helper : Nat => 1
      }
      """);
    update("""
      \\import Foo (P)
      \\open P (twice)

      \\record R \\extends P {
        \\func f => twice.helper
      }
      """);
    resolveAndTypecheck();

    assertNoFindings();
  }
}
