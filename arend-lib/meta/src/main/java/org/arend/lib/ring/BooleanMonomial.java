package org.arend.lib.ring;

import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BooleanMonomial implements Comparable<BooleanMonomial> {
  private final List<Boolean> elements;

  public BooleanMonomial(List<Boolean> elements) {
    this.elements = elements;
  }

  public static BooleanMonomial singleton(int index) {
    List<Boolean> result = new ArrayList<>();
    while (index > result.size()) {
      result.add(false);
    }
    result.add(true);
    return new BooleanMonomial(result);
  }

  public int size() {
    return elements.size();
  }

  public boolean getElement(int i) {
    return i < elements.size() && elements.get(i);
  }

  public void getIndices(Collection<Integer> result) {
    for (int i = 0; i < elements.size(); i++) {
      if (elements.get(i)) {
        result.add(i);
      }
    }
  }

  public BooleanMonomial multiply(BooleanMonomial monomial) {
    int s = Math.max(elements.size(), monomial.elements.size());
    List<Boolean> result = new ArrayList<>(s);
    for (int i = 0; i < s; i++) {
      result.add(getElement(i) || monomial.getElement(i));
    }
    return new BooleanMonomial(result);
  }

  public static void multiplyPoly(List<BooleanMonomial> poly1, List<BooleanMonomial> poly2, List<BooleanMonomial> result) {
    if (poly2.isEmpty()) return;
    for (BooleanMonomial monomial1 : poly1) {
      for (BooleanMonomial monomial2 : poly2) {
        result.add(monomial1.multiply(monomial2));
      }
    }
  }

  public static List<BooleanMonomial> collapse(List<BooleanMonomial> nf) {
    List<BooleanMonomial> result = new ArrayList<>();
    for (int i = 0; i < nf.size(); i++) {
      if (i + 1 < nf.size() && nf.get(i).equals(nf.get(i + 1))) {
        i++;
      } else {
        result.add(nf.get(i));
      }
    }
    return result;
  }

  public @Nullable BooleanMonomial divide(BooleanMonomial monomial) {
    for (int i = elements.size(); i < monomial.elements.size(); i++) {
      if (monomial.elements.get(i)) return null;
    }

    List<Boolean> result = new ArrayList<>(elements.size());
    for (int i = 0; i < elements.size(); i++) {
      if (monomial.getElement(i)) {
        if (!elements.get(i)) return null;
        result.add(false);
      } else {
        result.add(elements.get(i));
      }
    }
    return new BooleanMonomial(result);
  }

  /**
   * @return a pair (d,r) such that poly1 = d * poly2 + r.
   */
  public static @NotNull Pair<List<BooleanMonomial>, List<BooleanMonomial>> divideAndRemainder(List<BooleanMonomial> poly1, List<BooleanMonomial> poly2) {
    BooleanMonomial first = poly2.getFirst();
    List<BooleanMonomial> factor = new ArrayList<>();
    for (BooleanMonomial monomial : poly1) {
      BooleanMonomial factorMonomial = monomial.divide(first);
      if (factorMonomial != null) {
        factor.add(factorMonomial);
      }
    }

    factor.removeIf(factorMonomial -> {
      for (int i = 1; i < poly2.size(); i++) {
        if (!poly1.contains(factorMonomial.multiply(poly2.get(i)))) {
          return true;
        }
      }
      return false;
    });
    if (factor.isEmpty()) return new Pair<>(Collections.emptyList(), poly1);

    List<BooleanMonomial> leftMultiplied = new ArrayList<>();
    multiplyPoly(poly2, factor, leftMultiplied);
    Collections.sort(leftMultiplied);
    leftMultiplied = collapse(leftMultiplied);

    List<BooleanMonomial> remainder = new ArrayList<>();
    for (BooleanMonomial monomial : poly1) {
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

  @Override
  public boolean equals(Object o) {
    return o instanceof BooleanMonomial && compareTo((BooleanMonomial) o) == 0;
  }

  @Override
  public int hashCode() {
    int hc = 0;
    for (int i = elements.size() - 1; i >= 0; i--) {
      hc *= 2;
      if (elements.get(i)) ++hc;
    }
    return hc;
  }

  @Override
  public int compareTo(@NotNull BooleanMonomial o) {
    int s = Math.max(elements.size(), o.elements.size());
    for (int i = 0; i < s; i++) {
      int c = Boolean.compare(getElement(i), o.getElement(i));
      if (c < 0) return 1;
      if (c > 0) return -1;
    }
    return 0;
  }
}
