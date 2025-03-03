package org.arend.server.modifier;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record RawSequenceModifier(@NotNull List<RawModifier> sequence) implements RawModifier {
}
