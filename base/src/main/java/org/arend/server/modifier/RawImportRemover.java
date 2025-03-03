package org.arend.server.modifier;

import org.jetbrains.annotations.NotNull;

public record RawImportRemover(@NotNull Object namespaceCommand) implements RawModifier {}
