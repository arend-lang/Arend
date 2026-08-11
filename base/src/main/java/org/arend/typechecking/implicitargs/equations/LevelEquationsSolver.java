package org.arend.typechecking.implicitargs.equations;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.binding.inference.InferenceLevelVariable;
import org.arend.core.sort.Level;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.core.subst.SimpleLevelSubstitution;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.TypecheckingError;
import org.arend.typechecking.error.local.ConstantSolveLevelEquationError;
import org.arend.typechecking.error.local.SolveLevelEquationsError;
import org.arend.typechecking.dfs.MapDFS;
import org.arend.ext.util.Pair;

import java.math.BigInteger;
import java.util.*;

public class LevelEquationsSolver {
  private final List<AbstractEquation<Level>> myDeferredMaxEquations;
  private final LevelEquations<InferenceLevelVariable> myEquations = new LevelEquations<>();
  private final Map<InferenceLevelVariable, Level> myConstantUpperBounds = new HashMap<>();
  private final Map<InferenceLevelVariable, Set<LevelVariable>> myLowerBounds = new HashMap<>();
  private final Map<LevelVariable, Set<InferenceLevelVariable>> myUpperBounds = new HashMap<>();
  private final ErrorReporter myErrorReporter;

  public LevelEquationsSolver(List<LevelEquation<LevelVariable>> levelEquations, List<? extends AbstractEquation<Level>> deferredMaxEquations, List<InferenceLevelVariable> variables, ErrorReporter errorReporter) {
    myDeferredMaxEquations = new ArrayList<>(deferredMaxEquations);
    for (InferenceLevelVariable var : variables) {
      myEquations.addVariable(var);
    }
    variables.clear();

    for (LevelEquation<LevelVariable> levelEquation : levelEquations) {
      addLevelEquation(levelEquation.getVariable1(), levelEquation.getVariable2(), levelEquation.getConstant(), levelEquation.getMaxConstant());
    }

    for (Map.Entry<InferenceLevelVariable, Level> entry : myConstantUpperBounds.entrySet()) {
      if (entry.getValue().isClosed()) {
        myEquations.getUpperBounded().add(entry.getKey());
      }
    }

    myErrorReporter = errorReporter;
  }

  private void addLevelEquation(final LevelVariable var1, LevelVariable var2, BigInteger constant, BigInteger maxConstant) {
    // 0 <= max(_ +-c, +-d) // 10
    if (var1 == null) {
      // 0 <= max(?y - c, -d) // 1
      if (var2 instanceof InferenceLevelVariable infVar && (maxConstant.compareTo(BigInteger.ZERO) < 0 && constant.compareTo(BigInteger.ZERO) < 0 || maxConstant.equals(BigInteger.ZERO) && constant.equals(BigInteger.ZERO))) {
        addEquation(new LevelEquation<>(null, infVar, constant));
      }
      return;
    }

    if (var2 instanceof InferenceLevelVariable && var1 != var2) {
      myLowerBounds.computeIfAbsent((InferenceLevelVariable) var2, k -> new HashSet<>()).add(var1);
      myUpperBounds.computeIfAbsent(var1, k -> new HashSet<>()).add((InferenceLevelVariable) var2);
    }

    // ?x <= max(_ +- c, +-d) // 10
    if (var1 instanceof InferenceLevelVariable) {
      // ?x <= max(?y +- c, +-d) // 4
      if (var2 instanceof InferenceLevelVariable) {
        addEquation(new LevelEquation<>((InferenceLevelVariable) var1, (InferenceLevelVariable) var2, constant, maxConstant.compareTo(BigInteger.ZERO) < 0 ? null : maxConstant));
      } else {
        // ?x <= max(+-c, +-d), ?x <= max(l +- c, +-d) // 6
        Level oldLevel = myConstantUpperBounds.get(var1);
        if (oldLevel == null) {
          myConstantUpperBounds.put((InferenceLevelVariable) var1, new Level(var2, constant, maxConstant));
        } else {
          Map.Entry<LevelVariable,BigInteger> oldEntry = oldLevel.getVarPairs().isEmpty() ? null : oldLevel.getVarPairs().iterator().next();
          if (var2 == null && oldEntry != null || var2 != null && oldEntry == null) {
            BigInteger otherConstant = var2 == null ? constant.max(maxConstant) : oldLevel.getConstant();
            BigInteger thisConst = var2 == null ? oldEntry.getValue() : constant;
            BigInteger thisMaxConst = var2 == null ? oldLevel.getConstant() : maxConstant;
            myConstantUpperBounds.put((InferenceLevelVariable) var1, new Level(thisMaxConst.min(otherConstant).max(thisConst.min(otherConstant))));
          } else {
            if (var2 == null) {
              BigInteger newConst = constant.max(maxConstant);
              if (newConst.compareTo(oldLevel.getConstant()) < 0) {
                myConstantUpperBounds.put((InferenceLevelVariable) var1, new Level(newConst));
              }
            } else {
              myConstantUpperBounds.put((InferenceLevelVariable) var1, constant.compareTo(BigInteger.ZERO) < 0 ? new Level(maxConstant.min(oldLevel.getConstant())) : new Level(var2.min(oldEntry.getKey()), constant.min(oldEntry.getValue()), maxConstant.min(oldLevel.getConstant())));
            }
          }
        }
      }
      return;
    }

    // l <= max(?y +- c, +-d) // 4
    if (var2 instanceof InferenceLevelVariable && constant.compareTo(BigInteger.ZERO) < 0) {
      addEquation(new LevelEquation<>(null, (InferenceLevelVariable) var2, constant));
    }
  }

