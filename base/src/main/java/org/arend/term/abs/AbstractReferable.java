package org.arend.term.abs;

public interface AbstractReferable {
  default boolean isErrorReferable() {
    return false;
  }
}