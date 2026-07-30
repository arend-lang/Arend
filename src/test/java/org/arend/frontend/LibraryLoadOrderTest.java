package org.arend.frontend;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.ArendExtension;
import org.arend.ext.DefaultArendExtension;
import org.arend.ext.LiteralTypechecker;
import org.arend.ext.error.ListErrorReporter;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServerListener;
import org.arend.server.ArendServerRequester;
import org.arend.server.impl.ArendServerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the order in which libraries are loaded.
 *
 * <p>A library's extension is set up exactly once, when the library is loaded, and the
 * {@code dependency -> extension} map it receives is built from the libraries loaded at that moment
 * (see {@code LibraryService.setupExtension}). A library must therefore be loaded <em>after</em> its
 * dependencies. The CLI used to load every requested library first and only then walk its
 * dependencies, so a project depending on arend-lib was set up with an empty dependency map: its
 * {@link org.arend.ext.DefaultArendExtension} had nothing to delegate to, arend-lib's
 * {@link LiteralTypechecker} was never consulted, and number and string literals failed to typecheck
 * with a bare {@code Type mismatch: expected R.E, actual Nat}.
 *
 * <p>These tests pin the load order rather than a workaround for the wrong one: the fix is that
 * dependencies load first, not that a late-arriving dependency retroactively repairs its dependents.
 */
