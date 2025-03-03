package org.arend.server.modifier;

import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.NotNull;

public record RawImportAdder(@NotNull ConcreteNamespaceCommand command) implements RawModifier {}
