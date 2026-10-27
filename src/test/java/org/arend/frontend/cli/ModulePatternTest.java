package org.arend.frontend.cli;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.SourceLibrary;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/** Wildcard module positionals, as expanded by {@link CliSetup#expandModulePatterns}. */
public class ModulePatternTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private CommandContext ctx;

  @Before
  public void setUp() throws IOException {
    Path root = tempFolder.newFolder("lib").toPath();
    for (String module : List.of("A", "A/B", "A/C/D", "E")) {
      Path file = root.resolve("src").resolve(module + ".ard");
      Files.createDirectories(file.getParent());
      Files.writeString(file, "", StandardCharsets.UTF_8);
    }
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    SourceLibrary library = FileSourceLibrary.fromConfigFile(root.resolve("arend.yaml"), false, DummyErrorReporter.INSTANCE);
    assertNotNull(library);

    ctx = new CommandContext();
    ctx.requestedLibraries.add(library);
  }

  private List<String> expand(String... patterns) {
    ctx.requestedModulePatterns.addAll(List.of(patterns));
    CliSetup.expandModulePatterns(ctx);
    assertTrue("patterns are consumed by the expansion", ctx.requestedModulePatterns.isEmpty());
    return ctx.requestedModules.stream().map(pair -> {
      assertNull(pair.proj2);
      return String.join(".", pair.proj1.toList());
    }).toList();
  }

  /** {@code *} crosses dots, so a directory pattern reaches every depth, but not the directory's own module. */
  @Test
  public void directoryPatternIsRecursive() {
    assertEquals(List.of("A.B", "A.C.D"), expand("A.*"));
    assertFalse(ctx.exitWithError);
  }

  @Test
  public void leadingAndTrailingWildcards() {
    assertEquals(List.of("A.C.D"), expand("*D"));
    ctx.requestedModules.clear();
    assertEquals(List.of("A", "A.B", "A.C.D"), expand("A*"));
  }

  /** Overlapping patterns and explicit names name each module once. */
  @Test
  public void matchesAreDeduplicated() {
    ctx.requestedModules.add(new Pair<>(new ModulePath("A", "B"), null));
    assertEquals(List.of("A.B", "A.C.D"), expand("A.*", "*.C.*"));
  }

  /** An empty expansion must fail the run; an empty scope would mean a whole-library typecheck. */
  @Test
  public void patternMatchingNothingIsAnError() {
    assertEquals(List.of(), expand("Nope.*"));
    assertTrue(ctx.exitWithError);
  }
}
