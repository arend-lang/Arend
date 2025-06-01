package org.arend.typechecking.instance.provider;

import org.arend.naming.reference.TCDefReferable;
import org.arend.typechecking.instance.ArendInstances;
import org.jetbrains.annotations.NotNull;

public interface InstanceScopeProvider {
  @NotNull ArendInstances getInstancesFor(@NotNull TCDefReferable referable);

  InstanceScopeProvider EMPTY = referable -> new ArendInstances();
}
