package org.arend.naming.resolving.typing;

import org.arend.naming.reference.GlobalReferable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DynamicScopeProviderImpl implements DynamicScopeProvider {
  private final GlobalReferable myReferable;
  private final List<? extends GlobalReferable> mySuperReferables;
  private final List<? extends GlobalReferable> myDynamicContent;

  public DynamicScopeProviderImpl(GlobalReferable referable, List<? extends GlobalReferable> superReferables, List<? extends GlobalReferable> dynamicContent) {
    myReferable = referable;
    mySuperReferables = superReferables;
    myDynamicContent = dynamicContent;
  }

  @Override
  public @NotNull GlobalReferable getReferable() {
    return myReferable;
  }

  @Override
  public @NotNull List<? extends GlobalReferable> getSuperReferables() {
    return mySuperReferables;
  }

  @Override
  public @NotNull List<? extends GlobalReferable> getDynamicContent() {
    return myDynamicContent;
  }
}
