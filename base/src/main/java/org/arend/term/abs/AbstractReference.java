package org.arend.term.abs;

import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AbstractReference {
  @NotNull String getReferenceText();
  @Nullable ModuleLocation getReferenceModule();
}
