package org.arend.term.group;

import org.jetbrains.annotations.Nullable;

public record ConcreteStatement(@Nullable ConcreteGroup group, @Nullable ConcreteNamespaceCommand command) {}
