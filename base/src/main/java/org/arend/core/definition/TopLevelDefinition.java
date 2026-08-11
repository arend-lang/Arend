package org.arend.core.definition;

import org.arend.core.context.binding.LevelVariable;
import org.arend.ext.util.Pair;
import org.arend.naming.reference.TCDefReferable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class TopLevelDefinition extends CallableDefinition {
  private List<? extends LevelVariable> myLevelParameters = Collections.emptyList();
  private List<Pair<TCDefReferable,Integer>> myParametersOriginalDefinitions = Collections.emptyList();
  private Set<? extends FunctionDefinition> myAxioms = Collections.emptySet();
  private Set<? extends Definition> myGoals = Collections.emptySet();

  public TopLevelDefinition(TCDefReferable referable, TypeCheckingStatus status) {
    super(referable, status);
  }

  @Override
  public TopLevelDefinition getTopLevelDefinition() {
    return this;
  }

  @Override
  public @NotNull List<? extends LevelVariable> getLevelParameters() {
    return myLevelParameters;
  }

  public void setLevelParameters(@NotNull List<LevelVariable> parameters) {
    myLevelParameters = parameters;
  }

  @Override
  public List<? extends Pair<TCDefReferable,Integer>> getParametersOriginalDefinitions() {
    return myParametersOriginalDefinitions;
  }

  public void setParametersOriginalDefinitions(List<Pair<TCDefReferable,Integer>> definitions) {
    myParametersOriginalDefinitions = definitions;
  }

  @Override
  public Set<? extends FunctionDefinition> getAxioms() {
    return myAxioms;
  }

  public boolean isAxiom() {
    return false;
  }

  public void setAxioms(Set<? extends FunctionDefinition> axioms) {
    myAxioms = axioms;
  }

  @Override
  public Set<? extends Definition> getGoals() {
    return myGoals;
  }

  public void setGoals(Set<? extends Definition> goals) {
    myGoals = goals;
  }
}