  private void addEquation(LevelEquation<InferenceLevelVariable> equation) {
    InferenceLevelVariable var1 = equation.getVariable1();
    InferenceLevelVariable var2 = equation.getVariable2();

    if (var1 != null || var2 != null) {
      myEquations.addEquation(equation);
    } else {
      throw new IllegalStateException();
    }
  }

  public LevelSubstitution solveLevels() {
    Map<InferenceLevelVariable, BigInteger> solution = new HashMap<>();
    Set<InferenceLevelVariable> unBased = new HashSet<>();
    List<LevelEquation<InferenceLevelVariable>> cycle = myEquations.solve(solution);
    calculateUnBased(myEquations, unBased, solution);
    if (cycle != null) {
      reportCycle(cycle);
    }

    SimpleLevelSubstitution result = new SimpleLevelSubstitution();
    reportUnjustifiedConstants(unBased, solution);
    for (InferenceLevelVariable var : unBased) {
      result.add(var, new Level(solution.get(var).negate()));
    }

    for (Map.Entry<InferenceLevelVariable, BigInteger> entry : solution.entrySet()) {
      if (!unBased.contains(entry.getKey())) {
        BigInteger sol = solution.get(entry.getKey());
        MapDFS<LevelVariable> dfs = new MapDFS<>(myLowerBounds);
        dfs.visit(entry.getKey());
        Map<LevelVariable, BigInteger> vars = new HashMap<>();
        for (LevelVariable variable : dfs.getVisited()) {
          if (!(variable instanceof InferenceLevelVariable)) {
            vars.put(variable, entry.getValue().negate());
          }
        }
        result.add(entry.getKey(), new Level(vars, sol.negate()));
      }
    }

    for (Map.Entry<InferenceLevelVariable, Level> entry : myConstantUpperBounds.entrySet()) {
      Level level = result.get(entry.getKey());
      if (!Level.compare(level, entry.getValue(), CMP.LE, DummyEquations.getInstance(), null)) {
        myErrorReporter.report(new SolveLevelEquationsError(Collections.singletonList(new Pair<>(level, entry.getValue())), entry.getKey().getSourceNode()));
      }
    }

    for (AbstractEquation<Level> equation : myDeferredMaxEquations) {
      if (!Level.compare(equation.left().subst(result), equation.right().subst(result), equation.cmp(), DummyEquations.getInstance(), equation.sourceNode())) {
        myErrorReporter.report(new SolveLevelEquationsError(Collections.singletonList(new Pair<>(equation.left(), equation.right())), equation.sourceNode()));
      }
    }

    return result;
  }

