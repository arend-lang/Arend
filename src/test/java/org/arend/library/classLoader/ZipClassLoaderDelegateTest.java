package org.arend.library.classLoader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class ZipClassLoaderDelegateTest {
  @Rule
  public final TemporaryFolder myFolder = new TemporaryFolder();

  private static final byte[] FOO_BYTES = {1, 2, 3, 4};
  private static final byte[] BAR_BYTES = {5, 6, 7};

  private File writeZip(Map<String, byte[]> entries) throws IOException {
    File file = myFolder.newFile("lib.zip");
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(entry.getKey()));
        out.write(entry.getValue());
        out.closeEntry();
      }
    }
    return file;
  }

  private static Map<String, byte[]> entries(Object... pairs) {
    Map<String, byte[]> result = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      result.put((String) pairs[i], (byte[]) pairs[i + 1]);
    }
    return result;
  }

  @Test
  public void findsClassUnderPrefix() throws Exception {
    File zip = writeZip(entries("ext/org/arend/Foo.class", FOO_BYTES));
    assertArrayEquals(FOO_BYTES, new ZipClassLoaderDelegate(zip, "ext").findClass("org.arend.Foo"));
  }

  @Test
  public void prefixNormalizationIsStable() throws Exception {
    File zip = writeZip(entries("ext/org/arend/Foo.class", FOO_BYTES));
    assertArrayEquals(FOO_BYTES, new ZipClassLoaderDelegate(zip, "ext").findClass("org.arend.Foo"));
    assertArrayEquals(FOO_BYTES, new ZipClassLoaderDelegate(zip, "ext/").findClass("org.arend.Foo"));
  }

  @Test
  public void missingClassReturnsNull() throws Exception {
    File zip = writeZip(entries("ext/org/arend/Foo.class", FOO_BYTES));
    assertNull(new ZipClassLoaderDelegate(zip, "ext").findClass("org.arend.Bar"));
  }

  /** The entry name must be the exact inverse of the class name, '$' included. */
  @Test
  public void nestedClassNamesRoundTrip() throws Exception {
    File zip = writeZip(entries("ext/a/b/Foo$Bar.class", FOO_BYTES, "ext/Root.class", BAR_BYTES));
    ZipClassLoaderDelegate delegate = new ZipClassLoaderDelegate(zip, "ext");
    assertArrayEquals(FOO_BYTES, delegate.findClass("a.b.Foo$Bar"));
    assertArrayEquals(BAR_BYTES, delegate.findClass("Root"));
  }

  @Test
  public void classOutsidePrefixIsNotFound() throws Exception {
    File zip = writeZip(entries(
      "ext/Foo.class", FOO_BYTES,
      "other/Bar.class", BAR_BYTES,
      "extras/Baz.class", BAR_BYTES));
    ZipClassLoaderDelegate delegate = new ZipClassLoaderDelegate(zip, "ext");
    assertArrayEquals(FOO_BYTES, delegate.findClass("Foo"));
    assertNull(delegate.findClass("Bar"));
    // `extras/` must not be mistaken for the `ext` prefix
    assertNull(delegate.findClass("Baz"));
  }

  @Test
  public void emptyPrefixResolvesArchiveRoot() throws Exception {
    File zip = writeZip(entries("Foo.class", FOO_BYTES, "org/arend/Bar.class", BAR_BYTES));
    ZipClassLoaderDelegate delegate = new ZipClassLoaderDelegate(zip, "");
    assertArrayEquals(FOO_BYTES, delegate.findClass("Foo"));
    assertArrayEquals(BAR_BYTES, delegate.findClass("org.arend.Bar"));
  }

  /**
   * The `.class` filter is what bounds the snapshot when the prefix is empty -- otherwise an empty
   * `extensions` would pull the library's sources into memory as well.
   */
  @Test
  public void nonClassEntriesAreIgnored() throws Exception {
    File zip = writeZip(entries(
      "Foo.class", FOO_BYTES,
      "src/Logic.ard", new byte[]{9, 9, 9},
      "arend.yaml", new byte[]{8}));
    ZipClassLoaderDelegate delegate = new ZipClassLoaderDelegate(zip, "");
    assertArrayEquals(FOO_BYTES, delegate.findClass("Foo"));
    assertNull(delegate.findClass("src.Logic"));
    assertNull(delegate.findClass("arend"));
  }

  /** Construction must stay free of I/O: a delegate is built on every library update. */
  @Test
  public void constructionDoesNotTouchTheFile() {
    new ZipClassLoaderDelegate(new File(myFolder.getRoot(), "absent.zip"), "ext");
  }

  /**
   * The archive is read in one pass rather than reopened per class. Overwriting it after the first
   * lookup proves later bytes come from the snapshot and not from a second open.
   */
  @Test
  public void archiveIsReadOnlyOnce() throws Exception {
    File zip = writeZip(entries("ext/Foo.class", FOO_BYTES, "ext/Bar.class", BAR_BYTES));
    ZipClassLoaderDelegate delegate = new ZipClassLoaderDelegate(zip, "ext");
    assertArrayEquals(FOO_BYTES, delegate.findClass("Foo"));

    Files.write(zip.toPath(), new byte[]{1, 2, 3});
    assertArrayEquals(BAR_BYTES, delegate.findClass("Bar"));
    assertNull(delegate.findClass("Absent"));
  }

  /** A read failure is remembered, so an unreadable archive is not reopened once per class. */
  @Test
  public void unreadableArchiveThrowsAndIsMemoized() throws Exception {
    File zip = myFolder.newFile("garbage.zip");
    Files.write(zip.toPath(), new byte[]{1, 2, 3, 4});
    ZipClassLoaderDelegate delegate = new ZipClassLoaderDelegate(zip, "ext");

    ClassNotFoundException first = assertThrows(ClassNotFoundException.class, () -> delegate.findClass("Foo"));
    ClassNotFoundException second = assertThrows(ClassNotFoundException.class, () -> delegate.findClass("Bar"));

    assertTrue(first.getMessage().contains("garbage.zip"));
    assertSame(first.getCause(), second.getCause());
  }

  @Test
  public void toStringNamesThePathAndPrefix() throws Exception {
    File zip = writeZip(entries("ext/Foo.class", FOO_BYTES));
    assertEquals(zip.getPath() + "!/ext/", new ZipClassLoaderDelegate(zip, "ext").toString());
    assertEquals(zip.getPath(), new ZipClassLoaderDelegate(zip, "").toString());
  }
}
