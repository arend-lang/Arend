package org.arend.library.classLoader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class MultiClassLoaderTest {
  @Rule
  public final TemporaryFolder myFolder = new TemporaryFolder();

  /** Fails the way an unreadable archive does. */
  private static class ThrowingDelegate implements ClassLoaderDelegate {
    private final ClassNotFoundException myError = new ClassNotFoundException("cannot read 'broken.zip'");
    private int myCalls = 0;

    @Override
    public byte[] findClass(String name) throws ClassNotFoundException {
      myCalls++;
      throw myError;
    }

    @Override
    public String toString() {
      return "broken.zip";
    }
  }

  private static class StubDelegate implements ClassLoaderDelegate {
    private final String myName;
    private final byte[] myBytes;
    private int myCalls = 0;

    StubDelegate(String name, byte[] bytes) {
      myName = name;
      myBytes = bytes;
    }

    @Override
    public byte[] findClass(String name) {
      myCalls++;
      return name.equals(myName) ? myBytes : null;
    }

    @Override
    public String toString() {
      return "stub(" + myName + ")";
    }
  }

  private MultiClassLoader<String> loader() {
    return new MultiClassLoader<>(MultiClassLoaderTest.class.getClassLoader());
  }

  private static byte[] ownClassBytes() throws Exception {
    try (InputStream in = MultiClassLoaderTest.class.getResourceAsStream("MultiClassLoaderTest.class")) {
      assertNotNull("own class file must be on the test classpath", in);
      return in.readAllBytes();
    }
  }

  @Test
  public void aThrowingDelegateDoesNotHideLaterOnes() {
    ThrowingDelegate broken = new ThrowingDelegate();
    StubDelegate later = new StubDelegate("some.Other", new byte[]{1});
    MultiClassLoader<String> loader = loader();
    loader.addDelegate("broken", broken);
    loader.addDelegate("later", later);

    ClassNotFoundException e = assertThrows(ClassNotFoundException.class, () -> loader.findClass("no.such.Class"));

    assertEquals("the delegate after the throwing one must still be consulted", 1, later.myCalls);
    assertEquals(1, broken.myCalls);
    assertArrayEquals(new Throwable[]{broken.myError}, e.getSuppressed());
    assertTrue(e.getMessage().contains("broken.zip"));
    assertTrue(e.getMessage().contains("stub(some.Other)"));
  }

  @Test
  public void classIsDefinedFromALaterDelegateAfterAnEarlierThrows() throws Exception {
    String name = MultiClassLoaderTest.class.getName();
    ThrowingDelegate broken = new ThrowingDelegate();
    MultiClassLoader<String> loader = loader();
    loader.addDelegate("broken", broken);
    loader.addDelegate("good", new StubDelegate(name, ownClassBytes()));

    Class<?> defined = loader.findClass(name);

    assertEquals(name, defined.getName());
    assertSame("must be defined by this loader, not inherited from the parent", loader, defined.getClassLoader());
    assertNotSame(MultiClassLoaderTest.class, defined);
    assertEquals(1, broken.myCalls);
  }

  /** A malformed class file is a real error, not a reason to fall through to the next location. */
  @Test
  public void malformedClassFileIsNotSwallowed() {
    MultiClassLoader<String> loader = loader();
    loader.addDelegate("garbage", new StubDelegate("some.Class", new byte[]{1, 2, 3, 4}));
    StubDelegate later = new StubDelegate("some.Class", new byte[]{9});
    loader.addDelegate("later", later);

    assertThrows(LinkageError.class, () -> loader.findClass("some.Class"));
    assertEquals("the loop must not continue past a malformed class file", 0, later.myCalls);
  }

  @Test
  public void nothingFoundListsEveryLocation() {
    MultiClassLoader<String> loader = loader();
    loader.addDelegate("a", new StubDelegate("x.A", new byte[]{1}));
    loader.addDelegate("b", new StubDelegate("x.B", new byte[]{2}));

    ClassNotFoundException e = assertThrows(ClassNotFoundException.class, () -> loader.findClass("x.C"));

    assertEquals(0, e.getSuppressed().length);
    assertTrue(e.getMessage().contains("stub(x.A)"));
    assertTrue(e.getMessage().contains("stub(x.B)"));
  }

  @Test
  public void removedDelegateIsNoLongerConsulted() {
    StubDelegate stub = new StubDelegate("x.A", new byte[]{1});
    MultiClassLoader<String> loader = loader();
    loader.addDelegate("a", stub);
    loader.removeDelegate("a");

    assertThrows(ClassNotFoundException.class, () -> loader.findClass("x.A"));
    assertEquals(0, stub.myCalls);
  }

  /**
   * A delegate is arbitrary code behind a public interface, so the loop must survive an unchecked
   * failure too -- not only the declared ClassNotFoundException.
   */
  @Test
  public void uncheckedExceptionFromDelegateIsAlsoTolerated() throws Exception {
    String name = MultiClassLoaderTest.class.getName();
    StubDelegate later = new StubDelegate(name, ownClassBytes());
    MultiClassLoader<String> loader = loader();
    loader.addDelegate("rude", new ClassLoaderDelegate() {
      @Override
      public byte[] findClass(String requested) {
        throw new IllegalStateException("no path for " + requested);
      }
    });
    loader.addDelegate("later", later);

    assertEquals(name, loader.findClass(name).getName());
    assertEquals(1, later.myCalls);
  }

  /** The one test covering the real composition: a zip-backed delegate through the loader. */
  @Test
  public void zipBackedDelegateLoadsThroughTheLoader() throws Exception {
    String name = MultiClassLoaderTest.class.getName();
    File zip = myFolder.newFile("lib.zip");
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("ext/" + name.replace('.', '/') + ".class"));
      out.write(ownClassBytes());
      out.closeEntry();
    }

    MultiClassLoader<String> loader = loader();
    loader.addDelegate("lib", new ZipClassLoaderDelegate(zip, "ext"));

    Class<?> defined = loader.findClass(name);
    assertEquals(name, defined.getName());
    assertSame(loader, defined.getClassLoader());
  }
}
