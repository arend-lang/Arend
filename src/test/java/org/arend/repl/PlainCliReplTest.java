package org.arend.repl;

import org.arend.ArendTestCase;
import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.naming.reference.LocatedReferableImpl;
import org.arend.naming.reference.Referable;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServerRequester;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.arend.repl.Repl.replModuleLocation;

@Ignore
public class PlainCliReplTest extends ArendTestCase {
  @Override
  protected ArendServerRequester getRequester() {
    return new CliServerRequester(new LibraryManager(DummyErrorReporter.INSTANCE));
  }

  @Test
  public void func() {
    var repl = new PlainCliRepl(server, List.of());
    repl.initialize();

    String funcF = "\\func f => 0";
    ByteArrayInputStream funcFBytes = new ByteArrayInputStream(funcF.getBytes());
    System.setIn(funcFBytes);
    repl.runRepl(System.in);

    Set<ModuleLocation> modulePaths = repl.getLoadedModuleLocations();
    Assert.assertEquals(2, modulePaths.size());
    Assert.assertTrue(modulePaths.contains(Prelude.MODULE_LOCATION));
    Assert.assertTrue(modulePaths.contains(replModuleLocation));

    List<Referable> elements = Repl.getInScopeElements(server, Objects.requireNonNull(server.getRawGroup(replModuleLocation)).statements());
    Assert.assertTrue(elements.stream().anyMatch(
      referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked()
    ));
    Definition f = elements.stream().filter(referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked())
      .map(referable -> ((LocatedReferableImpl) referable).getTypechecked()).findAny().orElse(null);

    funcF = "\\func f => 1";
    funcFBytes = new ByteArrayInputStream(funcF.getBytes());
    System.setIn(funcFBytes);
    repl.runRepl(System.in);

    elements = Repl.getInScopeElements(server, Objects.requireNonNull(server.getRawGroup(replModuleLocation)).statements());
    Definition newF = elements.stream().filter(referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked())
      .map(referable -> ((LocatedReferableImpl) referable).getTypechecked()).findAny().orElse(null);
    Assert.assertNotEquals(f, newF);
    Assert.assertNotNull(newF);
    Assert.assertEquals("1", Objects.requireNonNull(((FunctionDefinition) newF).getBody()).toString());

    String funcG = "\\func g => 2";
    ByteArrayInputStream funcGBytes = new ByteArrayInputStream(funcG.getBytes());
    System.setIn(funcGBytes);
    repl.runRepl(System.in);

    elements = Repl.getInScopeElements(server, Objects.requireNonNull(server.getRawGroup(replModuleLocation)).statements());
    Assert.assertTrue(elements.stream().anyMatch(
      referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked()
    ));
    Assert.assertTrue(elements.stream().anyMatch(
      referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("g") && ((LocatedReferableImpl) referable).isTypechecked()
    ));
  }

  // See https://github.com/arend-lang/Arend/issues/128
  @Test
  public void errorPropagation() {
    var repl = new PlainCliRepl(server, List.of());
    repl.initialize();

    // A definition with an outright error must not be added to the context.
    feed(repl, "\\func x");
    Assert.assertEquals(0, countDefinitions(repl, "x"));

    // A subsequent valid definition is added and the earlier error is not resurrected.
    feed(repl, "\\func y => 0");
    Assert.assertEquals(1, countDefinitions(repl, "y"));

    // Re-using a name that previously errored is not a duplicate: the new definition is added.
    feed(repl, "\\func x => 1");
    Assert.assertEquals(1, countDefinitions(repl, "x"));

    // Redefining a valid definition replaces it instead of reporting a duplicate.
    feed(repl, "\\func y => 2");
    Assert.assertEquals(1, countDefinitions(repl, "y"));
    Assert.assertEquals("2", Objects.requireNonNull(((FunctionDefinition) typecheckedDefinition(repl, "y")).getBody()).toString());

    // A goal is not an error, so a definition containing {?} stays in the context.
    feed(repl, "\\func g : Nat => {?}");
    Assert.assertEquals(1, countDefinitions(repl, "g"));

    // Replacing a valid definition with an erroneous one drops it (and the old one) entirely.
    feed(repl, "\\func y => undefinedReference");
    Assert.assertEquals(0, countDefinitions(repl, "y"));
  }

  private static void feed(PlainCliRepl repl, String line) {
    System.setIn(new ByteArrayInputStream(line.getBytes()));
    repl.runRepl(System.in);
  }

  private static long countDefinitions(PlainCliRepl repl, String name) {
    return repl.getStatements().stream()
      .filter(statement -> statement.group() != null && statement.group().referable().getRefName().equals(name))
      .count();
  }

  private Definition typecheckedDefinition(PlainCliRepl repl, String name) {
    List<Referable> elements = Repl.getInScopeElements(server, repl.getStatements());
    return elements.stream()
      .filter(referable -> referable instanceof LocatedReferableImpl located && located.getRefName().equals(name) && located.isTypechecked())
      .map(referable -> ((LocatedReferableImpl) referable).getTypechecked())
      .findAny().orElse(null);
  }

  @Test
  public void importAndGetModules() {
    var repl = new PlainCliRepl(server, List.of());
    repl.initialize();

    String setUp = ":lib arend-lib\n:load Combinatorics.Factorial\n:import Combinatorics.Factorial\n";
    ByteArrayInputStream bytes = new ByteArrayInputStream(setUp.getBytes());
    System.setIn(bytes);
    repl.runRepl(System.in);

    Set<ModulePath> modulePaths = repl.getAllModules();
    Assert.assertTrue(modulePaths.contains(Prelude.MODULE_PATH));
    Assert.assertTrue(modulePaths.contains(replModuleLocation.getModulePath()));
    ModulePath factorial = new ModulePath("Combinatorics", "Factorial");
    Assert.assertTrue(modulePaths.contains(factorial));

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));

    bytes = new ByteArrayInputStream("Combinatorics.Factorial.fac 5".getBytes());
    System.setIn(bytes);
    repl.runRepl(System.in);
    String output = outContent.toString();
    Assert.assertTrue(output.contains("120"));

    String lib = ":unload Combinatorics.Factorial";
    bytes = new ByteArrayInputStream(lib.getBytes());
    System.setIn(bytes);
    repl.runRepl(System.in);

    Set<ModulePath> allModules = repl.getAllModules();
    Assert.assertTrue(allModules.size() > 2);
    Assert.assertTrue(allModules.contains(factorial));
    Assert.assertFalse(repl.getLoadedModuleLocations().stream().map(ModuleLocation::getModulePath).toList().contains(factorial));

    lib = ":unlib arend-lib";
    bytes = new ByteArrayInputStream(lib.getBytes());
    System.setIn(bytes);
    repl.runRepl(System.in);

    allModules = repl.getAllModules();
    Assert.assertEquals(2, allModules.size());
    Assert.assertFalse(allModules.contains(factorial));
  }
}
