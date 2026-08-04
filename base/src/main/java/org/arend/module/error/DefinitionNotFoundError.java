package org.arend.module.error;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.reference.ArendRef;
import org.arend.naming.reference.FullModuleReferable;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public class DefinitionNotFoundError extends GeneralError {
  public final FullName definition;

  public DefinitionNotFoundError(@NotNull FullName definition) {
    super(Level.ERROR, "Definition " + definition.longName + " is not found" + (definition.module == null ? "" : " in " + definition.module));
    this.definition = definition;
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<ArendRef, GeneralError> consumer) {
    if (definition.module != null) {
      consumer.accept(new FullModuleReferable(definition.module), this);
    }
  }
}
