package org.arend.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ImportedName(@NotNull String original, @Nullable String renamed) {
  public @NotNull String visibleName() {
    return renamed == null ? original : renamed;
  }

  @Override
  public @NotNull String toString() {
    return renamed == null ? original : original + " \\as " + renamed;
  }
}
