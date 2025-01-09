package org.arend.term.abs;

import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.Nullable;

public interface AbstractReference {
  @Nullable ModuleLocation getReferenceModule();
}
