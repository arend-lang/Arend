package org.arend.frontend.query;

import org.arend.ext.module.ModulePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Round-trip tests for the on-disk symbol-index format.
 *
 * <p>An entry line holds six pipe-separated fields and escapes {@code \}, {@code |}, {@code \n}
 * and {@code \r} so it always occupies one physical line. The reader has to honour that escaping:
 * an earlier version split on a plain {@code |} and so silently dropped every definition whose
 * name contains one -- in arend-lib that is {@code ||}, {@code ||-search}, {@code ||-finiteAC}
 * and the {@code GCD.res|val1} fields, which were findable on a cold index and gone as soon as
 * the cache was read back.
 *
 * <p>An entry's source file is <em>not</em> one of those fields: every entry of a module shares
 * it, so it is written once on the module line. That line is space-delimited while a path may
 * contain spaces, hence the tests below that put one there.
 */
public class SymbolIndexStoreTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private static final ModulePath MODULE = ModulePath.fromString("Test.Module");
  private static final String FILE = "/src/Test/Module.ard";

  private static SymbolIndex.Entry entry(String shortName, String longName) {
    return entry(shortName, longName, FILE, "\\lemma " + shortName + " : 0 = 0");
  }

  private static SymbolIndex.Entry entry(String shortName, String longName, String file, String signature) {
    return new SymbolIndex.Entry(shortName, longName, SymbolIndex.Kind.LEMMA, MODULE, file, 12, 3, signature);
  }

  /** Writes {@code entries} (under {@code file}) to a fresh cache file and reads them straight back. */
  private List<SymbolIndex.Entry> roundTrip(String file, List<SymbolIndex.Entry> entries) throws IOException {
    Path cache = folder.newFolder().toPath().resolve(".arend-symbol-index");
    Map<ModulePath, SymbolIndex.FileStamp> stamps = new LinkedHashMap<>();
    Map<ModulePath, String> files = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.ModuleDep>> deps = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> byModule = new LinkedHashMap<>();
    stamps.put(MODULE, new SymbolIndex.FileStamp(1_000L, 42L));
    files.put(MODULE, file);
    byModule.put(MODULE, entries);
    SymbolIndexStore.write(cache, "test-lib", stamps, files, deps, byModule);
    assertTrue("cache file was not written", Files.isRegularFile(cache));

    Map<ModulePath, SymbolIndex.FileStamp> readStamps = new LinkedHashMap<>();
    Map<ModulePath, String> readFiles = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.ModuleDep>> readDeps = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> readEntries = new LinkedHashMap<>();
    SymbolIndexStore.load(cache, readStamps, readFiles, readDeps, readEntries);
    assertEquals(stamps, readStamps);
    assertEquals(files, readFiles);
    List<SymbolIndex.Entry> out = readEntries.get(MODULE);
    return out == null ? new ArrayList<>() : out;
  }

  private List<SymbolIndex.Entry> roundTrip(List<SymbolIndex.Entry> entries) throws IOException {
    return roundTrip(FILE, entries);
  }

  private void assertSurvives(SymbolIndex.Entry e) throws IOException {
    assertEquals(List.of(e), roundTrip(e.absoluteFile(), List.of(e)));
  }

  @Test
  public void plainEntrySurvives() throws IOException {
    assertSurvives(entry("idNat", "idNat"));
  }

  /** The regression: a pipe in the short name must not be read as a field separator. */
  @Test
  public void pipeInShortNameSurvives() throws IOException {
    assertSurvives(entry("||-search", "KFinSet.||-search"));
  }

  @Test
  public void pipeInLongNameSurvives() throws IOException {
    assertSurvives(entry("res|val1", "GCD.res|val1"));
  }

  /** A bare {@code ||} definition: every field boundary is adjacent to an escaped pipe. */
  @Test
  public void operatorNamedOnlyPipesSurvives() throws IOException {
    assertSurvives(entry("||", "||"));
  }

  @Test
  public void backslashAndPipeTogetherSurvive() throws IOException {
    assertSurvives(entry("a\\b|c", "M.a\\b|c"));
  }

  /** A path containing a pipe is legal on POSIX and must not confuse the module line. */
  @Test
  public void pipeInFilePathSurvives() throws IOException {
    assertSurvives(entry("f", "f", "/src/od|d/Module.ard", "\\func f : Nat"));
  }

  /**
   * The module line is space-delimited, so a path with a space is the case that decides whether
   * it is parsed from the left (correct) or from the right (broken).
   */
  @Test
  public void spaceInFilePathSurvives() throws IOException {
    assertSurvives(entry("f", "f", "/home/me/My Arend Libs/arend-lib/src/Test/Module.ard", "\\func f : Nat"));
  }

  /** Paranoid, but the escaping covers it: a newline in a path must not split the line. */
  @Test
  public void newlineInFilePathSurvives() throws IOException {
    assertSurvives(entry("f", "f", "/src/we\nird/Module.ard", "\\func f : Nat"));
  }

  /** An entry with no source file (a generated referable) round-trips with an empty path. */
  @Test
  public void emptyFilePathSurvives() throws IOException {
    List<SymbolIndex.Entry> out = roundTrip("", List.of(entry("rewrite", "rewrite", "", "\\meta rewrite  <generated meta from Paths.Meta>")));
    assertEquals(1, out.size());
    assertEquals("", out.getFirst().absoluteFile());
  }

  /**
   * A container signature is multi-line by design; the escaping has to fold it onto one physical
   * line and unfold it again, or a {@code \class}'s field list would be read as garbage entries.
   */
  @Test
  public void multiLineSignatureSurvives() throws IOException {
    assertSurvives(entry("Pair", "Pair", FILE, "\\record Pair {\n  | fst : Nat\n  | snd : Nat\n}"));
  }

  /** Every entry must survive when written together, and keep its order. */
  @Test
  public void mixedBatchSurvivesInOrder() throws IOException {
    List<SymbolIndex.Entry> entries = List.of(
        entry("idNat", "idNat"),
        entry("||-search", "KFinSet.||-search"),
        entry("||", "||"),
        entry("res|val1", "GCD.res|val1"),
        entry("plain", "plain"));
    assertEquals(entries, roundTrip(entries));
  }

  /** Two modules in one cache file each keep their own path -- the entries must not cross over. */
  @Test
  public void twoModulesKeepTheirOwnFiles() throws IOException {
    ModulePath other = ModulePath.fromString("Test.Other");
    String otherFile = "/src/Test/Other Module.ard";
    Path cache = folder.newFolder().toPath().resolve(".arend-symbol-index");

    Map<ModulePath, SymbolIndex.FileStamp> stamps = new LinkedHashMap<>();
    Map<ModulePath, String> files = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> byModule = new LinkedHashMap<>();
    stamps.put(MODULE, new SymbolIndex.FileStamp(1_000L, 42L));
    stamps.put(other, new SymbolIndex.FileStamp(2_000L, 7L));
    files.put(MODULE, FILE);
    files.put(other, otherFile);
    byModule.put(MODULE, List.of(entry("here", "here")));
    byModule.put(other, List.of(new SymbolIndex.Entry(
        "there", "there", SymbolIndex.Kind.FUNCTION, other, otherFile, 1, 1, "\\func there : Nat")));
    SymbolIndexStore.write(cache, "test-lib", stamps, files, new LinkedHashMap<>(), byModule);

    Map<ModulePath, SymbolIndex.FileStamp> readStamps = new LinkedHashMap<>();
    Map<ModulePath, String> readFiles = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> readEntries = new LinkedHashMap<>();
    SymbolIndexStore.load(cache, readStamps, readFiles, new LinkedHashMap<>(), readEntries);

    assertEquals(files, readFiles);
    assertEquals(FILE, readEntries.get(MODULE).getFirst().absoluteFile());
    assertEquals(otherFile, readEntries.get(other).getFirst().absoluteFile());
  }

  /**
   * The generated bucket is deliberately not persisted: {@code SymbolIndex.refresh} rebuilds it
   * on every run, and writing it would lose each entry's real module path (the reader takes it
   * from the module line, which for the bucket is the synthetic {@code $generated}).
   */
  @Test
  public void generatedBucketIsNotPersisted() throws IOException {
    Path cache = folder.newFolder().toPath().resolve(".arend-symbol-index");
    Map<ModulePath, SymbolIndex.FileStamp> stamps = new LinkedHashMap<>();
    Map<ModulePath, String> files = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> byModule = new LinkedHashMap<>();
    stamps.put(MODULE, new SymbolIndex.FileStamp(1_000L, 42L));
    stamps.put(SymbolIndex.GENERATED_BUCKET, SymbolIndex.GENERATED_STAMP);
    files.put(MODULE, FILE);
    files.put(SymbolIndex.GENERATED_BUCKET, "");
    byModule.put(MODULE, List.of(entry("here", "here")));
    byModule.put(SymbolIndex.GENERATED_BUCKET, List.of(new SymbolIndex.Entry(
        "rewrite", "rewrite", SymbolIndex.Kind.META, ModulePath.fromString("Paths.Meta"), "", 0, 0,
        "\\meta rewrite  <generated meta from Paths.Meta>")));
    SymbolIndexStore.write(cache, "test-lib", stamps, files, new LinkedHashMap<>(), byModule);

    Map<ModulePath, SymbolIndex.FileStamp> readStamps = new LinkedHashMap<>();
    Map<ModulePath, String> readFiles = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> readEntries = new LinkedHashMap<>();
    SymbolIndexStore.load(cache, readStamps, readFiles, new LinkedHashMap<>(), readEntries);

    assertEquals(Set.of(MODULE), readStamps.keySet());
    assertEquals(Set.of(MODULE), readEntries.keySet());
    assertEquals(1, readEntries.get(MODULE).size());
  }

  /**
   * The per-module {@code deps} line round-trips, and a module without one comes back with none.
   * It sits between the module line and the entries and must not be mistaken for either -- note
   * the path with a space above it, which the module line is parsed left-to-right to survive.
   */
  @Test
  public void moduleDepsSurvive() throws IOException {
    ModulePath depA = ModulePath.fromString("Algebra.Ring");
    ModulePath depB = ModulePath.fromString("Order.Lattice");
    ModulePath plain = ModulePath.fromString("Test.NoDeps");
    Path cache = folder.newFolder().toPath().resolve(".arend-symbol-index");

    Map<ModulePath, SymbolIndex.FileStamp> stamps = new LinkedHashMap<>();
    Map<ModulePath, String> files = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.ModuleDep>> deps = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> byModule = new LinkedHashMap<>();
    stamps.put(MODULE, new SymbolIndex.FileStamp(1_000L, 42L));
    stamps.put(plain, new SymbolIndex.FileStamp(3_000L, 9L));
    files.put(MODULE, "/src/My Libs/Test/Module.ard");
    files.put(plain, "/src/Test/NoDeps.ard");
    deps.put(MODULE, List.of(new SymbolIndex.ModuleDep(depA, new SymbolIndex.FileStamp(11L, 22L)),
                             new SymbolIndex.ModuleDep(depB, new SymbolIndex.FileStamp(33L, 44L))));
    byModule.put(MODULE, List.of(entry("C", "C")));
    byModule.put(plain, List.of(entry("D", "D")));
    SymbolIndexStore.write(cache, "test-lib", stamps, files, deps, byModule);

    Map<ModulePath, SymbolIndex.FileStamp> readStamps = new LinkedHashMap<>();
    Map<ModulePath, String> readFiles = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.ModuleDep>> readDeps = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> readEntries = new LinkedHashMap<>();
    SymbolIndexStore.load(cache, readStamps, readFiles, readDeps, readEntries);

    assertEquals(deps.get(MODULE), readDeps.get(MODULE));
    assertEquals(null, readDeps.get(plain));
    assertEquals("/src/My Libs/Test/Module.ard", readFiles.get(MODULE));
    assertEquals(1, readEntries.get(MODULE).size());
    assertEquals(1, readEntries.get(plain).size());
  }

  /** A cache written by an older format version is rejected, so the caller rebuilds from scratch. */
  @Test(expected = IOException.class)
  public void foreignVersionIsRejected() throws IOException {
    Path cache = folder.newFolder().toPath().resolve(".arend-symbol-index");
    Files.writeString(cache, "# arend symbol index v1\nlibrary: test-lib\n");
    SymbolIndexStore.load(cache, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
  }

  /**
   * An entry line whose field count does not match must fail the load, not be skipped. The header
   * does not change when a field is added or dropped, so this is the only thing standing between a
   * differently-shaped cache and a cache that looks fresh but has silently lost its definitions.
   */
  @Test(expected = IOException.class)
  public void wrongFieldCountIsRejected() throws IOException {
    Path cache = folder.newFolder().toPath().resolve(".arend-symbol-index");
    Files.writeString(cache, "# arend symbol index v5\nlibrary: test-lib\n"
        + "module Test.Module 1000 42 /src/Test/Module.ard\n"
        + "  idNat|idNat|LEMMA|12|3\n");   // five fields: no signature
    SymbolIndexStore.load(cache, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
  }
}
