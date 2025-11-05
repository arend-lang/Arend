package org.arend.server;

import org.arend.naming.reference.Referable;
import org.arend.term.abs.AbstractReference;
import org.jetbrains.annotations.NotNull;

public interface ArendServerResolveListener {
  default void addReference(@NotNull AbstractReference reference, @NotNull Referable referable) {}

  ArendServerResolveListener EMPTY = new ArendServerResolveListener() {};
}
