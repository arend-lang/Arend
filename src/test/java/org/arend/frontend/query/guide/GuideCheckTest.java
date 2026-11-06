package org.arend.frontend.query.guide;

import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.junit.After;
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
import java.util.List;

import static org.junit.Assert.*;

/**
 * {@code -gc} end to end: a library with a guide page, stamped, then edited the ways a library
 * gets edited. What has to hold is that the check stays quiet for edits that cannot make an entry
 * wrong (a proof, the layout, a comment) and speaks up for the ones that can.
 */
public class GuideCheckTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path libRoot;
  private PrintStream oldOut;
  private PrintStream oldErr;

  private static final String MODULE = """
      \\data Color | red | green

      \\func swap (c : Color) : Color
        | red => green
        | green => red

      \\lemma swap-swap (c : Color) : swap (swap c) = c
        | red => idp
        | green => idp

      \\func helper : Nat => 0
      """;

  private static final String PAGE = """
      ### Colors

      Two colors and the `swap` between them.

      - **`Color`**: Two colors.
      - **`swap`**: Exchanges `red` and `green`.
      - **`swap-swap`**: `swap` is an involution.
      """;

  @Before
  public void setUp() throws IOException {
    libRoot = tempFolder.newFolder("lib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    writeModule(MODULE);
    writePage("Colors.md", PAGE);
    oldOut = System.out;
    oldErr = System.err;
  }

  @After
  public void tearDown() {
    System.setOut(oldOut);
    System.setErr(oldErr);
  }

  private void writeModule(String text) throws IOException {
    Files.writeString(libRoot.resolve("src/Colors.ard"), text, StandardCharsets.UTF_8);
  }

  private void writePage(String name, String text) throws IOException {
    Path page = libRoot.resolve(".aiGuide").resolve(name);
    Files.createDirectories(page.getParent());
    Files.writeString(page, text, StandardCharsets.UTF_8);
  }

  private record Run(int exitCode, String output) {}

  /** Runs one command against a freshly loaded context, so every edit on disk is seen. */
  private Run run(String... args) {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);
    System.setOut(sink);
    System.setErr(sink);
    int exitCode;
    try {
      CommandContext ctx = new ConsoleMain().warmContext(new String[]{libRoot.toString()});
      assertNotNull("loading the library failed:\n" + captured.toString(StandardCharsets.UTF_8), ctx);
      exitCode = Dispatch.run(ctx, args);
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return new Run(exitCode, captured.toString(StandardCharsets.UTF_8));
  }

  private Run stampedThenEdited(String newModule) throws IOException {
    Run stamp = run("-gc", "stamp");
    assertEquals(stamp.output(), 0, stamp.exitCode());
    writeModule(newModule);
    return run("-gc");
  }

  @Test
  public void anUnstampedGuideFailsTheCheck() {
    Run check = run("-gc");
    assertEquals(check.output(), 1, check.exitCode());
    assertTrue(check.output(), check.output().contains("UNSTAMPED swap-swap [lemma]"));
  }

  @Test
  public void aStampedGuidePasses() {
    run("-gc", "stamp");
    Run check = run("-gc");
    assertEquals(check.output(), 0, check.exitCode());
    assertTrue(check.output(), check.output().contains("the guide is up to date"));
    assertTrue("the fingerprints live next to the pages, not in them",
        Files.isRegularFile(libRoot.resolve(".aiGuide/.fingerprints/Colors.tsv")));
  }

  @Test
  public void aNewProofIsNotAChange() throws IOException {
    Run check = stampedThenEdited(MODULE.replace("""
        \\lemma swap-swap (c : Color) : swap (swap c) = c
          | red => idp
          | green => idp""", """
        \\lemma swap-swap (c : Color) : swap (swap c) = c \\elim c
          | red => idp
          | green => idp"""));
    assertEquals(check.output(), 0, check.exitCode());
  }

  @Test
  public void layoutAndCommentsAreNotAChange() throws IOException {
    Run check = stampedThenEdited("-- a comment\n" + MODULE.replace("(c : Color) : Color", "(c : Color)\n    : Color"));
    assertEquals(check.output(), 0, check.exitCode());
  }

  @Test
  public void aNewStatementIsReportedWithTheNewSignature() throws IOException {
    Run check = stampedThenEdited(MODULE.replace("swap (swap c) = c", "swap (swap (swap (swap c))) = c"));
    assertEquals(check.output(), 1, check.exitCode());
    assertTrue(check.output(), check.output().contains("SIGNATURE swap-swap [lemma]"));
    assertTrue(check.output(), check.output().contains("swap (swap (swap (swap c))) = c"));
  }

  /**
   * A \func's body is its meaning: changing it flags the entry, and every entry that mentions it
   * is flagged for review -- here the lemma whose description says `swap`, and the page's prose.
   */
  @Test
  public void aNewFunctionBodyFlagsItAndWhatMentionsIt() throws IOException {
    Run check = stampedThenEdited(MODULE.replace("| green => red", "| green => green"));
    assertEquals(check.output(), 1, check.exitCode());
    assertTrue(check.output(), check.output().contains("DEFINITION swap [func]"));
    assertTrue(check.output(), check.output().contains("REVIEW swap-swap [lemma]"));
    assertTrue(check.output(), check.output().contains("REVIEW (overview)"));
  }

  /** A \data type's constructors are its meaning, and they are part of its signature. */
  @Test
  public void aNewConstructorChangesTheDataType() throws IOException {
    Run check = stampedThenEdited(MODULE.replace("| red | green", "| red | green | blue"));
    assertTrue(check.output(), check.output().contains("SIGNATURE Color [data]"));
  }

  @Test
  public void aRenameIsTracedThroughTheRecordedStatement() throws IOException {
    Run check = stampedThenEdited(MODULE.replace("swap-swap", "swap-involutive"));
    assertEquals(check.output(), 1, check.exitCode());
    assertTrue(check.output(), check.output().contains("GONE swap-swap"));
    assertTrue(check.output(), check.output().contains("the recorded statement is now Colors:swap-involutive"));
  }

  @Test
  public void stampingOneEntryLeavesTheOthersFlagged() throws IOException {
    run("-gc", "stamp");
    writeModule(MODULE.replace("swap (swap c) = c", "swap (swap (swap (swap c))) = c")
        .replace("| red | green", "| red | green | blue"));
    Run partial = run("-gc", "stamp", "in=Colors:swap-swap");
    assertEquals(partial.output(), 0, partial.exitCode());
    Run check = run("-gc");
    assertFalse(check.output(), check.output().contains("SIGNATURE swap-swap"));
    assertTrue(check.output(), check.output().contains("SIGNATURE Color [data]"));
  }

  /** page= is one page: unlike in=, it leaves the pages of submodules alone. */
  @Test
  public void pageSelectsExactlyOnePage() throws IOException {
    Files.createDirectories(libRoot.resolve("src/Colors"));
    Files.writeString(libRoot.resolve("src/Colors/Dark.ard"), "\\func dark : Nat => 1\n", StandardCharsets.UTF_8);
    writePage("Colors/Dark.md", "### Colors.Dark\n\n- **`dark`**: Dark.\n");
    run("-gc", "stamp", "page=Colors");
    String check = run("-gc").output();
    assertTrue(check, check.contains("UNSTAMPED dark [func]"));
    assertFalse(check, check.contains("UNSTAMPED swap"));
  }

  @Test
  public void undocumentedDefinitionsAndMissingPagesAreCountedNotFailed() throws IOException {
    Files.writeString(libRoot.resolve("src/Extra.ard"), "\\func extra : Nat => 1\n", StandardCharsets.UTF_8);
    run("-gc", "stamp");
    Run check = run("-gc");
    assertEquals(check.output(), 0, check.exitCode());
    assertTrue(check.output(), check.output().contains("1 UNDOCUMENTED"));
    assertTrue(check.output(), check.output().contains("1 NO-PAGE"));
    Run strict = run("-gc", "strict", "undocumented");
    assertEquals(strict.output(), 1, strict.exitCode());
    assertTrue(strict.output(), strict.output().contains("UNDOCUMENTED helper [func]"));
  }

  @Test
  public void aPageForAMissingModuleIsReported() throws IOException {
    writePage("Shapes.md", "### Shapes\n\n- **`circle`**: A circle.\n");
    Run check = run("-gc");
    assertTrue(check.output(), check.output().contains("PAGE-GONE Shapes"));
  }

  /** The report is one JSON document on one line (this harness captures the loader's chatter too). */
  /** A page left behind by a module rename points at the module its names moved to. */
  @Test
  public void aPageForARenamedModuleSaysWhereItsNamesWent() throws IOException {
    writePage("Hues.md", "### Hues\n\n- **`Color`**: w.\n- **`swap`**: x.\n- **`swap-swap`**: y.\n- **`gone`**: z.\n");
    Run check = run("-gc");
    assertTrue(check.output(), check.output().contains("PAGE-GONE Hues"));
    assertTrue(check.output(), check.output().contains("3 of the page's 4 names are now in Colors"));
  }

  @Test
  public void jsonIsOneDocument() {
    Run check = run("-gc", "--json");
    String json = check.output().lines().filter(l -> l.startsWith("{\"library\":")).findFirst().orElse("");
    assertTrue(check.output(), json.endsWith("]}"));
    assertTrue(json, json.contains("\"problem\":\"UNSTAMPED\""));
    assertEquals(check.output(), 1, check.exitCode());
  }

  /** The first command of a fresh context: it runs before anything is typechecked or resolved. */
  private Run runCold(String... gcArgs) {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);
    System.setOut(sink);
    System.setErr(sink);
    try {
      String[] args = new String[gcArgs.length + 1];
      args[0] = libRoot.toString();
      System.arraycopy(gcArgs, 0, args, 1, gcArgs.length);
      new ConsoleMain().warmContext(args);
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return new Run(0, captured.toString(StandardCharsets.UTF_8));
  }

  /**
   * Fingerprints do not depend on whether the library was typechecked first (a daemon, the REPL):
   * resolution rewrites references in place, and an operator used as a value prints differently
   * once resolved.
   */
  @Test
  public void fingerprintsAreTheSameWarmOrCold() throws IOException {
    // A \default with parameters becomes an implementation by a generated function, whose
    // reference prints one way unresolved and another resolved (as DecSet's `# => (#)` did).
    writeModule(MODULE + "\\class Apart (E : \\Set) | \\infix 4 # : E -> E -> \\Prop\n"
        + "\\class DecApart \\extends Apart {\n  \\default # x y : \\Prop => x = y\n}\n");
    writePage("Colors.md", PAGE + "- **`DecApart`**: Apartness defaulting to equality.\n");
    run("-gc", "stamp");           // warm: the library was typechecked before this command
    Run cold = runCold("-gc");      // cold: nothing resolved
    assertTrue(cold.output(), cold.output().contains("the guide is up to date"));
  }

  // ---- v2 fingerprints ------------------------------------------------------

  @Test
  public void aChangedStatementShowsWhatItWas() throws IOException {
    Run check = stampedThenEdited(MODULE.replace("swap (swap c) = c", "swap (swap (swap (swap c))) = c"));
    assertTrue(check.output(), check.output().contains("was: \\lemma swap-swap (c : Color) : swap (swap c) = c"));
  }

  // ---- fix ----------------------------------------------------------------

  private String page(String name) throws IOException {
    return Files.readString(libRoot.resolve(".aiGuide").resolve(name), StandardCharsets.UTF_8);
  }

  @Test
  public void fixDeletesAnEntryGoneWithoutATraceButNotInADryRun() throws IOException {
    writePage("Colors.md", PAGE + "- **`paint`**: Paints.\n");
    Run dry = run("-gc", "fix", "dry-run");
    assertTrue(dry.output(), dry.output().contains("DELETE paint"));
    assertTrue("a dry run writes nothing", page("Colors.md").contains("paint"));
    run("-gc", "fix");
    assertFalse(page("Colors.md").contains("paint"));
    assertTrue("the other entries stay", page("Colors.md").contains("- **`swap`**"));
  }

  @Test
  public void fixDropsAVanishedNameFromAnEntryThatNamesOthers() throws IOException {
    writePage("Colors.md", PAGE.replace("- **`swap`**:", "- **`swap`**, **`paint`**:"));
    run("-gc", "fix");
    assertTrue(page("Colors.md"), page("Colors.md").contains("- **`swap`**: Exchanges"));
  }

  @Test
  public void fixMovesAnEntryAfterItsDefinitionAndLeavesItToConfirm() throws IOException {
    run("-gc", "stamp");
    writeModule(MODULE.replace("\\func helper : Nat => 0\n", ""));
    Files.writeString(libRoot.resolve("src/Colors.ard"), MODULE.replace(
        "\\lemma swap-swap (c : Color) : swap (swap c) = c\n  | red => idp\n  | green => idp\n", ""), StandardCharsets.UTF_8);
    Files.writeString(libRoot.resolve("src/Involution.ard"), "\\import Colors\n"
        + "\\lemma swap-swap (c : Color) : swap (swap c) = c\n  | red => idp\n  | green => idp\n", StandardCharsets.UTF_8);
    Run fix = run("-gc", "fix");
    assertTrue(fix.output(), fix.output().contains("MOVE swap-swap"));
    assertFalse(page("Colors.md").contains("swap-swap"));
    assertTrue(page("Involution.md").startsWith("### Involution"));
    assertTrue(page("Involution.md").contains("- **`swap-swap`**: `swap` is an involution."));
    assertTrue(run("-gc").output().contains("UNSTAMPED swap-swap [lemma]"));
  }

  @Test
  public void fixRenamesThePageOfARenamedModule() throws IOException {
    run("-gc", "stamp");
    Files.move(libRoot.resolve("src/Colors.ard"), libRoot.resolve("src/Hues.ard"));
    Run fix = run("-gc", "fix");
    assertTrue(fix.output(), fix.output().contains("MOVE-PAGE Colors"));
    assertTrue(page("Hues.md").startsWith("### Hues"));
    assertFalse(Files.exists(libRoot.resolve(".aiGuide/Colors.md")));
    // The fingerprints moved with it: same definitions, so nothing is left to do.
    Run check = run("-gc");
    assertEquals(check.output(), 0, check.exitCode());
  }

  /**
   * A page whose module is gone, with no module holding most of its names, is never deleted
   * whole: what names nothing anywhere goes, what may be elsewhere stays to be placed by hand.
   */
  @Test
  public void fixThinsAPageWhoseNamesWentDifferentWays() throws IOException {
    Files.writeString(libRoot.resolve("src/Extra.ard"), "\\func extra : Nat => 1\n", StandardCharsets.UTF_8);
    writePage("Gone.md", "### Gone\n\n- **`swap`**: x.\n- **`extra`**: y.\n- **`nothing`**: z.\n");
    Run fix = run("-gc", "fix");
    assertTrue(fix.output(), fix.output().contains("THIN-PAGE Gone"));
    String gone = page("Gone.md");
    assertTrue(gone, gone.contains("**`swap`**") && gone.contains("**`extra`**"));
    assertFalse(gone, gone.contains("nothing"));
    Run tasks = run("-gc", "tasks", "--json");
    assertTrue(tasks.output(), tasks.output().contains("\"do\":\"FIX-PAGE\",\"why\":[\"PAGE-GONE\"],\"name\":\"extra\""));
  }

  @Test
  public void fixDropsTheFingerprintsOfADeletedPage() throws IOException {
    run("-gc", "stamp");
    Files.delete(libRoot.resolve(".aiGuide/Colors.md"));
    Run fix = run("-gc", "fix");
    assertTrue(fix.output(), fix.output().contains("DROP-FINGERPRINTS Colors.md"));
    assertFalse(Files.exists(libRoot.resolve(".aiGuide/.fingerprints/Colors.tsv")));
  }

  @Test
  public void fixQualifiesANameThatBecameAmbiguous() throws IOException {
    String withWhere = MODULE.replace("\\func helper : Nat => 0", "\\func helper : Nat => 0 \\where \\func tint : Nat => 1");
    writeModule(withWhere);
    writePage("Colors.md", PAGE + "- **`tint`**: A tint.\n");
    run("-gc", "stamp");
    writeModule(withWhere + "\\func other : Nat => 2 \\where \\func tint : Nat => 3\n");
    assertTrue(run("-gc").output().contains("AMBIGUOUS tint"));
    Run fix = run("-gc", "fix");
    assertTrue(fix.output(), fix.output().contains("QUALIFY tint"));
    assertTrue(page("Colors.md"), page("Colors.md").contains("- **`helper.tint`**: A tint."));
  }

  // ---- tasks --------------------------------------------------------------

  @Test
  public void tasksHandOverWhatAnEntryNeeds() throws IOException {
    Run stamp = run("-gc", "stamp");
    assertEquals(stamp.output(), 0, stamp.exitCode());
    writeModule(MODULE.replace("swap (swap c) = c", "swap (swap (swap (swap c))) = c"));
    Run tasks = run("-gc", "tasks", "out=" + libRoot.resolve("tasks"));
    assertEquals(tasks.output(), 0, tasks.exitCode());
    String packet = Files.readString(libRoot.resolve("tasks/Colors.json"), StandardCharsets.UTF_8);
    assertTrue(packet, packet.contains("\"do\":\"VERIFY\",\"why\":[\"SIGNATURE\"],\"name\":\"swap-swap\""));
    assertTrue(packet, packet.contains("\"was\":\"\\\\lemma swap-swap (c : Color) : swap (swap c) = c\""));
    assertTrue(packet, packet.contains("\"do\":\"WRITE\",\"why\":[\"UNDOCUMENTED\"],\"name\":\"helper\""));
    assertTrue(Files.readString(libRoot.resolve("tasks/index.json"), StandardCharsets.UTF_8).contains("\"packet\":\"Colors.json\""));
  }

  @Test
  public void aModuleWithoutAPageGetsAPacketOfWrites() throws IOException {
    Files.writeString(libRoot.resolve("src/Extra.ard"), "\\func extra : Nat => 1\n", StandardCharsets.UTF_8);
    Run tasks = run("-gc", "tasks", "--json");
    String json = tasks.output().lines().filter(l -> l.startsWith("{\"guide\":")).findFirst().orElse("");
    assertTrue(tasks.output(), json.contains("\"page\":\"Extra.md\""));
    assertTrue(json, json.contains("\"newPage\":true"));
  }

  /** Helpers in \where blocks are not expected on a page unless asked for, as the guide was written. */
  @Test
  public void whereHelpersAreNotExpectedByDefault() throws IOException {
    writeModule(MODULE.replace("\\func helper : Nat => 0", "\\func helper : Nat => 0 \\where \\func inner : Nat => 1"));
    run("-gc", "stamp");
    assertTrue(run("-gc").output().contains("1 UNDOCUMENTED"));
    assertTrue(run("-gc", "helpers").output().contains("2 UNDOCUMENTED"));
  }

  @Test
  public void versionOneFingerprintsAreStillRead() throws IOException {
    run("-gc", "stamp");
    Path file = libRoot.resolve(".aiGuide/.fingerprints/Colors.tsv");
    StringBuilder v1 = new StringBuilder();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (line.startsWith("#")) continue;
      v1.append(line, 0, line.lastIndexOf('\t')).append('\n');
    }
    Files.writeString(file, v1.toString(), StandardCharsets.UTF_8);
    Run check = run("-gc");
    assertEquals(check.output(), 0, check.exitCode());
  }

  // ---- page parsing -------------------------------------------------------

  @Test
  public void entryHeaders() {
    assertEquals(List.of("a", "b"), names("**`a`**, **`b`**: text"));
    assertEquals(List.of("<=-transitive", "<=∘"), names("**`<=-transitive`** (alias **`<=∘`**): text"));
    assertEquals(List.of("o"), names("**`o`** (infixr 8): text"));
    assertEquals(List.of("SFunc"), names("**`SFunc V B`**: text"));
    assertEquals(List.of("nil<::"), names("**`nil<::`**: text"));
    assertEquals(List.of("implies", "-->"), names("**`implies` / `-->`**: text"));
    assertEquals(List.of("and"), names("**`and`**: Boolean conjunction."));
    assertEquals(List.of("and", "or"), names("**`and`** / **`or`**: text"));
    assertEquals(List.of("Equalizer.Arrows", "Shape", "functor"), names("**`Equalizer.Arrows` / `Shape` / `functor`**: text"));
    assertEquals(List.of("Meets"), names("**`Meets`** (in `\\where`): text; **`Meets-isProp`** says more"));
  }

  private static List<String> names(String header) {
    return GuidePage.headerNames(header, new int[1]);
  }

  @Test
  public void theOwnNameIsLeftOutOfTheSignatureHash() {
    assertEquals("\\lemma _ (c : Color) : swap (swap c) = c",
        ModuleDefinitions.withoutOwnName("\\lemma swap-swap (c : Color) : swap (swap c) = c", "swap-swap"));
    // Only the declared name: a later use of it (a recursive type) is part of the meaning.
    assertEquals("\\data _ | nil | cons Nat List",
        ModuleDefinitions.withoutOwnName("\\data List | nil | cons Nat List", "List"));
  }
}
