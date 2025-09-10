package org.arend.repl;

import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.naming.reference.LocatedReferableImpl;
import org.arend.naming.reference.Referable;
import org.arend.prelude.Prelude;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import static org.arend.repl.Repl.REPL_MODULE_LOCATION;

public class PlainCliReplTest {
  @Test
  public void func() {
    var repl = new PlainCliRepl();
    repl.initialize();

    String funcF = "\\func f => 0";
    ByteArrayInputStream funcFBytes = new ByteArrayInputStream(funcF.getBytes());
    System.setIn(funcFBytes);
    repl.runRepl(System.in);

    Set<ModuleLocation> modulePaths = repl.getLoadedModuleLocations();
    Assert.assertEquals(2, modulePaths.size());
    Assert.assertTrue(modulePaths.contains(Prelude.MODULE_LOCATION));
    Assert.assertTrue(modulePaths.contains(REPL_MODULE_LOCATION));

    List<Referable> elements = repl.getInScopeElements();
    Assert.assertTrue(elements.stream().anyMatch(
      referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked()
    ));
    Definition f = elements.stream().filter(referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked())
      .map(referable -> ((LocatedReferableImpl) referable).getTypechecked()).findAny().orElse(null);

    funcF = "\\func f => 1";
    funcFBytes = new ByteArrayInputStream(funcF.getBytes());
    System.setIn(funcFBytes);
    repl.runRepl(System.in);

    elements = repl.getInScopeElements();
    Definition newF = elements.stream().filter(referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked())
      .map(referable -> ((LocatedReferableImpl) referable).getTypechecked()).findAny().orElse(null);
    Assert.assertNotEquals(f, newF);
    Assert.assertEquals("1", ((FunctionDefinition) newF).getBody().toString());

    String funcG = "\\func g => 2";
    ByteArrayInputStream funcGBytes = new ByteArrayInputStream(funcG.getBytes());
    System.setIn(funcGBytes);
    repl.runRepl(System.in);

    Assert.assertEquals(3, repl.statements.size());
    elements = repl.getInScopeElements();
    Assert.assertTrue(elements.stream().anyMatch(
      referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("f") && ((LocatedReferableImpl) referable).isTypechecked()
    ));
    Assert.assertTrue(elements.stream().anyMatch(
      referable -> referable instanceof LocatedReferableImpl && referable.getRefName().equals("g") && ((LocatedReferableImpl) referable).isTypechecked()
    ));
  }

  @Test
  public void importAndGetModules() {
    var repl = new PlainCliRepl();
    repl.initialize();

    String setUp = ":lib arend-lib\n:load Combinatorics.Factorial\n:import Combinatorics.Factorial\n";
    ByteArrayInputStream bytes = new ByteArrayInputStream(setUp.getBytes());
    System.setIn(bytes);
    repl.runRepl(System.in);

    Set<ModulePath> modulePaths = repl.getAllModules();
    Assert.assertTrue(modulePaths.contains(Prelude.MODULE_PATH));
    Assert.assertTrue(modulePaths.contains(REPL_MODULE_LOCATION.getModulePath()));
    ModulePath factorial = new ModulePath("Combinatorics", "Factorial");
    Assert.assertTrue(modulePaths.contains(factorial));

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));

    bytes = new ByteArrayInputStream("fac 5".getBytes());
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

    Assert.assertEquals(1, repl.statements.size());
    Assert.assertEquals("\\import Combinatorics.Factorial", repl.statements.getFirst().command().toString());

    lib = ":unlib arend-lib";
    bytes = new ByteArrayInputStream(lib.getBytes());
    System.setIn(bytes);
    repl.runRepl(System.in);

    allModules = repl.getAllModules();
    Assert.assertEquals(2, allModules.size());
    Assert.assertFalse(allModules.contains(factorial));
  }
}
