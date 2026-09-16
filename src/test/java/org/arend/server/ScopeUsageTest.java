package org.arend.server;

import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.library.MemoryLibrary;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public class ScopeUsageTest extends TypeCheckingTestCase {
  private static final ModuleLocation FOO = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath("Foo"));
  private static final LongName FILE = new LongName(Collections.emptyList());

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

  private Set<String> instances(LongName longName) {
    Set<TCDefReferable> instances = usage(longName).instances();
    assertNotNull("The instances of " + longName + " are unknown", instances);
    Set<String> result = new HashSet<>();
    for (TCDefReferable instance : instances) result.add(instance.getRefLongName().toString());
    return result;
  }

  private ScopeUsage usage(LongName longName) {
    return server.getScopeUsages().getUsage(MODULE, longName);
  }

  private Set<String> used(LongName longName) {
    Set<String> result = new HashSet<>();
    for (ScopeUsage.UsedName name : usage(longName).names()) {
      if (name.command() != null) result.add(name.command().toString() + " -> " + name.name());
    }
    return result;
  }

  private ConcreteNamespaceCommand command(String prefix) {
    ConcreteGroup group = server.getRawGroup(MODULE);
    assertNotNull(group);
    for (ConcreteStatement statement : group.statements()) {
      ConcreteNamespaceCommand command = statement.command();
      if (command != null && command.toString().startsWith(prefix)) return command;
    }
    throw new AssertionError("No command starting with '" + prefix + "'");
  }

  @Test
  public void aNameIsRecordedWithItsCommand() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo)

      \\func f => foo
      """);
    resolve();

    assertEquals(Set.of("\\import Foo (foo) -> foo"), used(new LongName("f")));
    assertEquals(Set.of(), used(FILE));
    assertEquals(Set.of(command("\\import Foo")),
      Set.of(usage(new LongName("f")).names().iterator().next().command()));
  }

  @Test
  public void anUnusedCommandIsRecordedNowhere() {
    update(FOO, """
      \\func used => 0
      \\func unused => 1
      """);
    update("""
      \\import Foo (used, unused)

      \\func f => used
      """);
    resolve();

    assertEquals(Set.of("\\import Foo (used, unused) -> used"), used(new LongName("f")));
  }

  @Test
  public void onlyTheQualifierIsRecorded() {
    update(FOO, """
      \\module M \\where {
        \\func bar => 0
      }
      """);
    update("""
      \\import Foo (M)

      \\func f => M.bar
      """);
    resolve();

    assertEquals(Set.of("\\import Foo (M) -> M"), used(new LongName("f")));
  }

  @Test
  public void aRenamedNameIsRecordedAsWritten() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo \\as bar)

      \\func f => bar
      """);
    resolve();

    assertEquals(Set.of("\\import Foo (foo \\as bar) -> bar"), used(new LongName("f")));
  }

  @Test
  public void usagesGoToTheInnermostGroup() {
    update(FOO, """
      \\func foo => 0
      \\func bar => 1
      """);
    update("""
      \\import Foo (foo, bar)

      \\func f => g
        \\where \\func g => foo

      \\func h => bar
      """);
    resolve();

    assertEquals(Set.of(), used(new LongName("f")));
    assertEquals(Set.of("\\import Foo (foo, bar) -> foo"), used(new LongName("f", "g")));
    assertEquals(Set.of("\\import Foo (foo, bar) -> bar"), used(new LongName("h")));
  }

  @Test
  public void theModuleOfACommandIsNotRecorded() {
    update(FOO, """
      \\module M \\where {
        \\func bar => 0
      }
      """);
    update("""
      \\import Foo (M)
      \\open M

      \\func f => bar
      """);
    resolve();

    assertEquals(Set.of(), used(FILE));
    assertEquals(Set.of("\\open M -> bar"), used(new LongName("f")));
  }

  @Test
  public void theNameOfAnImportedModuleIsUnknown() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo

      \\func f => Foo.foo
      """);
    resolve();

    assertEquals(Set.of(), used(new LongName("f")));
    assertEquals(Set.of("Foo"), usage(new LongName("f")).unknownNames());
  }

  @Test
  public void aProtectedNameUsedAsAQualifierIsUnknown() {
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

    assertEquals(Set.of(), used(new LongName("f")));
    assertEquals(Set.of("prot"), usage(new LongName("f")).unknownNames());
  }

  @Test
  public void everyGroupIsRecorded() {
    update("""
      \\func f => 0

      \\module M \\where {
        \\func g => 1
      }
      """);
    resolve();

    assertEquals(Set.of(FILE, new LongName("f"), new LongName("M"), new LongName("M", "g")),
      server.getScopeUsages().getGroupNames(MODULE));
  }

  @Test
  public void reResolvingReplacesTheNames() {
    update(FOO, """
      \\func foo => 0
      \\func bar => 1
      """);
    update("""
      \\import Foo (foo, bar)

      \\func f => foo
      """);
    resolve();
    assertEquals(Set.of("\\import Foo (foo, bar) -> foo"), used(new LongName("f")));

    update("""
      \\import Foo (foo, bar)

      \\func f => bar
      """);
    resolve();
    assertEquals(Set.of("\\import Foo (foo, bar) -> bar"), used(new LongName("f")));
  }

  @Test
  public void aRemovedGroupLosesItsUsages() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo)

      \\func f => foo
      \\func g => foo
      """);
    resolve();
    assertTrue(server.getScopeUsages().getGroupNames(MODULE).contains(new LongName("g")));

    update("""
      \\import Foo (foo)

      \\func f => foo
      """);
    resolve();
    assertFalse(server.getScopeUsages().getGroupNames(MODULE).contains(new LongName("g")));
  }

  @Test
  public void usedInstancesAreRecorded() {
    update("""
      \\class C (X : \\Type) {
        | op : X -> X
      }

      \\instance i : C Nat
        | op => \\lam x => x

      \\func f => op 0
      \\func g => 0
      """);
    resolveAndTypecheck();

    assertEquals(Set.of("i"), instances(new LongName("f")));
    // Typechecked and needed no instance, which is not the same as "unknown".
    assertEquals(Set.of(), instances(new LongName("g")));
    assertTrue(usage(new LongName("g")).areInstancesKnown());
  }

  @Test
  public void anInstanceOfAnOpenedModuleIsRecorded() {
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
      \\open M

      \\func f => op 0
      """);
    resolveAndTypecheck();

    assertEquals(Set.of("M.i"), instances(new LongName("f")));
  }

  @Test
  public void instancesAreUnknownWithoutTypechecking() {
    update("\\func f => 0");
    resolve();

    assertFalse(usage(new LongName("f")).areInstancesKnown());
    assertNull(usage(new LongName("f")).instances());
  }

  @Test
  public void aModuleGroupHasNoInstances() {
    update("""
      \\module M \\where {
        \\func g => 0
      }
      """);
    resolveAndTypecheck();

    assertFalse(usage(new LongName("M")).areInstancesKnown());
    assertTrue(usage(new LongName("M", "g")).areInstancesKnown());
  }

  @Test
  public void reResolvingKeepsTheInstancesOfUnchangedDefinitions() {
    update("""
      \\class C (X : \\Type) {
        | op : X -> X
      }

      \\instance i : C Nat
        | op => \\lam x => x

      \\func f => op 0
      \\func h => 0
      """);
    resolveAndTypecheck();
    assertEquals(Set.of("i"), instances(new LongName("f")));

    update("""
      \\class C (X : \\Type) {
        | op : X -> X
      }

      \\instance i : C Nat
        | op => \\lam x => x

      \\func f => op 0
      \\func h => 1
      """);
    resolveAndTypecheck();
    assertEquals(Set.of("i"), instances(new LongName("f")));
  }

  @Test
  public void usagesAreDroppedWithTheModule() {
    update("\\func f => 0");
    resolve();
    assertTrue(server.getScopeUsages().isCollected(MODULE));

    server.removeModule(MODULE);
    assertFalse(server.getScopeUsages().isCollected(MODULE));
    assertEquals(ScopeUsage.EMPTY, usage(new LongName("f")));
  }
}
