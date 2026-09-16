package org.arend.server;

import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public record ScopeUsage(@NotNull Set<UsedName> names, @NotNull Set<String> unknownNames, @NotNull Set<UsedName> guessedNames, @Nullable Set<TCDefReferable> instances, @Nullable Set<TCDefReferable> inferenceFields) {

  public record UsedName(@Nullable ConcreteNamespaceCommand command, @NotNull String name, @NotNull Referable referable) {}

  public static final ScopeUsage EMPTY = new ScopeUsage(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), null, null);

  public boolean areInstancesKnown() {
    return instances != null;
  }
}
