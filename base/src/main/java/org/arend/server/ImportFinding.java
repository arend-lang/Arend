package org.arend.server;

import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ImportFinding(@NotNull Kind kind, @NotNull ConcreteNamespaceCommand command, @Nullable ConcreteNamespaceCommand.NameRenaming renaming) {
  public enum Kind {
    UNUSED_IMPORT,
    UNUSED_OPEN,
    UNUSED_NAME,
    UNUSED_ALIAS
  }

  public static @NotNull ImportFinding ofCommand(@NotNull ConcreteNamespaceCommand command) {
    return new ImportFinding(command.isImport() ? Kind.UNUSED_IMPORT : Kind.UNUSED_OPEN, command, null);
  }

  public static @NotNull ImportFinding ofName(@NotNull ConcreteNamespaceCommand command, ConcreteNamespaceCommand.@NotNull NameRenaming renaming) {
    return new ImportFinding(renaming.newName() == null ? Kind.UNUSED_NAME : Kind.UNUSED_ALIAS, command, renaming);
  }

  public @NotNull String name() {
    if (renaming == null) return String.join(".", command.module().getPath());
    String newName = renaming.newName();
    return newName != null ? newName : renaming.reference().getRefName();
  }

  public @Nullable Object data() {
    return renaming == null ? command.data() : renaming.data();
  }

  @Override
  public @NotNull String toString() {
    return kind + " " + name() + (renaming == null ? "" : " in " + command);
  }
}
