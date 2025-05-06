package org.arend.source;

import org.arend.ext.typechecking.DefinitionListener;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a module persisted in a binary format.
 */
public interface BinarySource {
  @NotNull ModuleLocation getModule();

  long getTimeStamp();

  void setKeyRegistry(SerializableKeyRegistryImpl keyRegistry);

  void setDefinitionListener(DefinitionListener definitionListener);
}
