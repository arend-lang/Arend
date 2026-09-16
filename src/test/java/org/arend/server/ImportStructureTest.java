package org.arend.server;

import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.library.MemoryLibrary;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ImportStructureTest extends TypeCheckingTestCase {
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

  private void resolveAndTypecheck() {
    server.getCheckerFor(myModules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    server.getCheckerFor(myModules).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertThat(getAllErrors(), containsErrors(0));
  }

  private List<String> structure() {
    ImportStructure result = new ImportAnalyzer(server).optimize(MODULE, false, false);
    assertNull("expected a structure", result == null ? "" : null);
    List<String> lines = new ArrayList<>();
    for (Map.Entry<ModulePath, Set<ImportedName>> entry : sorted(result.fileImports())) {
      lines.add("\\import " + entry.getKey() + names(entry.getValue()));
    }
    render(result.openStructure(), "", lines);
    return lines;
  }

  private void render(ImportStructure.Group group, String prefix, List<String> lines) {
    for (Map.Entry<ModulePath, Set<ImportedName>> entry : sorted(group.usages())) {
      if (entry.getKey().toList().isEmpty()) continue;
      lines.add(prefix + "\\open " + entry.getKey() + names(entry.getValue()));
    }
    for (ImportStructure.Group subgroup : group.subgroups()) {
      render(subgroup, prefix + subgroup.name() + ": ", lines);
    }
  }

  private static List<Map.Entry<ModulePath, Set<ImportedName>>> sorted(Map<ModulePath, Set<ImportedName>> map) {
    List<Map.Entry<ModulePath, Set<ImportedName>>> result = new ArrayList<>(map.entrySet());
    result.sort(Comparator.comparing(entry -> entry.getKey().toString()));
    return result;
  }

  private static String names(Set<ImportedName> names) {
    List<String> result = new ArrayList<>();
    for (ImportedName name : names) result.add(name.toString());
    Collections.sort(result);
    return " (" + String.join(", ", result) + ")";
  }

  @Test
  public void aUsedNameIsImportedFromTheModuleItLivesIn() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo)

      \\func f => foo
      """);
    resolveAndTypecheck();

    assertEquals(List.of("\\import Foo (foo)"), structure());
  }

  @Test
  public void aNestedDefinitionNeedsAnOpen() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (M)
      \\open M

      \\func f => foo
      """);
    resolveAndTypecheck();

    assertEquals(List.of("\\import Foo (M)", "\\open M (foo)"), structure());
  }

  @Test
  public void aQualifiedReferenceNeedsNoOpen() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (M)

      \\func f => M.foo
      """);
    resolveAndTypecheck();

    assertEquals(List.of("\\import Foo (M)"), structure());
  }

  @Test
  public void aSharedOpenIsLifted() {
    update(FOO, """
      \\module M \\where {
        \\func foo => 0
      }
      """);
    update("""
      \\import Foo (M)

      \\func f => foo
        \\where \\open M

      \\func g => foo
        \\where \\open M
      """);
    resolveAndTypecheck();

    assertEquals(List.of("\\import Foo (M)", "\\open M (foo)"), structure());
  }

  @Test
  public void aRenamingIsKept() {
    update(FOO, "\\func foo => 0");
    update("""
      \\import Foo (foo \\as bar)

      \\func f => bar
      """);
    resolveAndTypecheck();

    assertEquals(List.of("\\import Foo (foo \\as bar)"), structure());
  }

  @Test
  public void anInstanceKeepsItsCommand() {
    update(FOO, """
      \\module M \\where {
        \\class C (X : \\Type) {
          | op : X -> X
        }

        \\instance i : C Nat
          | op => \\lam x => x
      }
      """);
    update("""
      \\import Foo (M)
      \\open M

      \\func f => op 0
      """);
    resolveAndTypecheck();

    assertEquals(List.of("\\import Foo (M)", "\\open M (i, op)"), structure());
  }
}
