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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Round-trip tests for the on-disk symbol-index format.
 *
 * <p>The format escapes {@code \}, {@code |}, {@code \n} and {@code \r} so an entry always
 * occupies one physical line with seven pipe-separated fields. The reader has to honour that
 * escaping: an earlier version split on a plain {@code |} and so silently dropped every
 * definition whose name contains one -- in arend-lib that is {@code ||}, {@code ||-search},
 * {@code ||-finiteAC} and the {@code GCD.res|val1} fields, which were findable on a cold
 * index and gone as soon as the cache was read back.
 */
public class SymbolIndexStoreTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private static final ModulePath MODULE = ModulePath.fromString("Test.Module");

  private static SymbolIndex.Entry entry(String shortName, String longName, String signature) {
    return new SymbolIndex.Entry(shortName, longName, SymbolIndex.Kind.LEMMA, MODULE,
        "/src/Test/Module.ard", 12, 3, signature);
  }

  /** Writes {@code entries} to a fresh cache file and reads them straight back. */
  private List<SymbolIndex.Entry> roundTrip(List<SymbolIndex.Entry> entries) throws IOException {
    Path file = folder.newFolder().toPath().resolve(".arend-symbol-index");
    Map<ModulePath, SymbolIndex.FileStamp> stamps = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> byModule = new LinkedHashMap<>();
    stamps.put(MODULE, new SymbolIndex.FileStamp(1_000L, 42L));
    byModule.put(MODULE, entries);
    SymbolIndexStore.write(file, "test-lib", stamps, byModule);
    assertTrue("cache file was not written", Files.isRegularFile(file));

    Map<ModulePath, SymbolIndex.FileStamp> readStamps = new LinkedHashMap<>();
    Map<ModulePath, List<SymbolIndex.Entry>> readEntries = new LinkedHashMap<>();
    SymbolIndexStore.load(file, readStamps, readEntries);
    assertEquals(stamps, readStamps);
    List<SymbolIndex.Entry> out = readEntries.get(MODULE);
    return out == null ? new ArrayList<>() : out;
  }

  private void assertSurvives(SymbolIndex.Entry e) throws IOException {
    assertEquals(List.of(e), roundTrip(List.of(e)));
  }

  @Test
  public void plainEntrySurvives() throws IOException {
    assertSurvives(entry("idNat", "idNat", "\\func idNat (x : Nat) : Nat"));
  }

  /** The regression: a pipe in the short name must not be read as a field separator. */
  @Test
  public void pipeInShortNameSurvives() throws IOException {
    assertSurvives(entry("||-search", "KFinSet.||-search", "\\lemma ||-search {A : \\Type} : A || B"));
  }

  @Test
  public void pipeInLongNameSurvives() throws IOException {
    assertSurvives(entry("res|val1", "GCD.res|val1", "| res|val1 : LDiv res val1"));
  }

  /** A bare {@code ||} definition: every field boundary is adjacent to an escaped pipe. */
  @Test
  public void operatorNamedOnlyPipesSurvives() throws IOException {
    assertSurvives(entry("||", "||", "\\data \\infixr 2 || (A B : \\Type) : \\Prop"));
  }

  @Test
  public void backslashAndPipeTogetherSurvive() throws IOException {
    assertSurvives(entry("a\\b|c", "M.a\\b|c", "\\func a\\b|c : \\Sigma \\lp \\lh"));
  }

  /** Container signatures are multi-line; they must come back with newlines intact. */
  @Test
  public void multiLineSignatureSurvives() throws IOException {
    assertSurvives(entry("Pair", "Pair", "\\record Pair {\n  | fst : Nat\n  | snd : Nat\n}"));
  }

  /** A path containing a pipe is legal on POSIX and must not shift the field layout. */
  @Test
  public void pipeInFilePathSurvives() throws IOException {
    SymbolIndex.Entry e = new SymbolIndex.Entry("f", "f", SymbolIndex.Kind.FUNCTION, MODULE,
        "/src/od|d/Module.ard", 1, 1, "\\func f : Nat");
    assertSurvives(e);
  }

  /** Every entry must survive when written together, and keep its order. */
  @Test
  public void mixedBatchSurvivesInOrder() throws IOException {
    List<SymbolIndex.Entry> entries = List.of(
        entry("idNat", "idNat", "\\func idNat : Nat"),
        entry("||-search", "KFinSet.||-search", "\\lemma ||-search : A || B"),
        entry("||", "||", "\\data || (A B : \\Type)"),
        entry("res|val1", "GCD.res|val1", "| res|val1 : LDiv"),
        entry("plain", "plain", ""));
    assertEquals(entries, roundTrip(entries));
  }

  /** A cache written by an older format version is rejected wholesale, not half-read. */
  @Test
  public void foreignHeaderIsRejected() throws IOException {
    Path file = folder.newFolder().toPath().resolve(".arend-symbol-index");
    Files.writeString(file, "# arend symbol index v4\nlibrary: test-lib\n");
    try {
      SymbolIndexStore.load(file, new LinkedHashMap<>(), new LinkedHashMap<>());
      throw new AssertionError("expected an IOException for a stale header");
    } catch (IOException expected) {
      // the caller turns this into "start from an empty index"
    }
  }
}
