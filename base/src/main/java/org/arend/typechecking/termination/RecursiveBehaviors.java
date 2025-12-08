package org.arend.typechecking.termination;

import org.arend.typechecking.computation.ComputationRunner;

import java.util.*;

public class RecursiveBehaviors<T> {
  private T myBasepoint = null;
  private final Set<RecursiveBehavior<T>> myBehaviors = new HashSet<>();
  private int myLength = -1;
  private RecursiveBehaviors<T> myBestRbAttained = null;

  public RecursiveBehaviors(HashMap<T, HashMap<T, HashSet<BaseCallMatrix<T>>>> graph, T v) {
    this(graph.get(v).get(v));
    myBasepoint = v;
  }

  private RecursiveBehaviors(Set<BaseCallMatrix<T>> callMatrices) {
    if (callMatrices != null)
      for (BaseCallMatrix<T> m : callMatrices) myBehaviors.add(new RecursiveBehavior<>(m));
    if (!myBehaviors.isEmpty()) {
      Iterator<RecursiveBehavior<T>> i = myBehaviors.iterator();
      myLength = i.next().getLength();
      while (i.hasNext()) if (myLength != i.next().getLength()) throw new IllegalArgumentException();
    }
  }

  private RecursiveBehaviors() {
  }

  private RecursiveBehaviors<T> createShorterBehavior(int i) {
    RecursiveBehaviors<T> result = new RecursiveBehaviors<>();
    for (RecursiveBehavior<T> rb : myBehaviors) {
      switch (rb.behavior.get(i)) {
        case LessThan:
          continue;
        case Equal:
          result.myBehaviors.add(new RecursiveBehavior<>(rb, i));
          continue;
        case Unknown:
          return null;
      }
    }
    result.myLength = myLength - 1;
    result.myBasepoint = myBasepoint;
    return result;
  }

  private List<Integer> findTerminationOrder(RecursiveBehaviors<T> recBehaviors, List<Integer> indices) {
    if (recBehaviors == null) throw new IllegalArgumentException();
    ComputationRunner.checkCanceled();

    if (recBehaviors.myBehaviors.isEmpty()) return indices;
    if (recBehaviors.myLength == 0) return null;

    if (myBestRbAttained == null || myBestRbAttained.myLength > recBehaviors.myLength)
      myBestRbAttained = recBehaviors;

    for (int i = 0; i < recBehaviors.myLength; i++) {
      RecursiveBehaviors<T> shorterBehavior = recBehaviors.createShorterBehavior(i);
      if (shorterBehavior != null) {
        List<Integer> shorterIndices = new LinkedList<>(indices);
        shorterIndices.remove(i);
        List<Integer> termOrder = findTerminationOrder(shorterBehavior, shorterIndices);
        if (termOrder != null) {
          termOrder.add(0, indices.get(i));
          return termOrder;
        }
      }
    }

    return null;
  }

  private List<Integer> findTerminationOrder() {
    List<Integer> indices = new LinkedList<>();
    for (int i = 0; i < myLength; i++) indices.add(i);
    return findTerminationOrder(this, indices);
  }

  public List<String> findTerminationOrderAnnotated() {
    List<Integer> to = findTerminationOrder();
    if (to == null) return null;
    if (!myBehaviors.isEmpty()) {
      RecursiveBehavior<T> rb = myBehaviors.iterator().next();
      List<String> result = new ArrayList<>();
      for (Integer i : to) result.add(rb.labels.get(i));
      return result;
    }
    return new ArrayList<>();
  }

  private Set<RecursiveBehavior<T>> onlyMinimalElements() {
    Set<RecursiveBehavior<T>> result = new HashSet<>();
    for (RecursiveBehavior<T> rb : myBehaviors) {
      boolean containsSmaller = false;
      Set<RecursiveBehavior<T>> greater = new HashSet<>();
      for (RecursiveBehavior<T> rb2 : result)
        if (rb2.leq(rb)) {
          containsSmaller = true;
          break;
        } else if (rb.leq(rb2)) {
          greater.add(rb2);
        }
      result.removeAll(greater);
      if (!containsSmaller) result.add(rb);
    }
    return result;
  }

}