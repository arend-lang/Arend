package org.arend.lib.meta.positivity;

/**
 * The kinds of facts {@code positivity} proves about a term {@code t}: {@code 0 < t}, {@code 0 <= t}, and {@code t /= 0}.
 */
public enum Strictness {
  POSITIVE("0 < _"), NONNEGATIVE("0 <= _"), NONZERO("_ /= 0");

  private final String shape;

  Strictness(String shape) {
    this.shape = shape;
  }

  @Override
  public String toString() {
    return shape;
  }
}
