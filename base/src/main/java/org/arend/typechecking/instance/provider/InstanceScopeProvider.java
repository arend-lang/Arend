package org.arend.typechecking.instance.provider;

import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
import org.jetbrains.annotations.NotNull;

public interface InstanceScopeProvider {
  @NotNull Scope getInstanceScopeFor(@NotNull TCDefReferable referable);

  InstanceScopeProvider EMPTY = new InstanceScopeProvider() {
    @Override
    public @NotNull Scope getInstanceScopeFor(@NotNull TCDefReferable referable) {
      return EmptyScope.INSTANCE;
    }
  };
}
