package org.arend.util.list;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.Predicate;

public sealed interface PersistentList<T> extends Iterable<T> permits NilList, ConsList {
  static <T> NilList<T> empty() {
    //noinspection unchecked
    return (NilList<T>) NilList.INSTANCE;
  }

  default ConsList<T> cons(@Nullable T value) {
    return new ConsList<>(value, this);
  }

  boolean isEmpty();

  int size();

  @Nullable T find(@NotNull Predicate<T> predicate);

  @Override
  default @NotNull Iterator<T> iterator() {
    return new PersistentListIterator<>(this);
  }

  @NotNull PersistentList<T> removeFirst(@Nullable T value);

  @NotNull PersistentList<T> removeFirst(@NotNull Predicate<T> predicate);
}
