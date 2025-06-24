package org.arend.lib.ring;

import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Monomial(BigInteger coefficient, List<Integer> elements) implements Comparable<Monomial> {
  public Monomial multiply(Monomial m) {
    List<Integer> newElements = new ArrayList<>(elements.size() + m.elements.size());
    newElements.addAll(elements);
    newElements.addAll(m.elements);
    return new Monomial(coefficient.multiply(m.coefficient), newElements);
  }

  public Monomial multiplyComm(Monomial m) {
    List<Integer> newElements = new ArrayList<>(elements.size() + m.elements.size());
    int i = 0, j = 0;
    while (i < elements.size() && j < m.elements.size()) {
      if (elements.get(i) <= m.elements.get(j)) {
        newElements.add(elements.get(i++));
      } else {
        newElements.add(m.elements.get(j++));
      }
    }
    for (; i < elements.size(); i++) {
      newElements.add(elements.get(i));
    }
    for (; j < m.elements.size(); j++) {
      newElements.add(m.elements.get(j));
    }
    return new Monomial(coefficient.multiply(m.coefficient), newElements);
  }

  public Monomial multiply(int c) {
    return c == 1 ? this : new Monomial(coefficient.multiply(BigInteger.valueOf(c)), elements);
  }

  public static void multiply(List<Monomial> list1, List<Monomial> list2, List<Monomial> result) {
    for (Monomial monomial1 : list1) {
      for (Monomial monomial2 : list2) {
        result.add(monomial1.multiply(monomial2));
      }
    }
  }

  public static void multiplyComm(List<Monomial> list1, List<Monomial> list2, List<Monomial> result) {
    for (Monomial monomial1 : list1) {
      for (Monomial monomial2 : list2) {
        result.add(monomial1.multiplyComm(monomial2));
      }
    }
  }

  public @Nullable Monomial divide(Monomial monomial) {
    if (monomial.elements.size() > elements.size()) return null;
    BigInteger[] divRem = coefficient.divideAndRemainder(monomial.coefficient);
    if (!divRem[1].equals(BigInteger.ZERO)) return null;

    int i = 0;
    List<Integer> result = new ArrayList<>();
    for (int j = 0; j < monomial.elements.size(); i++) {
      if (i >= elements.size() || elements.get(i) > monomial.elements.get(j)) return null;
      if (elements.get(i).equals(monomial.elements.get(j))) {
        j++;
      } else {
        result.add(elements.get(i));
      }
    }
    result.addAll(elements.subList(i, elements.size()));
    return new Monomial(divRem[0], result);
  }

  /**
   * @return a pair (d,r) such that poly1 = d * poly2 + r.
   */
  public static @NotNull Pair<List<Monomial>, List<Monomial>> divideAndRemainder(List<Monomial> poly1, List<Monomial> poly2) {
    Monomial first = poly2.getFirst();
    List<Monomial> factor = new ArrayList<>();
    for (Monomial monomial : poly1) {
      Monomial factorMonomial = monomial.divide(first);
      if (factorMonomial != null) {
        factor.add(factorMonomial);
      }
    }

    factor.removeIf(factorMonomial -> {
      for (int i = 1; i < poly2.size(); i++) {
        if (!poly1.contains(factorMonomial.multiplyComm(poly2.get(i)))) {
          return true;
        }
      }
      return false;
    });
    if (factor.isEmpty()) return new Pair<>(Collections.emptyList(), poly1);

    List<Monomial> leftMultiplied = new ArrayList<>();
    multiplyComm(poly2, factor, leftMultiplied);
    Collections.sort(leftMultiplied);
    leftMultiplied = collapse(leftMultiplied);

    List<Monomial> remainder = new ArrayList<>();
    for (Monomial monomial : poly1) {
      if (!leftMultiplied.remove(monomial)) {
        remainder.add(monomial);
      }
    }

    if (!leftMultiplied.isEmpty()) {
      return new Pair<>(Collections.emptyList(), poly1);
    }
    Collections.sort(factor);
    return new Pair<>(factor, remainder);
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
