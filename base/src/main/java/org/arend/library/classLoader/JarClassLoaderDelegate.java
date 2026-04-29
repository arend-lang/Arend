package org.arend.library.classLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarClassLoaderDelegate implements ClassLoaderDelegate {
  private final List<Path> myJars = new ArrayList<>();

  public JarClassLoaderDelegate(Path root) {
    if (Files.isDirectory(root)) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.jar")) {
        for (Path entry : stream) {
          myJars.add(entry);
        }
      } catch (IOException ignored) {
      }
    } else if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".jar")) {
      myJars.add(root);
    }
  }

  @Override
  public byte[] findClass(String name) throws ClassNotFoundException {
    String entryName = name.replace('.', '/') + ".class";
    for (Path jar : myJars) {
      try (ZipFile zipFile = new ZipFile(jar.toFile())) {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry != null) {
          return zipFile.getInputStream(entry).readAllBytes();
        }
      } catch (IOException e) {
        throw new ClassNotFoundException("An exception happened during loading of class " + name + " from " + jar, e);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return myJars.toString();
  }
}
