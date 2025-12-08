package org.arend.core.sort;

import org.arend.core.expr.Expression;
import org.arend.core.expr.PiExpression;
import org.arend.core.expr.UniverseExpression;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.core.sort.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public sealed interface SortExpression extends CoreSortExpression permits SortExpression.Const, SortExpression.Max, SortExpression.Succ, SortExpression.Pi, SortExpression.Prev, SortExpression.Var {
  @NotNull SortExpression subst(boolean isType, @NotNull List<? extends Expression> arguments, @NotNull LevelSubstitution substitution);
  @NotNull Sort withInfLevel();

  default @NotNull SortExpression subst(@NotNull LevelSubstitution substitution) {
    return subst(false, Collections.emptyList(), substitution);
  }

  default @NotNull SortExpression simplify() {
    return subst(LevelSubstitution.EMPTY);
  }

  @Override
  default @Nullable BigInteger getSortHLevel() {
    SortExpression simplified = simplify();
    if (!(simplified instanceof Const(Sort sort))) return null;
    Level level = sort.getHLevel();
    return level.isClosed() ? BigInteger.valueOf(level.getConstant()) : null;
  }

  @Override
  default boolean isProp() {
    BigInteger level = getSortHLevel();
    return level != null && level.compareTo(BigInteger.ZERO) < 0;
  }

  record Const(@NotNull Sort sort) implements SortExpression, ConstSortExpression {
    @Override
    public @NotNull Sort getSort() {
      return sort;
    }

    @Override
    public @NotNull SortExpression subst(boolean isType, @NotNull List<? extends Expression> arguments, @NotNull LevelSubstitution substitution) {
      return new Const(sort.subst(substitution));
    }

    @Override
    public @NotNull Sort withInfLevel() {
      return sort;
    }

    @Override
    public @NotNull SortExpression simplify() {
      return this;
    }
  }

  record Var(int index) implements SortExpression {
    @Override
    public @NotNull SortExpression subst(boolean isType, @NotNull List<? extends Expression> arguments, @NotNull LevelSubstitution substitution) {
      if (index >= arguments.size()) return this;

      SortExpression result;
      if (isType) {
        result = arguments.get(index) instanceof UniverseExpression universe ? universe.getSortExpression() : null;
      } else {
        Expression type = arguments.get(index);
        if (type == null) return this;
        type = type.normalize(NormalizationMode.WHNF);
        while (type instanceof PiExpression piExpr) {
          type = piExpr.getCodomain().normalize(NormalizationMode.WHNF);
        }
        result = type.getSortExpressionOfType();
      }

      return result == null ? this : result;
    }

    @Override
    public @NotNull Sort withInfLevel() {
      return Sort.INFINITY;
    }
  }

  static @NotNull SortExpression makeMax(@NotNull List<SortExpression> sorts) {
    if (sorts.isEmpty()) return new Const(Sort.PROP);
    if (sorts.size() == 1) return sorts.getFirst();

    List<SortExpression> newSorts = new ArrayList<>(sorts.size());
    Sort result = Sort.PROP;
    for (SortExpression aSort : sorts) {
      Sort newResult = aSort instanceof Const(Sort sort) ? result.max(sort) : null;
      if (newResult != null) {
        result = newResult;
      } else if (aSort instanceof Max maxSort) {
        for (SortExpression bSort : maxSort.mySorts) {
          Sort newResult2 = bSort instanceof Const(Sort sort) ? result.max(sort) : null;
          if (newResult2 != null) {
            result = newResult2;
          } else {
            if (newSorts.isEmpty() || !newSorts.getLast().equals(bSort)) newSorts.add(bSort);
          }
        }
      } else {
        if (newSorts.isEmpty() || !newSorts.getLast().equals(aSort)) newSorts.add(aSort);
      }
    }

    if (newSorts.isEmpty()) return new Const(result);
    if (result.isProp()) return newSorts.size() == 1 ? newSorts.getFirst() : new Max(newSorts);
    newSorts.add(new Const(result));
    return new Max(newSorts);
  }

  static @NotNull SortExpression makePi(@NotNull List<SortExpression> domainSorts, @NotNull SortExpression codomain) {
    SortExpression domain = makeMax(domainSorts);
    if (domain instanceof Const(Sort sort1)) {
      if (sort1.getPLevel().isClosed() && sort1.getPLevel().getConstant() == 0) {
        return codomain;
      }
      if (codomain instanceof Const (Sort sort2)) {
        return new Const(PiExpression.piSort(sort1, sort2));
      }
    }
    return domain.equals(codomain) ? codomain : domain instanceof Max maxSort ? new Pi(maxSort.mySorts, codomain) : new Pi(Collections.singletonList(domain), codomain);
  }

  final class Max implements SortExpression, MaxSortExpression {
    private final List<SortExpression> mySorts;

    private Max(List<SortExpression> sorts) {
      mySorts = sorts;
    }

    @Override
    public @NotNull List<SortExpression> getSorts() {
      return mySorts;
    }

    @Override
    public @NotNull SortExpression subst(boolean isType, @NotNull List<? extends Expression> arguments, @NotNull LevelSubstitution substitution) {
      List<SortExpression> sorts = new ArrayList<>(mySorts.size());
      for (SortExpression sort : mySorts) {
        sorts.add(sort.subst(isType, arguments, substitution));
      }
      return makeMax(sorts);
    }

    @Override
    public @NotNull Sort withInfLevel() {
      Sort result = Sort.PROP;
      for (SortExpression sort : mySorts) {
        result = result.max(sort.withInfLevel());
      }
      return result;
    }
  }

  static @NotNull SortExpression makePrev(@NotNull SortExpression sort) {
    if (sort instanceof Const(Sort aSort)) {
      if (aSort.isProp() || aSort.isSet()) return new Const(Sort.PROP);
      if (aSort.getHLevel().isClosed()) return new Const(new Sort(aSort.getPLevel(), new Level(aSort.getHLevel().getConstant() - 1)));
    }
    return new Prev(sort);
  }

  static @NotNull SortExpression makeSucc(@NotNull SortExpression sort) {
    return sort instanceof Const(Sort aSort) ? new Const(aSort.succ()) : new Succ(sort);
  }

  final class Pi implements SortExpression, PiSortExpression {
    private final List<SortExpression> myDomain;
    private final SortExpression myCodomain;

    private Pi(List<SortExpression> domain, SortExpression codomain) {
      myDomain = domain;
      myCodomain = codomain;
    }

    @Override
    public @NotNull List<SortExpression> getDomain() {
      return myDomain;
    }

    @Override
    public @NotNull SortExpression getCodomain() {
      return myCodomain;
    }

    @Override
    public @NotNull SortExpression subst(boolean isType, @NotNull List<? extends Expression> arguments, @NotNull LevelSubstitution substitution) {
      List<SortExpression> domain = new ArrayList<>(myDomain.size());
      for (SortExpression sort : myDomain) {
        domain.add(sort.subst(isType, arguments, substitution));
      }
      return makePi(domain, myCodomain.subst(isType, arguments, substitution));
    }

    @Override
    public @NotNull Sort withInfLevel() {
      Sort domain = new Max(myDomain).withInfLevel();
      Sort codomain = myCodomain.withInfLevel();
      return PiExpression.piSort(domain, codomain);
    }
  }

  final class Prev implements SortExpression, PreviousSortExpression {
    private final SortExpression mySort;

    public Prev(SortExpression sort) {
      mySort = sort;
    }

    @Override
    public @NotNull SortExpression getSort() {
      return mySort;
    }

    @Override
    public @NotNull SortExpression subst(boolean isType, @NotNull List<? extends Expression> arguments, @NotNull LevelSubstitution substitution) {
      return makePrev(mySort.subst(isType, arguments, substitution));
    }

    @Override
    public @NotNull Sort withInfLevel() {
      Sort result = mySort.withInfLevel();
      return result.isSet() || result.isProp() ? Sort.PROP : result.getHLevel().isInfinity() || !result.getHLevel().isClosed() ? result : new Sort(result.getPLevel(), new Level(result.getHLevel().getConstant() - 1));
    }
  }

  final class Succ implements SortExpression, NextSortExpression {
    private final SortExpression mySort;

    public Succ(SortExpression sort) {
      mySort = sort;
    }

    @Override
    public @NotNull SortExpression getSort() {
      return mySort;
    }

    @Override
    public @NotNull SortExpression subst(boolean isType, @NotNull List<? extends Expression> arguments, @NotNull LevelSubstitution substitution) {
      return makeSucc(mySort.subst(isType, arguments, substitution));
    }

    @Override
    public @NotNull Sort withInfLevel() {
      return mySort.withInfLevel().succ();
    }
  }
}
