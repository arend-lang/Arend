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
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Updating a module without changing its interface (that is, its namespace and typing info) must not
 * force the modules importing it to be resolved again. This matters for modules in an import cycle
 * since all other modules of the cycle are both importers and dependencies of the updated module.
 */
public class CyclicModuleUpdateTest {
  private ArendServer server;
  private long modStamp = 1;
  private final Set<String> resolved = new HashSet<>();
  private final List<String> invalidations = new ArrayList<>();

  @Before
  public void setUp() {
    server = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server.updateLibrary(MemoryLibrary.INSTANCE, DummyErrorReporter.INSTANCE);
    server.addListener(new ArendServerListener() {
      @Override
      public void onModuleResolved(@NotNull ModuleLocation module) {
        if (!module.equals(Prelude.MODULE_LOCATION)) resolved.add(module.getModulePath().toString());
      }

      @Override
      public void onImportersInvalidated(@NotNull ModuleLocation module, @NotNull String reason) {
        invalidations.add(module.getModulePath() + ": " + reason);
      }
    });
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

  private Set<String> typecheck(String name) {
    resolved.clear();
    server.getCheckerFor(Collections.singletonList(moduleLoc(name))).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    return new HashSet<>(resolved);
  }

  private List<GeneralError> errors(String name) {
    return server.getErrorMap().getOrDefault(moduleLoc(name), Collections.emptyList());
  }

  private static final String B = "\\import A\n\\func b : a = 0 => idp";

  private static String moduleA(String extra) {
    return "\\import B\n\\func a : Nat => 0\n\\func c => b\n" + extra;
  }

  private void initCycle(String extra) {
    updateModule("A", moduleA(extra));
    updateModule("B", B);
    assertEquals(Set.of("A", "B"), typecheck("A"));
    assertTrue(errors("A").isEmpty());
    assertTrue(errors("B").isEmpty());
    invalidations.clear();
  }

  @Test
  public void bodyChangeDoesNotResolveCycle() {
    initCycle("");

    updateModule("A", "\\import B\n\\func a : Nat => 1\n\\func c => b");
    assertEquals(Set.of("A"), typecheck("A"));
    assertFalse(errors("B").isEmpty());

    updateModule("A", moduleA(""));
    assertEquals(Set.of("A"), typecheck("A"));
    assertTrue(errors("A").isEmpty());
    assertTrue(errors("B").isEmpty());
    assertEquals(List.of(), invalidations);
  }

  @Test
  public void newDefinitionResolvesCycle() {
    initCycle("");
    updateModule("A", moduleA("\\func d => 0"));
    assertEquals(Set.of("A", "B"), typecheck("A"));
    assertTrue(errors("B").isEmpty());
    assertEquals(1, invalidations.size());
  }

  @Test
  public void renamedDefinitionResolvesCycle() {
    initCycle("");
    updateModule("A", "\\import B\n\\func a' : Nat => 0\n\\func c => b");
    assertEquals(Set.of("A", "B"), typecheck("A"));
    assertFalse(errors("B").isEmpty());
    assertEquals(1, invalidations.size());
  }

  @Test
  public void typeChangeResolvesCycle() {
    initCycle("");
    updateModule("A", "\\import B\n\\func a : Int => 0\n\\func c => b");
    assertEquals(Set.of("A", "B"), typecheck("A"));
    assertEquals(1, invalidations.size());
    assertTrue(invalidations.getFirst(), invalidations.getFirst().contains("type of 'a'"));
  }

  @Test
  public void lemmaBodyChangeDoesNotResolveCycle() {
    initCycle("\\lemma l : 0 = 0 => idp");
    updateModule("A", moduleA("\\lemma l : 0 = 0 => (\\lam x => x) idp"));
    assertEquals(Set.of("A"), typecheck("A"));
    assertEquals(List.of(), invalidations);
  }

  @Test
  public void functionBodyHeadChangeResolvesCycle() {
    initCycle("\\func f : 0 = 0 => idp");
    updateModule("A", moduleA("\\func f : 0 = 0 => (\\lam x => x) idp"));
    assertEquals(Set.of("A", "B"), typecheck("A"));
    assertEquals(1, invalidations.size());
    assertTrue(invalidations.getFirst(), invalidations.getFirst().contains("body of 'f'"));
  }

  @Test
  public void fixedErrorDisappearsInImporter() {
    updateModule("M", "\\func f : Nat => suc idp");
    updateModule("X", "\\import M\n\\func x : f = 1 => idp");
    typecheck("X");
    assertFalse(errors("M").isEmpty());
    assertFalse(errors("X").isEmpty());

    updateModule("M", "\\func f : Nat => suc 0");
    assertEquals(Set.of("M"), typecheck("X"));
    assertTrue(errors("M").isEmpty());
    assertTrue(errors("X").isEmpty());
  }
}
