package org.arend.term.group;

import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.Nullable;

public record ConcreteStatement(@Nullable ConcreteGroup group, @Nullable ConcreteNamespaceCommand command, @Nullable Concrete.LevelsDefinition pLevelsDefinition, @Nullable Concrete.LevelsDefinition hLevelsDefinition) {}
