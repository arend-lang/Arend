package org.arend.term.group;

import org.arend.naming.reference.TCDefReferable;
import org.arend.term.NamespaceCommand;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.Nullable;

public record ConcreteStatement(@Nullable ConcreteGroup group, @Nullable NamespaceCommand command, @Nullable Concrete.LevelsDefinition pLevelsDefinition, @Nullable Concrete.LevelsDefinition hLevelsDefinition) {
  public @Nullable TCDefReferable getReferable() {
    if (group == null) return null;
    Concrete.ResolvableDefinition definition = group.definition();
    return definition == null ? null : definition.getData();
  }
}
