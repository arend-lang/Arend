package org.arend.lib.ring;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public record Monomial(BigInteger coefficient, List<Integer> elements) implements Comparable<Monomial> {
  public Monomial multiply(Monomial m) {
    List<Integer> newElements = new ArrayList<>(elements.size() + m.elements.size());
    newElements.addAll(elements);
    newElements.addAll(m.elements);
    return new Monomial(coefficient.multiply(m.coefficient), newElements);
  }

  public static void multiply(List<Monomial> list1, List<Monomial> list2, List<Monomial> result) {
    for (Monomial monomial1 : list1) {
      for (Monomial monomial2 : list2) {
        result.add(monomial1.multiply(monomial2));
      }
    }
  }

  public Monomial negate() {
    return new Monomial(coefficient.negate(), elements);
  }

  public static void negate(List<Monomial> list, List<Monomial> result) {
    for (Monomial monomial : list) {
      result.add(monomial.negate());
    }
  }

  public static List<Monomial> collapse(List<Monomial> list) {
    List<Monomial> result = new ArrayList<>(list.size());
    for (Monomial monomial : list) {
      if (!result.isEmpty() && result.getLast().elements.equals(monomial.elements)) {
        BigInteger coef = result.getLast().coefficient.add(monomial.coefficient);
        if (coef.equals(BigInteger.ZERO)) {
          result.removeLast();
        } else {
          result.set(result.size() - 1, new Monomial(coef, monomial.elements));
        }
      } else if (!monomial.coefficient.equals(BigInteger.ZERO)) {
        result.add(monomial);
      }
    }
    return result;
  }

  public enum ComparisonResult {LESS, GREATER, EQUALS, UNCOMPARABLE}

  public ComparisonResult compare(Monomial m) {
    if (elements.size() == m.elements.size()) {
      return elements.equals(m.elements) ? ComparisonResult.EQUALS : ComparisonResult.UNCOMPARABLE;
    }
    return elements.size() < m.elements.size() ? (isLess(m) ? ComparisonResult.LESS : ComparisonResult.UNCOMPARABLE) : (m.isLess(this) ? ComparisonResult.GREATER : ComparisonResult.UNCOMPARABLE);
  }

  private boolean isLess(Monomial m) {
    for (int i = 0, j = 0; i < elements.size(); j++) {
      if (j == m.elements.size()) return false;
      int cmp = elements.get(i).compareTo(m.elements.get(j));
      if (cmp > 0) return false;
      if (cmp == 0) i++;
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Monomial monomial = (Monomial) o;
    return coefficient.equals(monomial.coefficient) && elements.equals(monomial.elements);
  }

  @Override
  public int compareTo(@NotNull Monomial o) {
    for (int i = 0; i < elements.size() && i < o.elements.size(); i++) {
      int x = elements.get(i).compareTo(o.elements.get(i));
      if (x != 0) {
        return x;
      }
    }
    return elements.size() < o.elements.size() ? -1 : elements.size() > o.elements.size() ? 1 : coefficient.compareTo(o.coefficient);
  }
}
