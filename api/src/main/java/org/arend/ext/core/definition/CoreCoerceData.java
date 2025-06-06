package org.arend.ext.core.definition;

import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CoreCoerceData {
  @NotNull List<? extends CoreDefinition> getCoerceToAny();
  @NotNull List<? extends CoreDefinition> getCoerceToPi();
  @NotNull List<? extends CoreDefinition> getCoerceToSigma();
  @NotNull List<? extends CoreDefinition> getCoerceToUniverse();
  @NotNull List<? extends CoreDefinition> getCoerceToDefinition(@NotNull CoreDefinition definition);
  @NotNull List<Pair<CoreDefinition, List<? extends CoreDefinition>>> getDefinitionCoercesTo();

  @NotNull List<? extends CoreDefinition> getCoerceFromAny();
  @NotNull List<? extends CoreDefinition> getCoerceFromPi();
  @NotNull List<? extends CoreDefinition> getCoerceFromSigma();
  @NotNull List<? extends CoreDefinition> getCoerceFromUniverse();
  @NotNull List<? extends CoreDefinition> getCoerceFromDefinition(@NotNull CoreDefinition definition);
  @NotNull List<Pair<CoreDefinition, List<? extends CoreDefinition>>> getDefinitionCoercesFrom();
}
