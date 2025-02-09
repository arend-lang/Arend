package org.arend.util.list;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public final class ConsList<T> implements PersistentList<T> {
  private final T myValue;
  private final PersistentList<T> myTail;
  private final int mySize;

  public ConsList(T value, PersistentList<T> tail) {
    myValue = value;
    myTail = tail;
    mySize = tail.size() + 1;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public @Nullable T find(@NotNull Predicate<T> predicate) {
    for (PersistentList<T> list = this; list instanceof ConsList<T> cons; list = cons.myTail) {
      if (predicate.test(cons.myValue)) return cons.myValue;
    }
    return null;
  }

  public T getValue() {
    return myValue;
  }

  public PersistentList<T> getTail() {
    return myTail;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConsList<?> consList = (ConsList<?>) o;
    if (mySize != consList.mySize || !Objects.equals(myValue, consList.myValue)) return false;

    PersistentList<T> list1 = myTail;
    PersistentList<?> list2 = consList.myTail;
    while (true) {
      if (list1 instanceof NilList<T> && list2 instanceof NilList<?>) return true;
      if (!(list1 instanceof ConsList<T> cons1 && list2 instanceof ConsList<?> cons2 && Objects.equals(cons1.myValue, cons2.myValue))) return false;
      list1 = cons1.myTail;
      list2 = cons2.myTail;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(myValue, myTail);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("[");
    builder.append(myValue);
    for (PersistentList<T> list = myTail; list instanceof ConsList<T> cons; list = cons.myTail) {
      builder.append(", ").append(cons.myValue);
    }
    builder.append("]");
    return builder.toString();
  }
}
