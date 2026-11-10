package org.arend.server;

import org.arend.core.definition.Definition;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.module.FullName;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.MemoryLibrary;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * A definition in a {@code \where} block that uses only parameters of its parent gets these parameters during typechecking,
 * so it depends on the definitions in their types although it does not refer to them.
 * When such a definition is invalidated, the ones using it must be invalidated too, even if the parent was never typechecked.
 * Otherwise, they keep a core referring to an old core of the definition.
 */
public class ExternalParameterDependencyTest {
  private static final String USER = "\\import A\n\\func S (r : R) : Nat => 0\n  \\where\n    \\func P => r = r\n";

  private ArendServerImpl server;
  private long modStamp = 1;

  @Before
  public void setUp() {
    server = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server.updateLibrary(MemoryLibrary.INSTANCE, DummyErrorReporter.INSTANCE);
  }

  private ModuleLocation moduleLoc(String name) {
    return new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath(name));
  }

  private void updateModule(String name, String text) {
    ModuleLocation module = moduleLoc(name);
    ListErrorReporter parseErrors = new ListErrorReporter();
    ArendParser.StatementsContext tree = CommonCliRepl.createParser(text, module, parseErrors).statements();
    ConcreteGroup group = parseErrors.getErrorList().isEmpty() ? new BuildVisitor(module, parseErrors).visitStatements(tree) : null;
    assertNotNull("Failed to parse module " + name, group);
    server.updateModule(modStamp++, module, () -> group);
  }

  private static String base(int k) {
    return "\\func k : Nat => " + k + "\n\\record R (n : Nat) (p : n = k)\n";
  }

  private TCDefReferable find(String module, String name) {
    List<TCDefReferable> result = new ArrayList<>();
    collect(Objects.requireNonNull(server.getRawGroup(moduleLoc(module))), name, result);
    assertEquals(name + " in " + module, 1, result.size());
    return result.getFirst();
  }

  private static void collect(ConcreteGroup group, String name, List<TCDefReferable> out) {
    if (group.referable() instanceof TCDefReferable ref && ref.getRefName().equals(name)) out.add(ref);
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) collect(statement.group(), name, out);
    }
  }

  /** P needs R to be typechecked first, although it does not refer to it. */
  @Test
  public void definitionUsingOnlyParentParametersTypechecksTheirTypes() {
    updateModule("A", base(0));
    updateModule("B", USER);
    server.getCheckerFor(List.of(moduleLoc("B"))).typecheck(List.of(new FullName(moduleLoc("B"), new LongName("S", "P"))), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertNotNull("P must be typechecked", find("B", "P").getTypechecked());
    assertNotNull("R must be typechecked", find("A", "R").getTypechecked());
  }

  @Test
  public void definitionUsingOnlyParentParametersIsInvalidated() {
    updateModule("A", base(0));
    updateModule("B", USER);
    server.getCheckerFor(List.of(moduleLoc("A"))).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    // Only P is typechecked, as when it is a dependency of another module and S is not
    server.getCheckerFor(List.of(moduleLoc("B"))).typecheck(List.of(new FullName(moduleLoc("B"), new LongName("S", "P"))), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    Definition p = find("B", "P").getTypechecked();
    assertNotNull("P must be typechecked", p);
    Definition r = find("A", "R").getTypechecked();
    assertNotNull("R must be typechecked", r);

    // R depends on k, so it is rebuilt
    updateModule("A", base(1));
    server.getCheckerFor(List.of(moduleLoc("A"))).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertNotSame("R must be rebuilt", r, find("A", "R").getTypechecked());
    assertNotSame("P must be rebuilt since the type of its parameter was rebuilt", p, find("B", "P").getTypechecked());
  }
}
