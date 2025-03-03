package org.arend.server;

import org.arend.naming.reference.LocatedReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RawAnchor(@NotNull LocatedReferable parent, @Nullable Object data) {}
