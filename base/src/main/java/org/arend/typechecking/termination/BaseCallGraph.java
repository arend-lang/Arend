package org.arend.typechecking.termination;

import org.arend.ext.util.Pair;

import java.util.*;

public class BaseCallGraph<T> {
  private final HashMap<T, HashMap<T, HashSet<BaseCallMatrix<T>>>> myGraph = new HashMap<>();
  public final Set<T> myErrorInfo = new HashSet<>();
  public final int CUTOFF_COUNT = 100;

  public BaseCallGraph() {
  }

  public BaseCallGraph(BaseCallGraph<T> baseGraph) { // copy constructor
    for (Map.Entry<T, HashMap<T, HashSet<BaseCallMatrix<T>>>> e : baseGraph.myGraph.entrySet()) {
      T dom = e.getKey();
      HashMap<T, HashSet<BaseCallMatrix<T>>> out = new HashMap<>();
      for (Map.Entry<T, HashSet<BaseCallMatrix<T>>> e2 : e.getValue().entrySet()) {
        T cod = e2.getKey();
        out.put(cod, new HashSet<>(e2.getValue()));
      }
      myGraph.put(dom, out);
    }
  }

  public void add(Set<BaseCallMatrix<T>> set) {
    for (BaseCallMatrix<T> cm : set) {
      append(cm, myGraph);
    }
  }

  public HashMap<T, HashMap<T, HashSet<BaseCallMatrix<T>>>> getGraph() {
    return myGraph;
  }

  public void formErrorMessage(T vertex) {
    myErrorInfo.add(vertex);
  }

  public BaseCallGraph<T> getCompletedGraph() {
    ArrayDeque<BaseCallMatrix<T>> workEdges = new ArrayDeque<>();

    for (HashMap<T, HashSet<BaseCallMatrix<T>>> m : myGraph.values()) {
      for (HashSet<BaseCallMatrix<T>> edge : m.values()) {
        workEdges.addAll(edge);
      }
    }

    BaseCallGraph<T> currGraph = this;
    while (!workEdges.isEmpty()) {
      BaseCallGraph<T> newGraph = new BaseCallGraph<>(currGraph);

      BaseCallMatrix<T> currentEdge = workEdges.removeFirst();
      HashMap<T, HashSet<BaseCallMatrix<T>>> midOut = currGraph.getGraph().get(currentEdge.getCodomain());
      if (midOut != null) {
        for (Map.Entry<T, HashSet<BaseCallMatrix<T>>> e : midOut.entrySet()) {
          for (BaseCallMatrix<T> composableEdge : e.getValue()) {
            BaseCallMatrix<T> compositeEdge = new CompositeCallMatrix<>(currentEdge, composableEdge);
            if (append(compositeEdge, newGraph.getGraph())) {
              workEdges.addLast(compositeEdge);
            }
          }
        }
      }

      for (T dom : currGraph.getGraph().keySet()) {
        HashMap<T, HashSet<BaseCallMatrix<T>>> out = currGraph.getGraph().get(dom);
        if (out == null) continue;

        // also take a stable snapshot of potential left edges
        HashSet<BaseCallMatrix<T>> lefts = out.get(currentEdge.getDomain());
        if (lefts == null || lefts.isEmpty()) continue;

        for (BaseCallMatrix<T> left : new ArrayList<>(lefts)) {
          BaseCallMatrix<T> prod = new CompositeCallMatrix<>(left, currentEdge);
          if (append(prod, newGraph.getGraph())) {
            workEdges.addLast(prod);
          }
        }
      }

      if (!newGraph.getFailingVertices().isEmpty()) return newGraph;

      currGraph = newGraph;
    }

    return currGraph;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    for (T vDom : myGraph.keySet()) {
      for (T vCodom : myGraph.get(vDom).keySet()) {
        result.append(vDom.toString()).append(" -> ").append(vCodom.toString()).append("\n ");
        for (BaseCallMatrix<T> cm : myGraph.get(vDom).get(vCodom)) {
          result.append(cm.toString()).append("\n");
        }
      }
    }
    return result.toString();
  }

