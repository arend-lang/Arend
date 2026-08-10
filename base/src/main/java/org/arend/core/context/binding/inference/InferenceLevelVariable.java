package org.arend.core.context.binding.inference;

import org.arend.core.context.binding.LevelVariable;
import org.arend.ext.core.ops.CMP;
import org.arend.term.concrete.Concrete;

public class InferenceLevelVariable implements LevelVariable {
  private final Concrete.SourceNode mySourceNode;
  private final boolean myGenerated;

  public InferenceLevelVariable(Concrete.SourceNode sourceNode, boolean isGenerated) {
    mySourceNode = sourceNode;
    myGenerated = isGenerated;
  }

  public boolean isGenerated() {
    return myGenerated;
  }

  @Override
  public LevelVariable min(LevelVariable other) {
    return this == other ? this : null;
  }

  @Override
  public boolean compare(LevelVariable other, CMP cmp) {
    return this == other;
  }

  public Concrete.SourceNode getSourceNode() {
    return mySourceNode;
  }

  @Override
  public String toString() {
    return "?p";
  }
}
