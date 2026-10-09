package org.arend.server;

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
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A module typechecked piecemeal, as a dependency of several other modules, must keep the errors
 * of the definitions checked by an earlier pass: those definitions are up-to-date and are not
 * reported again by the later passes.
 */
public class PartialModuleErrorsTest {
  private ArendServer server;
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

  private void addModule(String name, String text) {
    ModuleLocation module = moduleLoc(name);
    ListErrorReporter parseErrors = new ListErrorReporter();
    ArendParser.StatementsContext tree = CommonCliRepl.createParser(text, module, parseErrors).statements();
    ConcreteGroup group = parseErrors.getErrorList().isEmpty() ? new BuildVisitor(module, parseErrors).visitStatements(tree) : null;
    assertNotNull("Failed to parse module " + name, group);
    server.updateModule(modStamp++, module, () -> group);
  }

  private void typecheck(String name) {
    server.getCheckerFor(Collections.singletonList(moduleLoc(name))).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  @Test
  public void errorsSurviveLaterPartialPass() {
    addModule("B", "\\func bad : Nat => idp\n\\func ok : Nat => 0");
    addModule("X", "\\import B\n\\func x : Nat => bad");
    addModule("Y", "\\import B\n\\func y : Nat => ok");

    typecheck("X");
    List<GeneralError> afterX = server.getErrorMap().get(moduleLoc("B"));
    assertNotNull(afterX);
    assertFalse(afterX.isEmpty());

    typecheck("Y");
    typecheck("B");
    assertEquals(afterX.size(), server.getErrorMap().getOrDefault(moduleLoc("B"), Collections.emptyList()).size());
  }
}