  public String toTestScenario(String edgeSetName) {
    StringBuilder result = new StringBuilder();
    for (T v : myGraph.keySet()) {
      String label = v.toString();
      String[] parameterLabels = null;
      for (T v2 : myGraph.get(v).keySet()) for (BaseCallMatrix<T> cm : myGraph.get(v).get(v2)) {
        parameterLabels = cm.getColumnLabels();
        break;
      }
      if (parameterLabels == null) throw new IllegalArgumentException();
      result.append("TestVertex ").append(label).append(" = new TestVertex(\"").append(label).append("\",");
      for (int i = 0; i < parameterLabels.length; i++) {
        result.append('"').append(parameterLabels[i]).append('"');
        if (i < parameterLabels.length-1) result.append(", ");
      }
      result.append(");\n");
    }

    Integer counter = 0;
    for (T v : myGraph.keySet()) {
      String domLabel = v.toString();
      for (T v2 : myGraph.get(v).keySet())
        for (BaseCallMatrix<T> edge : myGraph.get(v).get(v2)) {
          String codomLabel = v2.toString();
          result.append(edgeSetName).append(".add(new TestCallMatrix(\"e").append(counter).append("\", ").append(domLabel).append(", ").append(codomLabel).append(", ");
          result.append(edge.convertToTestCallMatrix());
          result.append("));\n");
          counter++;
      }
    }
    return result.toString();
  }

  static <T> boolean append(BaseCallMatrix<T> cm, HashMap<T, HashMap<T, HashSet<BaseCallMatrix<T>>>> graph) {
    HashSet<BaseCallMatrix<T>> set;
    HashMap<T, HashSet<BaseCallMatrix<T>>> map;
    if (!(graph.containsKey(cm.getDomain()))) {
      map = new HashMap<>();
      set = new HashSet<>();
      set.add(cm);
      map.put(cm.getCodomain(), set);
      graph.put(cm.getDomain(), map);
      return true;
    } else {
      map = graph.get(cm.getDomain());
      if (!(map.containsKey(cm.getCodomain()))) {
        set = new HashSet<>();
        set.add(cm);
        map.put(cm.getCodomain(), set);
        return true;
      } else {
        set = map.get(cm.getCodomain());
        boolean alreadyContainsSmaller = set.contains(cm);

        if (!alreadyContainsSmaller) for (BaseCallMatrix<T> arrow : set)
          if (arrow.compare(cm) != BaseCallMatrix.R.Unknown) {
            alreadyContainsSmaller = true;
            break;
          }

        if (!alreadyContainsSmaller) {
          HashSet<BaseCallMatrix<T>> toRemove = new HashSet<>();
          for (BaseCallMatrix<T> arrow : set)
            if (cm.compare(arrow) == BaseCallMatrix.R.LessThan)
              toRemove.add(arrow);
          set.removeAll(toRemove);
          set.add(cm);
        }

        return !alreadyContainsSmaller;
      }
    }
  }

  public Pair<Boolean, BaseCallGraph<T>> checkTermination() {
    if (myGraph.size() == 1) {
      // attempt less computationally involving criterion of recursive behavior (no need for graph completion)
      T v = myGraph.keySet().iterator().next();
      RecursiveBehaviors<T> rbs = new RecursiveBehaviors<>(myGraph, v);
      List<String> order = rbs.findTerminationOrderAnnotated();
      if (order != null) return new Pair<>(true, this);
    }

    BaseCallGraph<T> completedGraph = getCompletedGraph();
    Set<T> failingVertices = completedGraph.getFailingVertices();

    if (!failingVertices.isEmpty())
      for (T v : myGraph.keySet()) formErrorMessage(v);

    return new Pair<>(failingVertices.isEmpty(), completedGraph);
  }

  public Set<T> getFailingVertices() {
    Set<T> result = new HashSet<>(verticesWithTooManyLoops());
    if (!result.isEmpty()) return result;

    for (T v : myGraph.keySet()) {
      HashMap<T, HashSet<BaseCallMatrix<T>>> map = myGraph.get(v);
      if (map == null) continue;
      HashSet<BaseCallMatrix<T>> loops = map.get(v);
      if (loops == null) continue;
      for (BaseCallMatrix<T> loop : loops)
        if (!loop.hasLessThanSomewhere() || (loop.isIdempotent()) && !loop.hasLessThanOnDiagonal()) {
        result.add(v);
        break;
      }
    }

    return result;
  }

  public Set<T> verticesWithTooManyLoops() {
    Set<T> result = new HashSet<>();
    for (T v : myGraph.keySet()) {
      HashMap<T, HashSet<BaseCallMatrix<T>>> map = myGraph.get(v);
      if (map != null) {
        HashSet<BaseCallMatrix<T>> loops = map.get(v);
        if (loops != null && loops.size() > CUTOFF_COUNT)
          result.add(v);
      }
    }
    return result;
  }

}
