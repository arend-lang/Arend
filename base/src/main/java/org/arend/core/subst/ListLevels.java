package org.arend.core.subst;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.definition.Definition;
import org.arend.core.sort.Level;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.CMP;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListLevels implements Levels {
  private final List<Level> myLevels;

  public ListLevels(List<Level> levels) {
    myLevels = levels;
  }

  public ListLevels(Level level) {
    myLevels = Collections.singletonList(level);
  }

  @Override
  public LevelSubstitution makeSubstitution(@NotNull Definition definition) {
    List<? extends LevelVariable> vars = definition.getLevelParameters();
    if (vars.isEmpty()) {
      return LevelSubstitution.EMPTY;
    }
    if (vars.size() > myLevels.size()) {
      throw new IllegalStateException();
    }
    SimpleLevelSubstitution result = new SimpleLevelSubstitution();
    for (int i = 0; i < vars.size(); i++) {
      result.add(vars.get(i), myLevels.get(i));
    }
    return result;
  }

  @Override
  public Levels subst(LevelSubstitution substitution) {
    if (substitution.isEmpty()) {
      return this;
    }

    // Level.subst returns the same object if nothing changes, so we make a copy only if some level is changed
    List<Level> result = null;
    for (int i = 0; i < myLevels.size(); i++) {
      Level level = myLevels.get(i);
      Level newLevel = level.subst(substitution);
      if (result == null && newLevel != level) {
        result = new ArrayList<>(myLevels.size());
        result.addAll(myLevels.subList(0, i));
      }
      if (result != null) {
        result.add(newLevel);
      }
    }
    return result == null ? this : new ListLevels(result);
  }

  @Override
  public boolean compare(Levels other, CMP cmp, Equations equations, Concrete.SourceNode sourceNode) {
    if (myLevels.isEmpty() && other.size() == 0) return true;
    if (!(other instanceof ListLevels) || myLevels.size() != other.size()) return false;
    List<? extends Level> otherList = other.toList();
    for (int i = 0; i < myLevels.size(); i++) {
      if (!Level.compare(myLevels.get(i), otherList.get(i), cmp, equations, sourceNode)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isClosed() {
    for (Level level : myLevels) {
      if (!level.isClosed()) return false;
    }
    return true;
  }

  @Override
  public List<? extends Level> toList() {
    return myLevels;
  }

  @Override
  public int size() {
    return myLevels.size();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Levels && compare((Levels) obj, CMP.EQ, DummyEquations.getInstance(), null);
  }
}
