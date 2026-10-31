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
import org.arend.naming.reference.FieldReferableImpl;
import org.arend.naming.reference.Referable;
import org.arend.prelude.Prelude;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.server.impl.GroupData;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Adding a member to a class recreates the referables of the class and its fields.
 * Importers resolved again must refer to the new referables, not to the ones they were resolved to before.
 */
public class RecreatedReferableTest {
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

  private List<GeneralError> errors() {
    List<GeneralError> result = new ArrayList<>();
    for (List<GeneralError> errors : server.getErrorMap().values()) result.addAll(errors);
    return result;
  }

  private Concrete.ClassDefinition rawClass(String module, String name) {
    for (var statement : Objects.requireNonNull(server.getGroupData(moduleLoc(module))).getRawGroup().statements()) {
      if (statement.group() != null && statement.group().definition() instanceof Concrete.ClassDefinition classDef && classDef.getData().getRefName().equals(name)) return classDef;
    }
    throw new AssertionError("Class " + name + " is not found in " + module);
  }

  private Concrete.ClassDefinition resolvedClass(String module, String name) {
    GroupData groupData = Objects.requireNonNull(server.getGroupData(moduleLoc(module)));
    for (DefinitionData data : Objects.requireNonNull(groupData.getResolvedDefinitions())) {
      if (data.definition() instanceof Concrete.ClassDefinition classDef && classDef.getData().getRefName().equals(name)) return classDef;
    }
    throw new AssertionError("Class " + name + " is not resolved in " + module);
  }

  private static final String BASE = """
    \\class Mag (E : \\Set) | op : E -> E -> E
    \\class CMag \\extends Mag
    \\func NatMag : Mag \\cowith | E => Nat | op _ _ => 0
    """;

  private static String moduleM(String extra) {
    return "\\import Base\n\\class L (R : Mag) {\n  | e : R\n  \\func f => 0\n" + extra + "}\n";
  }

  private void init(String a, String s) {
    updateModule("Base", BASE);
    updateModule("M", moduleM(""));
    updateModule("A", a);
    if (s != null) updateModule("S", s);
    typecheck(s != null ? "S" : "A");
    assertEquals(Collections.emptyList(), errors());
    // A new member of L recreates the referables of L and its fields
    updateModule("M", moduleM("  \\func g => 1\n"));
  }

  @Test
  public void overriddenFieldAfterClassRecreated() {
    init("\\import Base\n\\import M\n\\class P \\extends L {\n  \\override R : CMag\n}\n",
         "\\import Base\n\\import A\n\\class Q \\extends P {\n  \\override R : CMag\n  \\func h (x y : R) => op x y\n}\n");
    typecheck("S");
    assertEquals(Collections.emptyList(), errors());

    FieldReferableImpl field = null;
    for (Concrete.ClassElement element : rawClass("M", "L").getElements()) {
      if (element instanceof Concrete.ClassField classField && classField.getData().getRefName().equals("R")) field = classField.getData();
    }
    assertNotNull(field);
    assertNotNull(field.getTypechecked());

    Referable overridden = null;
    for (Concrete.ClassElement element : resolvedClass("A", "P").getElements()) {
      if (element instanceof Concrete.OverriddenField overriddenField) overridden = overriddenField.getOverriddenField();
    }
    assertSame(field, overridden);
  }

  @Test
  public void implementedFieldAfterClassRecreated() {
    init("\\import Base\n\\import M\n\\func p : L \\cowith | R => NatMag | e => 0\n\\func q => \\new L { | R => NatMag | e => 0 }\n", null);
    typecheck("A");
    assertEquals(Collections.emptyList(), errors());
  }
}
