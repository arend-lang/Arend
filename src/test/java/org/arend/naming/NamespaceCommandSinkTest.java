package org.arend.naming;

import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.library.MemoryLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.scope.NamespaceCommandSink;
import org.arend.naming.scope.Scope;
import org.arend.server.ProgressReporter;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public class NamespaceCommandSinkTest extends TypeCheckingTestCase {
  private static final ModuleLocation FOO = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath("Foo"));

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

  private Scope scopeInside(String name) {
    ConcreteGroup group = server.getRawGroup(MODULE);
    assertNotNull(group);
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && statement.group().referable().getRefName().equals(name)) {
        LocatedReferable referable = statement.group().referable();
        Scope scope = server.getReferableScope(referable);
        assertNotNull("No scope for " + name, scope);
        return scope;
      }
    }
    throw new AssertionError("No definition " + name);
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

  private NamespaceCommandSink lookup(Scope scope, String name) {
    NamespaceCommandSink sink = new NamespaceCommandSink();
    assertNotNull("'" + name + "' is not in scope", scope.resolveName(name, Scope.ScopeContext.STATIC, sink));
    return sink;
  }

  @Test
  public void nameFromAnImportReportsThatImport() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo)

      \\func f => foo
      """);
    resolve();

    NamespaceCommandSink sink = lookup(scopeInside("f"), "foo");
    assertTrue(sink.isKnown());
    assertSame(command("\\import Foo"), sink.getCommand());
  }

  @Test
  public void nameFromAnOpenReportsThatOpen() {
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

    Scope scope = scopeInside("f");
    assertSame(command("\\open M"), lookup(scope, "bar").getCommand());
    assertSame(command("\\import Foo"), lookup(scope, "M").getCommand());
  }

  @Test
  public void renamedNameReportsItsCommand() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo \\as bar)

      \\func f => bar
      """);
    resolve();

    Scope scope = scopeInside("f");
    assertSame(command("\\import Foo"), lookup(scope, "bar").getCommand());
    assertNull("The old name must not be visible", scope.resolveName("foo", Scope.ScopeContext.STATIC));
  }

  @Test
  public void theEarlierCommandWins() {
    update(FOO, """
      \\module A \\where \\func dup => 0
      \\module B \\where \\func dup => 1
      """);
    update("""
      \\import Foo (A, B)
      \\open A
      \\open B

      \\func f => dup
      """);
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());

    Scope scope = scopeInside("f");
    assertSame(command("\\open A"), lookup(scope, "dup").getCommand());
  }

  @Test
  public void ownDefinitionIsNotACommand() {
    update(FOO, """
      \\module M \\where {
        \\func bar => 0
      }
      """);
    update("""
      \\import Foo (M)
      \\open M

      \\func bar => 1
      \\func f => bar
      """);
    resolve();

    NamespaceCommandSink sink = lookup(scopeInside("f"), "bar");
    assertTrue(sink.isKnown());
    assertNull("A definition of the module itself needs no command", sink.getCommand());
  }

  @Test
  public void preludeNameIsNotACommand() {
    update("\\func f => 0");
    resolve();

    NamespaceCommandSink sink = lookup(scopeInside("f"), "Nat");
    assertTrue(sink.isKnown());
    assertNull("A name of the prelude needs no command", sink.getCommand());
  }

  @Test
  public void theNameOfAnImportedModuleIsNotClaimedToNeedNoCommand() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo

      \\func f => Foo.foo
      """);
    resolve();

    NamespaceCommandSink sink = lookup(scopeInside("f"), "Foo");
    assertFalse("The origin of an imported module name must stay unknown", sink.isKnown());
  }

  @Test
  public void missingNameLeavesTheSinkUnknown() {
    update("\\func f => 0");
    resolve();

    NamespaceCommandSink sink = new NamespaceCommandSink();
    assertNull(scopeInside("f").resolveName("nothingLikeThis", Scope.ScopeContext.STATIC, sink));
    assertFalse(sink.isKnown());
  }
}
