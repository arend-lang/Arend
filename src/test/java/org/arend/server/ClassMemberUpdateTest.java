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

import java.util.*;

import static org.junit.Assert.*;

/**
 * Adding a dynamic member to a class must not break the modules that use the class.
 */
public class ClassMemberUpdateTest {
  private static final String CLASS =
    "\\class C {\n  | f : Nat -> Nat\n  | g : f 0 = f 0\n\n  \\func h => f 0\n";
  private static final String USER =
    "\\import A\n\\instance I : C\n  | f (n : Nat) : Nat \\with {\n    | 0 => 0\n    | suc n => n\n  }\n  | g => idp\n";

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

  private void typecheck(String name) {
    server.getCheckerFor(Collections.singletonList(moduleLoc(name))).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  private void assertNoErrors(String name) {
    List<GeneralError> errors = new ArrayList<>(server.getErrorService().getResolverErrors(moduleLoc(name)));
    errors.addAll(server.getErrorService().getTypecheckingErrors(moduleLoc(name)));
    assertEquals("Errors in " + name, List.of(), errors.stream().map(GeneralError::getShortMessage).toList());
  }

  private void init() {
    updateModule("A", CLASS + "}\n");
    updateModule("B", USER);
    typecheck("B");
    assertNoErrors("A");
    assertNoErrors("B");
  }

  @Test
  public void addDynamicLemmaThenCheckUser() {
    init();
    updateModule("A", CLASS + "\n  \\lemma l : f 0 = f 0 => idp\n}\n");
    typecheck("B");
    assertNoErrors("A");
    assertNoErrors("B");
  }

  @Test
  public void addDynamicLemmaThenCheckClassThenUser() {
    init();
    updateModule("A", CLASS + "\n  \\lemma l : f 0 = f 0 => idp\n}\n");
    typecheck("A");
    assertNoErrors("A");
    typecheck("B");
    assertNoErrors("B");
  }

  @Test
  public void addDynamicLemmaInSeveralSteps() {
    init();
    updateModule("A", CLASS + "\n  \\lemma l : f 0 = f 0 => {?}\n}\n");
    typecheck("A");
    updateModule("A", CLASS + "\n  \\lemma l : f 0 = f 0 => idp\n}\n");
    typecheck("A");
    typecheck("B");
    assertNoErrors("A");
    assertNoErrors("B");
  }

  /** A new field recreates the class, so its users are resolved again and have to find its new fields. */
  @Test
  public void addFieldWithDefault() {
    init();
    updateModule("A", CLASS + "  | k : Nat\n  \\default k => 0\n}\n");
    typecheck("B");
    assertNoErrors("A");
    assertNoErrors("B");
  }
}
