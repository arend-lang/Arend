package org.arend.ext.concrete.definition;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.reference.ArendRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConcreteDefinition extends ConcreteSourceNode {
  @NotNull ArendRef getRef();
  @Nullable ConcreteLevelParameters getPLevelParameters();
  @Nullable ConcreteLevelParameters getHLevelParameters();
  void setPLevelParameters(@Nullable ConcreteLevelParameters parameters);
  void setHLevelParameters(@Nullable ConcreteLevelParameters parameters);
}
