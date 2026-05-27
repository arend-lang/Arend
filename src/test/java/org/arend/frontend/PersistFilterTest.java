package org.arend.frontend;

import org.arend.core.definition.Definition;
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
import org.arend.naming.reference.TCDefReferable;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for {@link ConsoleMain#groupHasTypecheckingErrors}, the
 * predicate that gates whether a module gets persisted to an {@code .arc} binary
 * cache.
 *
 * <p>The underlying bug: writing modules with {@link Definition.TypeCheckingStatus#HAS_ERRORS}
 * to {@code .arc} sets up a cycle in {@code CliServerRequester.loadBinaryCache} —
 * every subsequent CLI invocation deserializes the module, the load-side
 * {@code hasMissingTypechecked} check detects the HAS_ERRORS state, calls
 * {@code clearTypechecked}, and the typecheck loop re-typechecks from source.
 * In a long-lived daemon, each cycle allocates a fresh wave of
 * {@code FunctionDefinition} objects that are pinned by cached expression trees
 * the {@code clearTypechecked} walk doesn't touch — a slow leak that surfaces
 * as phantom errors with disambiguated {@code Foo.bar} actual types.
 *
 * <p>{@link ConsoleMain#persistLibrary} now consults
 * {@code groupHasTypecheckingErrors} and skips persisting any module whose
 * typechecked state contains a HAS_ERRORS def. This test pins the predicate.
 */
public class PersistFilterTest {
  private static final String LIB_NAME = "test_library";

  private ArendServer server;
  private long modStamp = 1;
  private final List<GeneralError> errorList = new ArrayList<>();
  private final ListErrorReporter errorReporter = new ListErrorReporter(errorList);

  @Before
  public void setUp() {
    server = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server.updateLibrary(MemoryLibrary.INSTANCE, DummyErrorReporter.INSTANCE);
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
    for (String name : moduleNames) modules.add(moduleLoc(name));
    server.getCheckerFor(modules).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  private TCDefReferable getDef(ConcreteGroup group, String name) {
    for (var statement : group.statements()) {
      if (statement.group() != null && statement.group().referable().getRefName().equals(name)) {
        return statement.group().referable() instanceof TCDefReferable r ? r : null;
      }
    }
    return null;
  }

  /** Sanity baseline: a clean module reports no HAS_ERRORS defs. */
  @Test
  public void cleanModule_predicateReturnsFalse() {
    addModule("Clean", "\\func f : Nat => 0\n\\func g : Nat => f");
    typecheck("Clean");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Clean"));
    assertNotNull(group);
    assertEquals("f should typecheck cleanly",
        Definition.TypeCheckingStatus.NO_ERRORS,
        getDef(group, "f").getTypechecked().status());
    assertFalse("predicate must report false on a clean module",
        ConsoleMain.groupHasTypecheckingErrors(group));
  }

  /**
   * The load-side regression guard: a module whose typechecked state contains
   * any HAS_ERRORS def must be reported as having errors, so the persist side
   * can skip it and avoid the deserialize → clear → re-typecheck cycle.
   */
  @Test
  public void moduleWithTypeError_predicateReturnsTrue() {
    // \\Set0 is not a Nat, so the body fails to typecheck.
    addModule("Broken", "\\func f : Nat => \\Set0");
    typecheck("Broken");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Broken"));
    assertNotNull(group);
    Definition fDef = getDef(group, "f").getTypechecked();
    assertNotNull("f should be typechecked (with errors)", fDef);
    assertEquals("f should be in HAS_ERRORS state",
        Definition.TypeCheckingStatus.HAS_ERRORS, fDef.status());
    assertTrue("predicate must report true once any def is in HAS_ERRORS state",
        ConsoleMain.groupHasTypecheckingErrors(group));
  }

  /**
   * Multi-level case: predicate should walk through {@code \\where} blocks and
   * find a HAS_ERRORS def nested anywhere, not just at the top level.
   */
  @Test
  public void moduleWithNestedTypeError_predicateReturnsTrue() {
    addModule("Nested",
        "\\func outer : Nat => inner\n" +
        "  \\where {\n" +
        "    \\func inner : Nat => \\Set0\n" +
        "  }");
    typecheck("Nested");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Nested"));
    assertNotNull(group);
    assertTrue("nested HAS_ERRORS def must be detected by the walker",
        ConsoleMain.groupHasTypecheckingErrors(group));
  }
}
