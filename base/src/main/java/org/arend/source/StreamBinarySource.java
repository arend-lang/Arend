package org.arend.source;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.typechecking.DefinitionListener;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.error.CorruptBinaryCacheError;
import org.arend.module.error.ExceptionError;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.ext.serialization.DeserializationException;
import org.arend.module.serialization.ModuleDeserialization;
import org.arend.module.serialization.ModuleProtos;
import org.arend.module.serialization.ModuleSerialization;
import org.arend.server.ArendServer;
import org.arend.source.error.LocationError;
import org.arend.source.error.PersistingError;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.order.dependency.DependencyCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipException;

/**
 * Represents a source that loads a binary module from an {@link InputStream} and persists it to an {@link OutputStream}.
 */
public abstract class StreamBinarySource implements PersistableBinarySource {
  private SerializableKeyRegistryImpl myKeyRegistry;
  private DefinitionListener myDefinitionListener;

  @Override
  public void setKeyRegistry(SerializableKeyRegistryImpl keyRegistry) {
    myKeyRegistry = keyRegistry;
  }

  @Override
  public void setDefinitionListener(DefinitionListener definitionListener) {
    myDefinitionListener = definitionListener;
  }

  /**
   * Gets an input stream from which the source will be loaded.
   *
   * @return an input stream from which the source will be loaded or null if some error occurred.
   */
  @Nullable
  protected abstract InputStream getInputStream() throws IOException;

  /**
   * Gets an output stream to which the source will be persisted.
   *
   * <p>Implementations that back this stream by a scratch location publish it in
   * {@link #commitOutput()}; {@link #persist} calls that only after the stream has been closed
   * without error, and calls {@link #discardOutput()} in every other case.
   *
   * @return an input stream from which the source will be loaded or null if the source does not support persisting.
   */
  @Nullable
  protected abstract OutputStream getOutputStream() throws IOException;

  /**
   * Publishes whatever was written to the last stream handed out by {@link #getOutputStream()}.
   * Called once, after that stream has been closed cleanly. The default is a no-op, for sources
   * whose output stream already writes to its final destination.
   */
  protected void commitOutput() throws IOException {}

  /**
   * Throws away an uncommitted output, if the implementation keeps one. Called from a
   * {@code finally}, so it also runs after a successful {@link #commitOutput()} and must be a
   * no-op then.
   */
  protected void discardOutput() {}

  @Override
  public @Nullable ConcreteGroup load(@NotNull ArendServer server, @NotNull ErrorReporter errorReporter) {
    ModuleLocation module = getModule();
    try (InputStream inputStream = getInputStream()) {
      if (inputStream == null) return null;

      CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
      codedInputStream.setRecursionLimit(Integer.MAX_VALUE);
      ModuleProtos.Module moduleProto = ModuleProtos.Module.parseFrom(codedInputStream);

      ModuleDeserialization moduleDeserialization =
          new ModuleDeserialization(moduleProto, myKeyRegistry, myDefinitionListener);
      ConcreteGroup group = moduleDeserialization.readGroup(module);
      server.updateModule(getTimeStamp(), module, () -> group);

      ModuleScopeProvider scopeProvider =
          server.getModuleScopeProvider(module.getLibraryName(), false);
      moduleDeserialization.readModule(scopeProvider, new DependencyCollector(null));
      return group;
    } catch (IOException | DeserializationException e) {
      reportUnreadableCache(errorReporter, e);
      return null;
    }
  }

  /**
   * Phase 1 of two-phase binary loading: parses the protobuf only.
   * Returns the {@link ModuleDeserialization} to be used in phase 2, or null on failure.
   * Does NOT set any typechecked definitions on the group referables —
   * that happens in phase 2 so that failures leave no half-initialized shells.
   */
  public @Nullable ModuleDeserialization parseProtobuf(@NotNull ErrorReporter errorReporter) {
    try (InputStream inputStream = getInputStream()) {
      if (inputStream == null) return null;

      CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
      codedInputStream.setRecursionLimit(Integer.MAX_VALUE);
      ModuleProtos.Module moduleProto = ModuleProtos.Module.parseFrom(codedInputStream);

      return new ModuleDeserialization(moduleProto, myKeyRegistry, myDefinitionListener);
    } catch (IOException e) {
      reportUnreadableCache(errorReporter, e);
      return null;
    }
  }

  /**
   * A cache we cannot read is a cache we do not have: report a warning rather than an error — the
   * caller recompiles the module from source and the build is not affected.
   *
   * <p>Whether the file is also removed depends on {@link #provesCorruption}. Dropping a cache we
   * can never read stops it being rediscovered on every run; dropping one whose <em>read</em>
   * merely failed destroys minutes of work for a condition that would have healed on retry.
   */
  private void reportUnreadableCache(ErrorReporter errorReporter, Exception e) {
    boolean deleted = provesCorruption(e) && delete();
    errorReporter.report(new CorruptBinaryCacheError(getModule().getModulePath(), e, deleted));
  }

  /**
   * Whether {@code e} proves the bytes on disk are not a cache we could ever read, as opposed to
   * the read itself having failed.
   *
   * <p>Only the first kind justifies deleting the file. A truncated ZLIB stream, a gzip header
   * that is not one, a protobuf tag mismatch and a failed deserialization all say the content is
   * wrong. A bare {@link IOException} says nothing of the sort: {@code EIO} on a failing disk, an
   * NFS hiccup, an {@code AccessDeniedException}, or the destination being replaced by a
   * concurrent persist all arrive the same way, and deleting on those is how a valid cache gets
   * destroyed — on a library the size of arend-lib, minutes of work — by a transient fault.
   */
  private static boolean provesCorruption(Exception e) {
    return e instanceof DeserializationException
        || e instanceof InvalidProtocolBufferException
        || e instanceof ZipException
        || e instanceof EOFException;
  }

  @Override
  public boolean persist(ArendServer server, ErrorReporter errorReporter) {
    ModuleLocation currentModule = getModule();
    ConcreteGroup group = server.getRawGroup(currentModule);
    if (group == null) {
      errorReporter.report(LocationError.module(currentModule.getModulePath()));
      return false;
    }

    OutputStream outputStream;
    try {
      outputStream = getOutputStream();
    } catch (Exception e) {
      // The scratch file may already exist even though we never got a usable stream back.
      discardOutput();
      errorReporter.report(new ExceptionError(e, "persisting", currentModule.getModulePath()));
      return false;
    }
    if (outputStream == null) {
      discardOutput();
      errorReporter.report(new PersistingError(currentModule.getModulePath()));
      return false;
    }

    // The close() has to happen inside the try: it is what flushes the last bytes, so a failure
    // there means the output is incomplete and must not be committed. Nothing between here and
    // commitOutput() may touch the destination, which is why an interrupted run can only lose
    // the new cache, never the old one.
    try {
      ModuleProtos.Module module = new ModuleSerialization(errorReporter, new DependencyCollector(null)).writeModule(group, currentModule.getModulePath());
      if (module == null) {
        return false;
      }

      module.writeTo(outputStream);
      outputStream.close();
      commitOutput();
      return true;
    } catch (Exception e) {
      errorReporter.report(new ExceptionError(e, "persisting", currentModule.getModulePath()));
      return false;
    } finally {
      try {
        outputStream.close();
      } catch (IOException ignored) {
      }
      discardOutput();
    }
  }
}