  private void reportUnjustifiedConstants(Set<InferenceLevelVariable> unBased, Map<InferenceLevelVariable, BigInteger> solution) {
    Set<InferenceLevelVariable> lowerBounded = myEquations.getLowerBounded();
    Set<InferenceLevelVariable> upperBounded = myEquations.getUpperBounded();
    for (InferenceLevelVariable var : unBased) {
      if (!(var.isGenerated() || lowerBounded.contains(var) || upperBounded.contains(var))) {
        myErrorReporter.report(new TypecheckingError(GeneralError.Level.WARNING, "Variable " + var + " is solved to " + solution.get(var).negate() + " while not bounded from either side", var.getSourceNode()));
      }
    }
  }

  private void calculateUnBased(LevelEquations<InferenceLevelVariable> basedEquations, Set<InferenceLevelVariable> unBased, Map<InferenceLevelVariable, BigInteger> basedSolution) {
    Set<InferenceLevelVariable> unBasedSet = new HashSet<>();
    if (!myConstantUpperBounds.isEmpty()) {
      for (InferenceLevelVariable var : basedEquations.getVariables()) {
        Level ub = myConstantUpperBounds.get(var);
        if (ub != null) {
          if (ub.getVarPairs().isEmpty()) {
            unBasedSet.add(var);
          } else {
            BigInteger sol = basedSolution.get(var);
            if (ub.getVarPairs().iterator().next().getValue().compareTo(sol) < 0) {
              unBasedSet.add(var);
            }
          }
        }
      }
    }

    if (!unBasedSet.isEmpty()) {
      Stack<InferenceLevelVariable> stack = new Stack<>();
      for (InferenceLevelVariable var : unBasedSet) {
        stack.push(var);
      }

      boolean ok = true;
      while (!stack.isEmpty()) {
        InferenceLevelVariable var = stack.pop();
        Set<LevelVariable> lowerBounds = myLowerBounds.get(var);
        if (lowerBounds != null) {
          for (LevelVariable lowerBound : lowerBounds) {
            if (lowerBound instanceof InferenceLevelVariable) {
              if (unBasedSet.add((InferenceLevelVariable) lowerBound)) {
                stack.push((InferenceLevelVariable) lowerBound);
              }
            } else if (ok) {
              myErrorReporter.report(new ConstantSolveLevelEquationError(lowerBound, var.getSourceNode()));
              ok = false;
            }
          }
        }
      }
    }

    if (!unBasedSet.isEmpty()) {
      for (Map.Entry<LevelVariable, Set<InferenceLevelVariable>> entry : myUpperBounds.entrySet()) {
        entry.getValue().removeAll(unBasedSet);
      }
    }

    MapDFS<LevelVariable> dfs = new MapDFS<>(myUpperBounds);
    for (LevelVariable var : myUpperBounds.keySet()) {
      if (!(var instanceof InferenceLevelVariable)) {
        dfs.visit(var);
      }
    }
    for (InferenceLevelVariable variable : basedEquations.getVariables()) {
      if (unBasedSet.contains(variable) || !dfs.getVisited().contains(variable)) {
        unBased.add(variable);
      }
    }
  }

  private void reportCycle(List<LevelEquation<InferenceLevelVariable>> cycle) {
    Set<LevelEquation<? extends LevelVariable>> basedCycle = new LinkedHashSet<>();
    for (LevelEquation<InferenceLevelVariable> equation : cycle) {
      if (equation.getVariable1() != null) {
        basedCycle.add(new LevelEquation<>(equation));
      } else {
        basedCycle.add(new LevelEquation<>(null, equation.getVariable2(), equation.getConstant()));
      }
    }
    LevelEquation<InferenceLevelVariable> lastEquation = cycle.getLast();
    InferenceLevelVariable var = lastEquation.getVariable1() != null ? lastEquation.getVariable1() : lastEquation.getVariable2();
    myErrorReporter.report(new SolveLevelEquationsError(basedCycle, var.getSourceNode()));
  }
}
