package org.arend.source;

import com.google.protobuf.CodedInputStream;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.typechecking.DefinitionListener;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.ext.module.ModuleLocation;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
   * @return an input stream from which the source will be loaded or null if the source does not support persisting.
   */
  @Nullable
  protected abstract OutputStream getOutputStream() throws IOException;

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
      errorReporter.report(new ExceptionError(e, "loading", module.getModulePath()));
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
      errorReporter.report(new ExceptionError(e, "loading", getModule().getModulePath()));
      return null;
    }
  }


  @Override
  public boolean persist(ArendServer server, ErrorReporter errorReporter) {
    ModuleLocation currentModule = getModule();
    ConcreteGroup group = server.getRawGroup(currentModule);
    if (group == null) {
      errorReporter.report(LocationError.module(currentModule.getModulePath()));
      return false;
    }

    try (OutputStream outputStream = getOutputStream()) {
      if (outputStream == null) {
        errorReporter.report(new PersistingError(currentModule.getModulePath()));
        return false;
      }

      ModuleProtos.Module module = new ModuleSerialization(errorReporter, new DependencyCollector(null)).writeModule(group, currentModule.getModulePath());
      if (module == null) {
        return false;
      }

      module.writeTo(outputStream);
      return true;
    } catch (Exception e) {
      errorReporter.report(new ExceptionError(e, "persisting", currentModule.getModulePath()));
      return false;
    }
  }
}
