package org.arend.library.classLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipClassLoaderDelegate implements ClassLoaderDelegate {
  private static final String CLASS_SUFFIX = ".class";

  private final File myFile;
  private final String myPrefix;

  private Map<String, byte[]> myClasses;
  private IOException myError;

  public ZipClassLoaderDelegate(File file, String prefix) {
    myFile = file;
    myPrefix = prefix.isEmpty() || prefix.endsWith("/") ? prefix : prefix + "/";
  }

  private Map<String, byte[]> readClasses() throws IOException {
    Map<String, byte[]> result = new HashMap<>();
    try (ZipFile zipFile = new ZipFile(myFile)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String entryName = entry.getName();
        if (entry.isDirectory() || !entryName.startsWith(myPrefix) || !entryName.endsWith(CLASS_SUFFIX)) {
          continue;
        }
        // The exact inverse of the entry name this class used to look up: strip the prefix and the
        // suffix, then undo the package separator. '$' is untouched, so nested classes round-trip.
        String className = entryName.substring(myPrefix.length(), entryName.length() - CLASS_SUFFIX.length()).replace('/', '.');
        try (InputStream stream = zipFile.getInputStream(entry)) {
          result.put(className, stream.readAllBytes());
        }
      }
    }
    return result;
  }

  @Override
  public synchronized byte[] findClass(String name) throws ClassNotFoundException {
    if (myClasses == null) {
      try {
        myClasses = readClasses();
      } catch (IOException e) {
        myClasses = Collections.emptyMap();
        myError = e;
      }
    }
    if (myError != null) {
      throw new ClassNotFoundException("Cannot read '" + this + "'", myError);
    }
    return myClasses.get(name);
  }

  @Override
  public String toString() {
    return myPrefix.isEmpty() ? myFile.getPath() : myFile.getPath() + "!/" + myPrefix;
  }
}
