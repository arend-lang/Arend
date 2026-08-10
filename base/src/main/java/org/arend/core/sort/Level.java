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

import java.math.BigInteger;
import java.util.*;

public class Level implements CoreLevel {
  private final Map<LevelVariable,BigInteger> myVars;
  private final BigInteger myConstant;

  public static final Level INFINITY = new Level();

  public Level(Map<LevelVariable,BigInteger> vars, BigInteger maxConstant) {
    myVars = vars;
    myConstant = maxConstant;
  }

  private Level() {
    myVars = null;
    myConstant = BigInteger.ZERO;
  }

  // max(var + constant, maxConstant)
  public Level(LevelVariable var, BigInteger constant, BigInteger maxConstant) {
    if (var == null) {
      myVars = Collections.emptyMap();
      myConstant = constant.max(maxConstant);
    } else {
      myVars = Collections.singletonMap(var, constant);
      myConstant = maxConstant.compareTo(constant) > 0 ? maxConstant : BigInteger.ZERO;
    }
  }

  public Level(LevelVariable var, int constant) {
    this(var, BigInteger.valueOf(constant), BigInteger.ZERO);
  }

  public Level(LevelVariable var) {
    this(var, BigInteger.ZERO, BigInteger.ZERO);
  }

  public Level(BigInteger constant) {
    this(Collections.emptyMap(), constant);
  }

  public boolean hasVars() {
    return myVars != null && !myVars.isEmpty();
  }

  public Set<? extends LevelVariable> getVars() {
    return myVars == null ? Collections.emptySet() : myVars.keySet();
  }

  @Override
  public @NotNull Set<? extends Map.Entry<LevelVariable,BigInteger>> getVarPairs() {
    return myVars == null ? Collections.emptySet() : myVars.entrySet();
  }

  @Override
  public @NotNull BigInteger getConstant() {
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

  public boolean isZero() {
    return myVars != null && myVars.isEmpty() && myConstant.equals(BigInteger.ZERO);
  }

  public boolean withMaxConstant() {
    return myVars != null && !myVars.isEmpty() && myConstant.compareTo(BigInteger.ZERO) > 0;
  }

  public boolean hasInferenceVariables() {
    if (myVars == null) return false;
    for (LevelVariable variable : myVars.keySet()) {
      if (variable instanceof InferenceLevelVariable) return true;
    }
    return false;
  }

  public @Nullable LevelVariable getSingleVar() {
    if (myVars == null || myVars.size() != 1) return null;
    var entry = myVars.entrySet().iterator().next();
    return entry.getValue().equals(BigInteger.ZERO) && myConstant.equals(BigInteger.ZERO) ? entry.getKey() : null;
  }

  public Level add(BigInteger constant) {
    if (myVars == null || constant.equals(BigInteger.ZERO)) return this;
    Map<LevelVariable,BigInteger> vars = new HashMap<>(myVars.size());
    boolean keepConstant = false;
    for (Map.Entry<LevelVariable, BigInteger> entry : myVars.entrySet()) {
      if (myConstant.compareTo(entry.getValue()) <= 0) keepConstant = true;
      vars.put(entry.getKey(), entry.getValue().add(constant));
    }
    return new Level(vars, keepConstant ? myConstant : myConstant.add(constant));
  }

  public Level max(Level level) {
    if (isInfinity() || level.isInfinity()) {
      return INFINITY;
    }

    if (myVars.isEmpty() && level.myVars.isEmpty()) {
      return new Level(myConstant.max(level.myConstant));
    }

    if (myVars.isEmpty()) {
      return new Level(level.myVars, myConstant.max(level.myConstant));
    }

    if (level.myVars.isEmpty()) {
      return new Level(myVars, myConstant.max(level.myConstant));
    }

    Map<LevelVariable,BigInteger> vars = new HashMap<>(myVars);
    for (Map.Entry<LevelVariable, BigInteger> entry : level.myVars.entrySet()) {
      vars.compute(entry.getKey(), (k,v) -> v == null ? entry.getValue() : v.max(entry.getValue()));
    }
    return new Level(vars, myConstant.max(level.myConstant));
  }

  public Level subst(LevelSubstitution subst) {
    if (myVars == null || myVars.isEmpty()) {
      return this;
    }

    List<Level> substLevels = new ArrayList<>();
    Map<LevelVariable,BigInteger> rest = new HashMap<>();
    for (Map.Entry<LevelVariable, BigInteger> entry : myVars.entrySet()) {
      Level level = (Level) subst.get(entry.getKey());
      if (level == null) {
        rest.put(entry.getKey(), entry.getValue());
      } else {
        substLevels.add(level.add(entry.getValue()));
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
    Concrete.LevelExpression level = ToAbstractVisitor.convert(this);
    if (level == null) return "∞";
    level.accept(new PrettyPrintVisitor(builder, 0), new Precedence(Concrete.Expression.PREC));
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
      return level2.hasInferenceVariables() && (equations == null || equations.addEquation(INFINITY, level2, CMP.LE, sourceNode));
    }

    if (level1.myConstant.compareTo(level2.myConstant) > 0) {
      boolean ok = false;
      boolean add = false;
      for (Map.Entry<LevelVariable, BigInteger> entry : level2.myVars.entrySet()) {
        if (level1.myConstant.compareTo(entry.getValue()) <= 0) {
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

    for (Map.Entry<LevelVariable, BigInteger> entry1 : level1.myVars.entrySet()) {
      boolean ok = false;
      boolean add = entry1.getKey() instanceof InferenceLevelVariable && entry1.getValue().compareTo(level2.myConstant) <= 0;
      for (Map.Entry<LevelVariable, BigInteger> entry2 : level2.myVars.entrySet()) {
        if (entry1.getKey().compare(entry2.getKey(), CMP.LE) && entry1.getValue().compareTo(entry2.getValue()) <= 0) {
          ok = true;
          break;
        }
        if (!add && (entry2.getKey() instanceof InferenceLevelVariable || entry1.getKey() instanceof InferenceLevelVariable && entry1.getValue().compareTo(entry2.getValue()) <= 0)) {
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

    if (equations != null && level1.isClosed()) {
      for (LevelVariable var : level2.getVars()) {
        if (var instanceof InferenceLevelVariable infVar) {
          equations.addEquation(new Level(BigInteger.ZERO), new Level(infVar), CMP.LE, sourceNode);
        }
      }
    }

    return true;
  }
}
