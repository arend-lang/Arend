package org.arend.typechecking.instance.provider;

import org.arend.naming.reference.TCDefReferable;
import org.arend.util.list.PersistentList;
import org.jetbrains.annotations.NotNull;

public interface InstanceScopeProvider {
  @NotNull PersistentList<TCDefReferable> getInstancesFor(@NotNull TCDefReferable referable);

  InstanceScopeProvider EMPTY = new InstanceScopeProvider() {
    @Override
    public @NotNull PersistentList<TCDefReferable> getInstancesFor(@NotNull TCDefReferable referable) {
      return PersistentList.empty();
    }
  };
}
