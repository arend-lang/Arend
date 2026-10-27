package org.arend.server.imports;

import org.arend.naming.reference.Referable;
import org.arend.naming.scope.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A scope that knows, for every name it binds, which part of which namespace command bound it.
 * A nested scope merges the entries of its parent instead of rediscovering them.
 */
interface TracingScope extends Scope {
  /**
   * @param referable  the referable bound to the name, if the entry binds one.
   * @param scope      the namespace bound to the name, if the entry binds one.
   * @param part       the part of the namespace command that brought the name in, or {@code null}
   *                   if it comes from the group itself, its external parameters or Prelude.
   */
  record Binding(@Nullable Referable referable, @Nullable Scope scope, ImportUsageData.@Nullable Part part) {}

  @NotNull Map<String, Binding> getBindings(@NotNull ScopeContext context);
}
