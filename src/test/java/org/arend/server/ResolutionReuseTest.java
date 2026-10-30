package org.arend.server;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.MemoryLibrary;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.prelude.Prelude;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Modules in an import cycle with an edited module stay unresolved until a background check
 * completes. Checks of the edited module must not redo work for them needlessly.
 */
public class ResolutionReuseTest {
  private ArendServerImpl server;
  private long modStamp = 1;
  private final Set<String> resolved = new HashSet<>();

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

  private void initCycle() {
    updateModule("A", "\\import B\n\\func a : Nat => 0\n\\func c => b");
    updateModule("B", "\\import A\n\\func b : a = 0 => idp");
    server.getCheckerFor(Collections.singletonList(moduleLoc("A"))).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    updateModule("A", "\\import B\n\\func a : Nat => 0\n\\func c => b\n");
    resolved.clear();
  }

  private GlobalTypingInfo typingInfo(String name) {
    return Objects.requireNonNull(server.getGroupData(moduleLoc(name))).getTypingInfo();
  }

  @Test
  public void typingInfoOfUnresolvedModuleIsReused() {
    initCycle();
    server.getCheckerFor(Collections.singletonList(moduleLoc("A"))).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    GlobalTypingInfo typingInfo = typingInfo("B");
    assertNotNull(typingInfo);
    assertFalse(Objects.requireNonNull(server.getGroupData(moduleLoc("B"))).isResolved());
    server.getCheckerFor(Collections.singletonList(moduleLoc("A"))).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertSame(typingInfo, typingInfo("B"));
  }

  @Test
  public void discardedResolutionIsNotRepeated() {
    initCycle();
    ArendChecker checker = server.getCheckerFor(Collections.singletonList(moduleLoc("A")));
    boolean[] updated = new boolean[1];
    checker.resolveAll(UnstoppableCancellationIndicator.INSTANCE, new ProgressReporter<>() {
      @Override
      public void beginProcessing(int numberOfItems) {}

      @Override
      public void beginItem(@NotNull ModuleLocation item) {
        if (!updated[0]) {
          updated[0] = true;
          updateModule("A", "\\import B\n\\func a : Nat => 0\n\\func c => b\n\n");
        }
      }

      @Override
      public void endItem(@NotNull ModuleLocation item) {}
    });
    assertTrue(updated[0]);
    assertEquals(Set.of(), resolved);

    // Resolution checks for cancellation for each module, so this counts the work done by the second attempt
    int[] checks = new int[1];
    CancellationIndicator countingIndicator = new CancellationIndicator() {
      @Override
      public boolean isCanceled() {
        checks[0]++;
        return false;
      }

      @Override
      public void cancel() {}
    };
    assertEquals(0, checker.typecheck(countingIndicator, ProgressReporter.empty()));
    assertEquals(0, checks[0]);
    assertTrue(resolved.isEmpty());

    server.getCheckerFor(Collections.singletonList(moduleLoc("A"))).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertEquals(Set.of("A", "B"), resolved);
  }
}
