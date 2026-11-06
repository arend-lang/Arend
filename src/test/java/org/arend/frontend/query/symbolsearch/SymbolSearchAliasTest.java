package org.arend.frontend.query.symbolsearch;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ListErrorReporter;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.SymbolIndex;
import org.arend.frontend.query.SymbolPattern;
import org.arend.frontend.query.TargetResolver;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.prelude.Prelude;
import org.arend.server.impl.ArendServerImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A definition declared with an {@code \alias} is written under either name in source, so the
 * query tools must find it under either: {@code -ss} by pattern, and the {@code -fu}/{@code -ch}/
 * {@code -sc} target resolver by spec. The symbol index used to record only the short name, so
 * {@code ∪} (of {@code \func union \alias \infixl 6 ∪}) or {@code Big_∧} (of {@code Big_meet}) were
 * unfindable -- in arend-lib that is every one of its 50 aliases, mostly operators.
 */
public class SymbolSearchAliasTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String MODULE = """
      \\func union \\alias \\infixl 6 ∪ (a b : Nat) : Nat => a

      \\func plain (n : Nat) : Nat => n

      \\func outer : Nat => 0
        \\where {
          \\func Big_meet \\alias Big_∧ (n : Nat) : Nat => n
        }
      """;

  private ArendServerImpl server;
  private LibraryManager libraryManager;
  private SourceLibrary library;

  @Before
  public void setUp() throws IOException {
    libraryManager = new LibraryManager(new ListErrorReporter(new ArrayList<>()));
    // The CLI's requester: it is what loads a source module the first time the index asks for it.
    CliServerRequester requester = new CliServerRequester(libraryManager);
    server = new ArendServerImpl(requester, false, false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    Path libDir = tempFolder.newFolder("libs").toPath();
    Path root = libDir.resolve("lib");
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    Files.writeString(root.resolve("src").resolve("Ops.ard"), MODULE, StandardCharsets.UTF_8);
    library = FileSourceLibrary.fromConfigFile(root.resolve("arend.yaml"), false, DummyErrorReporter.INSTANCE);
    assertNotNull(library);

    CommandContext ctx = new CommandContext();
    ctx.libraryManager = libraryManager;
    ctx.requester = requester;
    ctx.server = server;
    ctx.libDirs.add(libDir);
    assertTrue(CliSetup.loadLibraryWithDependencies(ctx, library, new HashSet<>()));
  }

  private SymbolSearchEngine.Result search(String... patterns) {
    List<SymbolPattern> compiled = new ArrayList<>();
    for (String p : patterns) compiled.add(SymbolPattern.compile(p, true));
    return SymbolSearchEngine.find(compiled, EnumSet.allOf(SymbolIndex.Kind.class), List.of(library), server);
  }

  /** The one hit of {@code pattern}, as {@code longName/matchedName}. */
  private String onlyHit(String pattern) {
    List<SymbolSearchEngine.Hit> hits = search(pattern).hits();
    assertEquals("hits of '" + pattern + "'", 1, hits.size());
    return hits.getFirst().entry().longName() + "/" + hits.getFirst().matchedName();
  }

  @Test
  public void operatorAliasIsFound() {
    assertEquals("union/∪", onlyHit("∪"));
  }

  @Test
  public void aliasIsFoundByLongName() {
    assertEquals("union/∪", onlyHit("Ops.∪"));
    assertEquals("outer.Big_meet/Big_∧", onlyHit("outer.Big_∧"));
  }

  @Test
  public void aliasIsFoundByGlob() {
    assertEquals("outer.Big_meet/Big_∧", onlyHit("glob:Big_∧"));
  }

  /** The short name still matches, and wins when both names do. */
  @Test
  public void shortNameStillMatchesFirst() {
    assertEquals("union/union", onlyHit("union"));
    assertEquals("outer.Big_meet/Big_meet", onlyHit("Big_"));
  }

  /** Ranking measures the name that matched, so a one-character alias is an exact-length hit. */
  @Test
  public void aliasRanksByItsOwnLength() {
    List<SymbolSearchEngine.Hit> hits = search("∪", "plain").hits();
    assertEquals(List.of("∪", "plain"), hits.stream().map(SymbolSearchEngine.Hit::matchedName).toList());
  }

  /** The alias has to survive the on-disk cache, which a later run reads instead of reparsing. */
  @Test
  public void aliasSurvivesTheCache() {
    search("plain");   // builds and saves the index
    SymbolIndex reloaded = SymbolIndex.loadOrCreate(library);
    SymbolIndex.Entry union = reloaded.allEntries().stream()
        .filter(e -> e.shortName().equals("union")).findFirst().orElseThrow();
    assertEquals("∪", union.aliasName());
    SymbolIndex.Entry plain = reloaded.allEntries().stream()
        .filter(e -> e.shortName().equals("plain")).findFirst().orElseThrow();
    assertNull(plain.aliasName());
  }

  @Test
  public void textOutputShowsTheAlias() {
    List<SymbolPattern> patterns = List.of(SymbolPattern.compile("∪", true));
    SymbolSearchEngine.Hit hit = search("∪").hits().getFirst();
    StringBuilder sb = new StringBuilder();
    SymbolSearchRenderer.appendEntry(sb, false, hit.libName(), hit.entry(), libraryManager, patterns);
    String text = sb.toString().replaceAll("\u001B\\[[0-9;]*m", "");
    assertTrue(text, text.contains("Ops:union \\alias ∪  [FUNCTION]\n"));
  }

  @Test
  public void jsonOutputCarriesTheAlias() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    new JsonSymbolSearchPrinter().print(new PrintStream(bytes, true, StandardCharsets.UTF_8),
        search("∪", "plain"), new SymbolSearchTool.Options(), false, libraryManager);
    String json = bytes.toString(StandardCharsets.UTF_8);
    assertTrue(json, json.contains("\"name\":\"union\",\"alias\":\"∪\",\"kind\":\"FUNCTION\""));
    assertTrue(json, json.contains("\"name\":\"plain\",\"kind\":\"FUNCTION\""));
  }

  private String resolve(String spec) {
    Map<SourceLibrary, SymbolIndex> indexes = SymbolIndex.refreshAll(List.of(library), server, false);
    TargetResolver.Target target = TargetResolver.resolve(spec, "-fu", server, List.of(library), indexes,
        false, null, "definition");
    return target == null ? null : target.referable().getRefLongName().toString();
  }

  @Test
  public void resolverAcceptsAliasInModuleSpec() {
    assertEquals("union", resolve("Ops:∪"));
    assertEquals("outer.Big_meet", resolve("Ops:outer.Big_∧"));
  }

  @Test
  public void resolverAcceptsAliasAsBareName() {
    assertEquals("union", resolve("∪"));
    assertEquals("outer.Big_meet", resolve("outer.Big_∧"));
    assertNull(resolve("inner.Big_∧"));
  }
}
