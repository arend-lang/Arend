package org.arend.server;

import org.arend.ext.module.ModulePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ImportStructure(@NotNull Map<ModulePath, Set<ImportedName>> fileImports, @NotNull Group openStructure, boolean instancesKnown) {
  public record Group(@NotNull String name, @NotNull List<Group> subgroups, @NotNull Map<ModulePath, Set<ImportedName>> usages) {
    public @Nullable Group getSubgroup(@NotNull String name) {
      for (Group subgroup : subgroups) {
        if (subgroup.name.equals(name)) return subgroup;
      }
      return null;
    }
  }
}
