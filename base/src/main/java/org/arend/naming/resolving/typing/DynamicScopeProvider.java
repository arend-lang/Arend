package org.arend.naming.resolving.typing;

import org.arend.naming.reference.GlobalReferable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DynamicScopeProvider {
  @NotNull GlobalReferable getReferable();
  @NotNull List<? extends GlobalReferable> getSuperReferables();
  @NotNull List<? extends GlobalReferable> getDynamicContent();
}
