package org.arend.core.sort;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.binding.inference.InferenceLevelVariable;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.level.CoreLevel;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.reference.Precedence;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Level implements CoreLevel {
  private final Map<LevelVariable,Integer> myVars;
  private final int myConstant;

  public static final Level INFINITY = new Level();

  private Level(Map<LevelVariable,Integer> vars, int maxConstant) {
    myVars = vars;
    myConstant = maxConstant;
  }

  private Level() {
    myVars = null;
    myConstant = 0;
  }

  // max(var + constant, maxConstant)
  public Level(LevelVariable var, int constant, int maxConstant) {
    assert constant >= (var == null ? -1 : var.getMinValue());
    if (var == null) {
      myVars = Collections.emptyMap();
      myConstant = Math.max(constant, maxConstant);
    } else {
      myVars = Collections.singletonMap(var, constant);
      myConstant = maxConstant > constant + var.getMinValue() ? maxConstant : var.getMinValue();
    }
  }

  public Level(LevelVariable var, int constant) {
    this(var, constant, -1);
  }

  public Level(LevelVariable var) {
    this(var, 0);
  }

  public Level(int constant) {
    this(Collections.emptyMap(), constant);
  }

  public LevelVariable.LvlType getType() {
    return myVars == null || myVars.isEmpty() ? null : myVars.keySet().iterator().next().getType();
  }

  public Set<? extends LevelVariable> getVars() {
    return myVars == null ? Collections.emptySet() : myVars.keySet();
  }

  @Override
  public @NotNull Set<? extends Map.Entry<LevelVariable,Integer>> getVarPairs() {
    return myVars == null ? Collections.emptySet() : myVars.entrySet();
  }

  public int getConstant() {
    return myConstant;
  }

  @Override
  public boolean isInfinity() {
    return myVars == null;
  }

  @Override
  public boolean isClosed() {
    return myVars == null || myVars.isEmpty();
  }

  public boolean isProp() {
    return isClosed() && myConstant == -1;
  }

  public boolean withMaxConstant() {
    return myVars != null && !myVars.isEmpty() && myConstant > myVars.keySet().iterator().next().getMinValue();
  }

  public @Nullable LevelVariable getSingleVar() {
    if (myVars == null || myVars.size() != 1) return null;
    var entry = myVars.entrySet().iterator().next();
    return entry.getValue() == 0 && myConstant == entry.getKey().getMinValue() ? entry.getKey() : null;
  }

  public Level add(int constant) {
    assert constant >= 0;
    if (constant == 0 || myVars == null) return this;
    Map<LevelVariable,Integer> vars = new HashMap<>(myVars.size());
    boolean keepConstant = false;
    for (Map.Entry<LevelVariable, Integer> entry : myVars.entrySet()) {
      if (myConstant <= entry.getValue() + entry.getKey().getMinValue()) keepConstant = true;
      vars.put(entry.getKey(), entry.getValue() + constant);
    }
    return new Level(vars, keepConstant ? myConstant : myConstant + constant);
  }

  // TODO[sorts]: Delete this after deleting h-level variables
  private Level add1(int constant) {
    assert constant >= -1;
    if (constant == 0 || myVars == null) return this;
    Map<LevelVariable,Integer> vars = new HashMap<>(myVars.size());
    for (Map.Entry<LevelVariable, Integer> entry : myVars.entrySet()) {
      int newConstant = entry.getValue() + constant;
      vars.put(entry.getKey(), newConstant < 0 ? -1 : newConstant);
    }
    int newConstant = myConstant + constant;
    return new Level(vars, newConstant < 0 ? -1 : newConstant);
  }

  public Level max(Level level) {
    if (isInfinity() || level.isInfinity()) {
      return INFINITY;
    }

    if (myVars.isEmpty() && level.myVars.isEmpty()) {
      return new Level(Math.max(myConstant, level.myConstant));
    }

    if (myVars.isEmpty()) {
      return new Level(level.myVars, Math.max(myConstant, level.myConstant));
    }

    if (level.myVars.isEmpty()) {
      return new Level(myVars, Math.max(myConstant, level.myConstant));
    }

    if (getType() != level.getType()) {
      return null;
    }

    Map<LevelVariable,Integer> vars = new HashMap<>(myVars);
    for (Map.Entry<LevelVariable, Integer> entry : level.myVars.entrySet()) {
      vars.compute(entry.getKey(), (k,v) -> v == null ? entry.getValue() : Math.max(v, entry.getValue()));
    }
    return new Level(vars, Math.max(myConstant, level.myConstant));
  }

  public Level subst(LevelSubstitution subst) {
    if (myVars == null || myVars.isEmpty()) {
      return this;
    }

    List<Level> substLevels = new ArrayList<>();
    Map<LevelVariable,Integer> rest = new HashMap<>();
    for (Map.Entry<LevelVariable, Integer> entry : myVars.entrySet()) {
      Level level = (Level) subst.get(entry.getKey());
      if (level == null) {
        rest.put(entry.getKey(), entry.getValue());
      } else {
        substLevels.add(level.add1(entry.getValue()));
      }
    }

    Level result = new Level(rest, myConstant);
    for (Level substLevel : substLevels) {
      result = result.max(substLevel);
    }
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    ToAbstractVisitor.convert(this).accept(new PrettyPrintVisitor(builder, 0), new Precedence(Concrete.Expression.PREC));
    return builder.toString();
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || obj instanceof Level && compare(this, (Level) obj, CMP.EQ, DummyEquations.getInstance(), null);
  }

  public static boolean compare(Level level1, Level level2, CMP cmp, Equations equations, Concrete.SourceNode sourceNode) {
    if (cmp == CMP.GE) {
      return compare(level2, level1, CMP.LE, equations, sourceNode);
    } else if (cmp == CMP.EQ) {
      return compare(level1, level2, CMP.LE, equations, sourceNode) && compare(level2, level1, CMP.LE, equations, sourceNode);
    }

    if (level2.isInfinity()) {
      return true;
    }
    if (level1.isInfinity()) {
      return !level2.isClosed() && (equations == null || equations.addEquation(INFINITY, level2, CMP.LE, sourceNode));
    }

    if (level1.myConstant > level2.myConstant) {
      boolean ok = false;
      boolean add = false;
      for (Map.Entry<LevelVariable, Integer> entry : level2.myVars.entrySet()) {
        if (level1.myConstant <= entry.getValue() + entry.getKey().getMinValue()) {
          ok = true;
          break;
        }
        if (entry.getKey() instanceof InferenceLevelVariable) {
          add = true;
        }
      }
      if (!ok) {
        if (add) {
          return equations == null || equations.addEquation(level1, level2, CMP.LE, sourceNode);
        } else {
          return false;
        }
      }
    }

    for (Map.Entry<LevelVariable, Integer> entry1 : level1.myVars.entrySet()) {
      boolean ok = false;
      boolean add = entry1.getKey() instanceof InferenceLevelVariable && entry1.getValue() + entry1.getKey().getMinValue() <= level2.myConstant;
      for (Map.Entry<LevelVariable, Integer> entry2 : level2.myVars.entrySet()) {
        if (entry1.getKey().compare(entry2.getKey(), CMP.LE) && entry1.getValue() <= entry2.getValue()) {
          ok = true;
          break;
        }
        if (!add && (entry2.getKey() instanceof InferenceLevelVariable || entry1.getKey() instanceof InferenceLevelVariable && (entry1.getValue() <= entry2.getValue()))) {
          add = true;
        }
      }
      if (!ok) {
        if (add) {
          return equations == null || equations.addEquation(level1, level2, CMP.LE, sourceNode);
        } else {
          return false;
        }
      }
    }

    return true;
  }
}
