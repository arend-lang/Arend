package org.arend.frontend;

import org.arend.core.definition.Definition;
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
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.BinaryCacheFilter;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for {@link BinaryCacheFilter}, the predicates that gate what reaches an
 * {@code .arc} binary cache: {@link BinaryCacheFilter#isCacheable} for the CLI and the IDE, both
 * of which persist whole modules or nothing, and {@link BinaryCacheFilter#isSerializable} /
 * {@link BinaryCacheFilter#countSerializable} for the IDE's own bookkeeping of how much of a
 * module it has already saved.
 *
 * <p>The underlying bug: writing modules with {@link Definition.TypeCheckingStatus#HAS_ERRORS}
 * to {@code .arc} sets up a cycle in {@code CliServerRequester.loadBinaryCache} —
 * every subsequent CLI invocation deserializes the module, Phase 2c's
 * orphan-shell sweep detects the dangling reference, calls
 * {@code clearTypechecked}, and the typecheck loop re-typechecks from source.
 * In a long-lived daemon, each cycle allocates a fresh wave of
 * {@code FunctionDefinition} objects that are pinned by cached expression trees
 * the {@code clearTypechecked} walk doesn't touch — a slow leak that surfaces
 * as phantom errors with disambiguated {@code Foo.bar} actual types.
 *
 * <p>The CLI's persist pass skips a module only when something typechecked in it has a
 * HAS_ERRORS def or an unfilled goal -- not one that has nothing checked at all (the normal state
 * of most of a targeted run's transitive import cone) and not one that is merely partially
 * typechecked, since {@code ModuleSerialization} already writes such a module as far as its cores
 * allow (none, some, or all) rather than refusing it outright. The IDE's save pass takes the same
 * whole-module {@code isCacheable} path as the CLI.
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

  private void typecheckDefinition(String moduleName, String definitionName) {
    ModuleLocation module = moduleLoc(moduleName);
    server.getCheckerFor(List.of(module)).typecheck(
        List.of(new FullName(module, new LongName(definitionName))), DummyErrorReporter.INSTANCE,
        UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
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
        BinaryCacheFilter.hasTypecheckingErrors(group));
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
        BinaryCacheFilter.hasTypecheckingErrors(group));
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
        BinaryCacheFilter.hasTypecheckingErrors(group));
  }

  /**
   * A module nothing has typechecked (only resolved, e.g. as a dependency the requested cone
   * never actually reached) is still cacheable: writing it records no cores and
   * {@code getComplete = false}, which the next load treats exactly like having no cache at all
   * for it. Refusing to write it here would only mean redoing the same nothing next time.
   */
  @Test
  public void untypecheckedModule_isStillCacheable() {
    addModule("Untouched", "\\func f : Nat => 0");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Untouched"));
    assertNotNull(group);
    assertTrue("a module with no cores is harmless to persist",
        BinaryCacheFilter.isCacheable(group));
  }

  /**
   * Typechecking one definition of a module leaves the rest without cores. Neither side refuses
   * such a module: the CLI's {@code ModuleSerialization} writes what it has and marks the module
   * incomplete rather than throwing the cache away, same as the IDE writing the part that is
   * there.
   */
  @Test
  public void partiallyTypecheckedModule_isSavedInPart() {
    addModule("Partial", "\\func f : Nat => 0\n\\func g : Nat => 1");
    typecheckDefinition("Partial", "f");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Partial"));
    assertNotNull(group);
    assertNotNull("f should be typechecked", getDef(group, "f").getTypechecked());
    assertNull("g should not be typechecked", getDef(group, "g").getTypechecked());
    assertTrue("a module that has something checked and nothing wrong with it is cacheable",
        BinaryCacheFilter.isCacheable(group));
    assertEquals("the IDE saves the one definition that has a core",
        1, BinaryCacheFilter.countSerializable(group));
  }

  /** The same module, once everything in it has been typechecked, is worth caching. */
  @Test
  public void fullyTypecheckedModule_isCacheable() {
    addModule("Full", "\\func f : Nat => 0\n\\func g : Nat => 1");
    typecheck("Full");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Full"));
    assertNotNull(group);
    assertTrue("a fully typechecked module must be persisted",
        BinaryCacheFilter.isCacheable(group));
  }

  /** A goal is not an error, but the definition holding it stays out of the cache all the same. */
  @Test
  public void moduleWithGoal_isNotCacheable() {
    addModule("Goal", "\\func f : Nat => {?}");
    typecheck("Goal");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Goal"));
    assertNotNull(group);
    assertTrue("the goal must be detected", BinaryCacheFilter.hasGoals(group));
    assertFalse("a module with a goal must not be persisted",
        BinaryCacheFilter.isCacheable(group));
    assertFalse("nor is the definition holding it serializable",
        BinaryCacheFilter.isSerializable(getDef(group, "f").getTypechecked()));
    assertEquals("so the module has nothing the IDE could save either",
        0, BinaryCacheFilter.countSerializable(group));
  }

  /**
   * Nothing to save, but still cacheable: a module with no definitions has nothing that could
   * hold an error or a goal, so it is written (as an empty group) the same as any other module
   * {@link #untypecheckedModule_isStillCacheable} covers -- harmless either way.
   */
  @Test
  public void moduleWithoutDefinitions_isStillCacheable() {
    addModule("Empty", "\\import Prelude");
    typecheck("Empty");

    ConcreteGroup group = server.getRawGroup(moduleLoc("Empty"));
    assertNotNull(group);
    assertTrue("a module with no definitions is harmless to persist",
        BinaryCacheFilter.isCacheable(group));
  }
}
