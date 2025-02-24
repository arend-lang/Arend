package org.arend.module.error;

import org.arend.ext.error.GeneralError;
import org.arend.module.FullName;
import org.jetbrains.annotations.NotNull;

public class DefinitionNotFoundError extends GeneralError {
  public final FullName definition;

  public DefinitionNotFoundError(@NotNull FullName definition) {
    super(Level.ERROR, "Definition " + definition.longName + " is not found" + (definition.module == null ? "" : " in " + definition.module));
    this.definition = definition;
  }
}
