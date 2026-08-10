package org.arend.source;

import org.arend.ext.module.ModuleLocation;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class FileBinarySource extends StreamBinarySource {
  private final Path myFile;
  private final ModuleLocation myModule;

  /**
   * Creates a new {@code FileBinarySource} from a path to the base directory and a path to the source.
   *
   * @param basePath    a path to the base directory.
   * @param module      a path to the source.
   */
  public FileBinarySource(Path basePath, ModuleLocation module) {
    myFile = FileUtils.binaryFile(basePath, module.getModulePath());
    myModule = module;
  }

  @NotNull
  @Override
  public ModuleLocation getModule() {
    return myModule;
  }

  @Nullable
  @Override
  protected InputStream getInputStream() throws IOException {
    return Files.newInputStream(myFile);
  }

  /**
   * Scratch file the new cache is built in before it replaces {@link #myFile}. Deliberately a
   * fixed name rather than a unique one: a run killed mid-write leaves it behind, and a fixed
   * name is reused by the next attempt instead of littering the binaries directory.
   */
  private Path tempFile() {
    return myFile.resolveSibling(myFile.getFileName() + ".tmp");
  }

  @Nullable
  @Override
  protected OutputStream getOutputStream() throws IOException {
    Files.createDirectories(myFile.getParent());
    return Files.newOutputStream(tempFile(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
  }

  @Override
  protected void commitOutput() throws IOException {
    try {
      Files.move(tempFile(), myFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      // Some filesystems (and some network mounts) cannot do this; a plain replace still beats
      // writing the destination in place, since the window in which it is invalid is far shorter.
      Files.move(tempFile(), myFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @Override
  protected void discardOutput() {
    try {
      Files.deleteIfExists(tempFile());
    } catch (IOException ignored) {
    }
  }

  @Override
  public long getTimeStamp() {
    try {
      return Files.getLastModifiedTime(myFile).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }

  @Override
  public boolean delete() {
    try {
      Files.deleteIfExists(myFile);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
