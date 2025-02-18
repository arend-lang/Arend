package org.arend.server;

import org.jetbrains.annotations.NotNull;

public interface ProgressReporter<T> {
  void beginProcessing(int numberOfItems);

  void beginItem(@NotNull T item);

  void endItem(@NotNull T item);

  ProgressReporter<?> EMPTY = new ProgressReporter<>() {
    @Override
    public void beginProcessing(int numberOfItems) {}

    @Override
    public void beginItem(@NotNull Object item) {}

    @Override
    public void endItem(@NotNull Object item) {}
  };

  static <T> ProgressReporter<T> empty() {
    //noinspection unchecked
    return (ProgressReporter<T>) EMPTY;
  }
}