public class LibraryLoadOrderTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Stands in for arend-lib: the dependency whose extension contributes a literal typechecker. The
   * typechecker is per-instance so that identity tells apart the live extension from one left over by
   * an extra load, and {@link #instances} counts how many times the extension was constructed.
   */
  public static class DependencyExtension implements ArendExtension {
    public static int instances;

    private final LiteralTypechecker myLiteralTypechecker = new LiteralTypechecker() {};

    public DependencyExtension() {
      instances++;
    }

    @Override
    public @Nullable LiteralTypechecker getLiteralTypechecker() {
      return myLiteralTypechecker;
    }
  }

  /**
   * Stands in for a project extension following the documented procedure: extend
   * {@link DefaultArendExtension} to inherit the services of the library's dependencies.
   */
  public static class InheritingExtension extends DefaultArendExtension {
  }

  /** An extension that provides its own literal typechecker, which must win over the dependency's. */
  public static class OverridingExtension extends DefaultArendExtension {
    static final LiteralTypechecker OWN_TYPECHECKER = new LiteralTypechecker() {};

    @Override
    public @Nullable LiteralTypechecker getLiteralTypechecker() {
      return OWN_TYPECHECKER;
    }
  }

  private ArendServerImpl server;
  private LibraryManager libraryManager;
  private ConsoleMain consoleMain;
  private Path libDir;
  /** Names of the libraries the server was told about, in the order the loader told it. */
  private final List<String> loadOrder = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    DependencyExtension.instances = 0;
    server = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    server.addListener(new ArendServerListener() {
      @Override
      public void onLibraryUpdated(@NotNull String libraryName) {
        loadOrder.add(libraryName);
      }
    });
    libraryManager = new LibraryManager(new ListErrorReporter(new ArrayList<>()));
    consoleMain = new ConsoleMain();
    libDir = tempFolder.newFolder("libs").toPath();
  }

  /**
   * Writes a library directory that {@code ConsoleMain.findLibrary} can discover by name. A library
   * given an {@code extensionMainClass} also gets an {@code ext} directory, because the extension is
   * only loaded when the library exposes a class-loader delegate; the class itself is then resolved
   * from the test classpath by the parent class loader.
   */
  private SourceLibrary writeLibrary(String name, List<String> dependencies, @Nullable String extensionMainClass) throws IOException {
    Path root = libDir.resolve(name);
    Files.createDirectories(root.resolve("src"));

    StringBuilder yaml = new StringBuilder("sourcesDir: src\n");
    if (!dependencies.isEmpty()) {
      yaml.append("dependencies: [").append(String.join(", ", dependencies)).append("]\n");
    }
    if (extensionMainClass != null) {
      Files.createDirectories(root.resolve("ext"));
      yaml.append("extensionsDir: ext\n");
      yaml.append("extensionMainClass: ").append(extensionMainClass).append('\n');
    }
    Files.writeString(root.resolve("arend.yaml"), yaml.toString(), StandardCharsets.UTF_8);

    SourceLibrary library = FileSourceLibrary.fromConfigFile(root.resolve("arend.yaml"), false, DummyErrorReporter.INSTANCE);
    assertNotNull("failed to read the generated arend.yaml of '" + name + "'", library);
    return library;
  }

  private @NotNull ArendExtension extensionOf(String libraryName) {
    ArendExtension extension = server.getExtensionProvider().getArendExtension(libraryName);
    assertNotNull("library '" + libraryName + "' is not loaded", extension);
    return extension;
  }

  /** Runs the loader over {@code requested} exactly as {@code ConsoleMain} does for the command line. */
  private boolean load(SourceLibrary... requested) {
    Set<String> loading = new HashSet<>();
    for (SourceLibrary library : requested) {
      if (!consoleMain.loadLibraryWithDependencies(library, libraryManager, List.of(libDir), server, loading)) return false;
    }
    return true;
  }

  /**
   * The bug from the report: a project whose only declared dependency carries the extension must see
   * that extension, no matter that the project is the library the user asked for.
   */
  @Test
  public void dependencyExtensionReachesRequestedLibrary() throws IOException {
    writeLibrary("dep", List.of(), DependencyExtension.class.getName());
    SourceLibrary project = writeLibrary("proj", List.of("dep"), null);

    assertTrue(load(project));

    assertSame("the dependency's extension must be the one loaded from the test classpath",
        DependencyExtension.class, extensionOf("dep").getClass());
    assertSame("the requested library must delegate literal typechecking to its dependency",
        extensionOf("dep").getLiteralTypechecker(), extensionOf("proj").getLiteralTypechecker());
    assertNotNull(extensionOf("proj").getLiteralTypechecker());
  }

  /** The dependency is loaded before the library that depends on it, not after. */
  @Test
  public void dependenciesAreLoadedFirst() throws IOException {
    writeLibrary("dep", List.of(), DependencyExtension.class.getName());
    SourceLibrary project = writeLibrary("proj", List.of("dep"), null);

    assertTrue(load(project));

    assertEquals("dependencies must be loaded before their dependents",
        List.of("dep", "proj"), loadOrder);
  }

  /** A transitive dependency's extension reaches the top library through the intermediate one. */
  @Test
  public void transitiveDependencyExtensionReachesRequestedLibrary() throws IOException {
    writeLibrary("base", List.of(), DependencyExtension.class.getName());
    writeLibrary("mid", List.of("base"), null);
    SourceLibrary top = writeLibrary("top", List.of("mid"), null);

    assertTrue(load(top));

    assertEquals("the whole chain must be loaded bottom-up",
        List.of("base", "mid", "top"), loadOrder);
    assertSame("the literal typechecker must reach the top library through 'mid'",
        extensionOf("base").getLiteralTypechecker(), extensionOf("top").getLiteralTypechecker());
  }

  /**
   * A library that is both requested explicitly and depended upon must be loaded exactly once:
   * reloading it would construct a second extension, leaving its dependents wired to a dead instance.
   */
  @Test
  public void libraryRequestedAndDependedUponIsLoadedOnce() throws IOException {
    SourceLibrary dep = writeLibrary("dep", List.of(), DependencyExtension.class.getName());
    SourceLibrary project = writeLibrary("proj", List.of("dep"), null);

    assertTrue(load(project, dep));

    assertEquals("'dep' must not be loaded a second time as a requested library",
        List.of("dep", "proj"), loadOrder);
    assertEquals("the dependency's extension must be constructed exactly once",
        1, DependencyExtension.instances);
    assertSame("'proj' must still point at the live extension instance of 'dep'",
        extensionOf("dep").getLiteralTypechecker(), extensionOf("proj").getLiteralTypechecker());
  }

  /**
   * The documented procedure for custom extensions: a project extension that extends
   * {@link DefaultArendExtension} inherits the literal typechecker of its dependency, so string
   * literals from arend-lib keep elaborating in a project with its own extension.
   */
  @Test
  public void inheritingExtensionSeesDependencyLiteralTypechecker() throws IOException {
    writeLibrary("dep", List.of(), DependencyExtension.class.getName());
    SourceLibrary project = writeLibrary("proj", List.of("dep"), InheritingExtension.class.getName());

    assertTrue(load(project));

    assertSame("an extension extending DefaultArendExtension must inherit the literal typechecker of its dependency",
        extensionOf("dep").getLiteralTypechecker(), extensionOf("proj").getLiteralTypechecker());
    assertNotNull(extensionOf("proj").getLiteralTypechecker());
  }

  /** A custom extension that provides its own literal typechecker must not be overridden by a dependency. */
  @Test
  public void overridingExtensionKeepsItsOwnLiteralTypechecker() throws IOException {
    writeLibrary("dep", List.of(), DependencyExtension.class.getName());
    SourceLibrary project = writeLibrary("proj", List.of("dep"), OverridingExtension.class.getName());

    assertTrue(load(project));

    assertSame("the library's own literal typechecker must take precedence over the dependency's",
        OverridingExtension.OWN_TYPECHECKER, extensionOf("proj").getLiteralTypechecker());
  }

  /** A dependency cycle must terminate instead of recursing forever, still loading both libraries. */
  @Test
  public void dependencyCycleTerminates() throws IOException {
    writeLibrary("a", List.of("b"), null);
    SourceLibrary b = writeLibrary("b", List.of("a"), null);

    assertTrue(load(b));

    assertEquals(List.of("a", "b"), loadOrder);
  }

  /** A missing dependency is reported as a failure and its dependent is not loaded. */
  @Test
  public void missingDependencyFailsAndLeavesDependentUnloaded() throws IOException {
    SourceLibrary project = writeLibrary("proj", List.of("absent"), null);

    assertFalse("a library whose dependency cannot be found must not be loaded", load(project));
    assertEquals(List.of(), loadOrder);
    assertNull(server.getExtensionProvider().getArendExtension("proj"));
  }
}
