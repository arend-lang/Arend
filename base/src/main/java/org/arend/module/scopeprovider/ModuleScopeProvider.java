package org.arend.module.scopeprovider;

import org.arend.ext.module.ModulePath;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.ModuleReferable;
import org.arend.naming.scope.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ModuleScopeProvider {
  @Nullable
  Scope forModule(@NotNull ModulePath module);

  default @NotNull GlobalReferable findModule(@NotNull ModulePath module) {
    return new ModuleReferable(module);
  }
}
