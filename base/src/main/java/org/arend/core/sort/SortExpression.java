package org.arend.core.sort;

import org.arend.core.expr.PiExpression;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.sort.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public sealed interface SortExpression extends CoreSortExpression permits SortExpression.Const, SortExpression.Max, SortExpression.Pi, SortExpression.Prev, SortExpression.Next {
  @NotNull SortExpression subst(@NotNull LevelSubstitution substitution);

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
    public @NotNull SortExpression subst(@NotNull LevelSubstitution substitution) {
      return new Const(sort.subst(substitution));
    }

    @Override
    public @NotNull SortExpression simplify() {
      return this;
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
            newSorts.add(bSort);
          }
        }
      } else {
        newSorts.add(aSort);
      }
    }

    if (newSorts.isEmpty()) return new Const(result);
    if (result.isProp()) return newSorts.size() == 1 ? newSorts.getFirst() : new Max(newSorts);
    newSorts.add(new Const(result));
    return new Max(newSorts);
  }

  static @NotNull SortExpression makePi(@NotNull List<SortExpression> domainSorts, @NotNull SortExpression codomain) {
    SortExpression domain = makeMax(domainSorts);
    if (domain instanceof Const(Sort sort1) && codomain instanceof Const (Sort sort2)) {
      return new Const(PiExpression.piSort(sort1, sort2));
    }
    return domain instanceof Max maxSort ? new Pi(maxSort.mySorts, codomain) : new Pi(Collections.singletonList(domain), codomain);
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
    public @NotNull SortExpression subst(@NotNull LevelSubstitution substitution) {
      List<SortExpression> sorts = new ArrayList<>(mySorts.size());
      for (SortExpression sort : mySorts) {
        sorts.add(sort.subst(substitution));
      }
      return makeMax(sorts);
    }
  }

  static @NotNull SortExpression makePrev(@NotNull SortExpression sort) {
    if (sort instanceof Const(Sort aSort)) {
      if (aSort.isProp() || aSort.isSet()) return new Const(Sort.PROP);
      if (aSort.getHLevel().isClosed()) return new Const(new Sort(aSort.getPLevel(), new Level(aSort.getHLevel().getConstant() - 1)));
    }
    return new Prev(sort);
  }

  static @NotNull SortExpression makeNext(@NotNull SortExpression sort) {
    return sort instanceof Const(Sort aSort) ? new Const(aSort.succ()) : new Next(sort);
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
    public @NotNull SortExpression subst(@NotNull LevelSubstitution substitution) {
      List<SortExpression> domain = new ArrayList<>(myDomain.size());
      for (SortExpression sort : myDomain) {
        domain.add(sort.subst(substitution));
      }
      return makePi(domain, myCodomain.subst(substitution));
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
    public @NotNull SortExpression subst(@NotNull LevelSubstitution substitution) {
      return makePrev(mySort.subst(substitution));
    }
  }

  final class Next implements SortExpression, NextSortExpression {
    private final SortExpression mySort;

    public Next(SortExpression sort) {
      mySort = sort;
    }

    @Override
    public @NotNull SortExpression getSort() {
      return mySort;
    }

    @Override
    public @NotNull SortExpression subst(@NotNull LevelSubstitution substitution) {
      return makeNext(mySort.subst(substitution));
    }
  }
}
