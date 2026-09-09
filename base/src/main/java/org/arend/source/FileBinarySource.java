package org.arend.source;

import org.arend.ext.module.ModuleLocation;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class FileBinarySource extends StreamBinarySource {
  /**
   * Suffix of the scratch files {@link #getOutputStream()} builds a new cache in. Kept distinct
   * from {@code .arc} so that a leftover scratch is never mistaken for a cache.
   */
  private static final String SCRATCH_SUFFIX = ".tmp";

  /**
   * How old a scratch file has to be before {@link #sweepStaleScratch} will remove it. Serializing
   * one module takes milliseconds, so anything this old belongs to a run that is no longer with
   * us; the margin is what keeps the sweep from deleting a scratch file another process is
   * filling right now.
   */
  private static final long SCRATCH_STALE_MILLIS = 60 * 60 * 1000L;

  private final Path myFile;
  private final ModuleLocation myModule;

  /** The scratch file of the write currently in flight, or null when no write is in flight. */
  private Path myTempFile;

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
   * Opens a scratch file next to {@link #myFile}; {@link #commitOutput()} moves it over the
   * destination once the caller has written and closed it. Nothing here opens the destination,
   * so a run that dies mid-write can only lose the new cache, never the old one.
   *
   * <p>The scratch name is unique per write rather than a fixed {@code <module>.arc.tmp}. Two
   * processes can persist the same module at the same time -- a daemon's persist pass and a
   * plain {@code arend} run over the same library -- and a shared name lets one of them commit
   * a mixture of both writes over the destination while the other's {@code discardOutput()}
   * deletes the scratch the first still needs. Uniqueness is what makes the atomicity this
   * class documents actually hold. Litter is bounded instead by {@link #sweepStaleScratch}.
   */
  @Nullable
  @Override
  protected OutputStream getOutputStream() throws IOException {
    Path directory = myFile.getParent();
    Files.createDirectories(directory);
    sweepStaleScratch(directory);
    myTempFile = Files.createTempFile(directory, myFile.getFileName() + ".", SCRATCH_SUFFIX);
    return Files.newOutputStream(myTempFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
  }

  @Override
  protected void commitOutput() throws IOException {
    Path scratch = myTempFile;
    if (scratch == null) return;
    try {
      Files.move(scratch, myFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      // Some filesystems (and some network mounts) cannot do this; a plain replace still beats
      // writing the destination in place, since the window in which it is invalid is far shorter.
      Files.move(scratch, myFile, StandardCopyOption.REPLACE_EXISTING);
    }
    myTempFile = null;
  }

  @Override
  protected void discardOutput() {
    Path scratch = myTempFile;
    myTempFile = null;
    if (scratch == null) return;
    try {
      Files.deleteIfExists(scratch);
    } catch (IOException ignored) {
    }
  }

  /**
   * Removes scratch files of this module left behind by runs that were killed before they could
   * discard their own. Only this module's scratch files are considered, and only ones older than
   * {@link #SCRATCH_STALE_MILLIS}, so a concurrent writer's scratch is never touched.
   */
  private void sweepStaleScratch(Path directory) {
    long cutoff = System.currentTimeMillis() - SCRATCH_STALE_MILLIS;
    try (DirectoryStream<Path> scratches =
             Files.newDirectoryStream(directory, myFile.getFileName() + ".*" + SCRATCH_SUFFIX)) {
      for (Path scratch : scratches) {
        try {
          if (Files.getLastModifiedTime(scratch).toMillis() < cutoff) {
            Files.deleteIfExists(scratch);
          }
        } catch (IOException ignored) {
        }
      }
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
