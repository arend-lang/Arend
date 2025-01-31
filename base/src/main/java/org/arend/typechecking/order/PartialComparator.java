package org.arend.typechecking.order;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface PartialComparator<T> {
  enum Result { LESS, EQUALS, GREATER, UNCOMPARABLE }

  default @NotNull Result compare(@Nullable T t1, @Nullable T t2) {
    if (t1 == null && t2 == null || t1 != null && t1.equals(t2)) return Result.EQUALS;
    if (t1 == null) return Result.UNCOMPARABLE;

    List<T> list = new ArrayList<>(2);
    list.add(t1);
    list.add(t2);
    if (!sort(list)) {
      return Result.UNCOMPARABLE;
    }

    return list.get(0).equals(t1) ? Result.LESS : Result.GREATER;
  }

  default boolean sort(List<T> list) {
    boolean ok = true;
    for (int i = 0; i < list.size(); i++) {
      int minIndex = i;
      for (int j = i + 1; j < list.size(); j++) {
        Result result = compare(list.get(j), list.get(minIndex));
        if (result == Result.UNCOMPARABLE) {
          ok = false;
        }
        if (result == Result.LESS) {
          minIndex = j;
        }
      }
      if (minIndex != i) {
        T t = list.get(i);
        list.set(i, list.get(minIndex));
        list.set(minIndex, t);
      }
    }
    return ok;
  }

  static <T> PartialComparator<T> getTrivial() {
    return new PartialComparator<>() {
      @Override
      public @NotNull Result compare(@Nullable T t1, @Nullable T t2) {
        return Result.UNCOMPARABLE;
      }

      @Override
      public boolean sort(List<T> list) {
        return list.size() <= 1;
      }
    };
  }
}
