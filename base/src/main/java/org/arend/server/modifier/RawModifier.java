package org.arend.server.modifier;

public sealed interface RawModifier permits RawImportAdder, RawImportRemover, RawSequenceModifier {}
