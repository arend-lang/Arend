package org.arend.util.list;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class NilList<T> implements PersistentList<T> {
  public final static NilList<Object> INSTANCE = new NilList<>();

  private NilList() {}

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public @Nullable T find(@NotNull Predicate<T> predicate) {
    return null;
  }

  @Override
  public String toString() {
    return "[]";
  }
}
