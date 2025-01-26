package org.arend.naming.resolving.typing;

import org.arend.naming.reference.GlobalReferable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class EmptyDynamicScopeProvider implements DynamicScopeProvider {
  private final GlobalReferable myReferable;

  public EmptyDynamicScopeProvider(GlobalReferable myReferable) {
    this.myReferable = myReferable;
  }

  @Override
  public @NotNull GlobalReferable getReferable() {
    return myReferable;
  }

  @Override
  public @NotNull List<? extends GlobalReferable> getSuperReferables() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<? extends GlobalReferable> getDynamicContent() {
    return Collections.emptyList();
  }
}
