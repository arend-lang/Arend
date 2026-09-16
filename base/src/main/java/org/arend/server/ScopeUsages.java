package org.arend.server;

import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface ScopeUsages {
  @NotNull ScopeUsage getUsage(@NotNull ModuleLocation module, @NotNull LongName longName);

  @NotNull Set<LongName> getGroupNames(@NotNull ModuleLocation module);

  boolean isCollected(@NotNull ModuleLocation module);
}
