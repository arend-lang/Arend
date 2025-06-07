package org.arend.term.concrete;

import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LocalVariablesCollector extends SearchConcreteVisitor<Void, Boolean> {
  private final Object myAnchor;
  private final List<Referable> myContext = new ArrayList<>();
  private List<Referable> myResult;
  private boolean myFreeData = true;

  public LocalVariablesCollector(Object anchor) {
    myAnchor = anchor;
  }

  public LocalVariablesCollector(Object anchor, boolean freeData) {
    myAnchor = anchor;
    myFreeData = freeData;
  }

  public static List<Referable> getLocalReferables(@Nullable Concrete.ResolvableDefinition definition, Object anchor) {
    List<Referable> localReferables = new ArrayList<>();
    if (definition != null) {
      LocalVariablesCollector collector = new LocalVariablesCollector(anchor, false);
      definition.accept(collector, null);
      List<Referable> collectedResult = collector.getResult();
      if (collectedResult != null) localReferables.addAll(collectedResult);
    }
    return localReferables;
  }

  public @Nullable List<Referable> getResult() {
    return myResult;
  }

  public @NotNull Set<String> getNames() {
    if (myResult == null) return Collections.emptySet();
    Set<String> result = new HashSet<>();
    for (Referable referable : myResult) {
      result.add(referable.getRefName());
    }
    return result;
  }

  @Override
  protected Boolean checkSourceNode(Concrete.SourceNode sourceNode, Void params) {
    if (myAnchor == null) {
      return null;
    }
    if (myAnchor.equals(sourceNode.getData())) {
      myResult = new ArrayList<>(myContext);
      return true;
    }
    return null;
  }

  @Override
  public void visitReferable(Referable referable, Void params) {
    myContext.add(referable);
  }

  @Override
  public void freeReferable(Referable referable, Void params) {
    if (!myFreeData) return;
    myContext.removeLast();
  }

  @Override
  public void freeEliminatedParameters(List<? extends Concrete.Parameter> parameters, List<? extends Concrete.ReferenceExpression> eliminated, Void params) {
    if (eliminated.isEmpty()) return;
    Set<Referable> elimRefs = new HashSet<>();
    for (Concrete.ReferenceExpression reference : eliminated) {
      elimRefs.add(reference.getReferent());
    }
    if (!myFreeData) return;
    myContext.removeAll(elimRefs);
  }

  private boolean checkNullAnchor() {
    if (myAnchor == null) {
      myResult = new ArrayList<>(myContext);
      return true;
    }
    return false;
  }

  @Override
  public Boolean visitFunction(Concrete.BaseFunctionDefinition def, Void params) {
    int n = myContext.size();
    Boolean result = super.visitFunction(def, params);
    if (checkNullAnchor()) return true;
    if (result != null) return result;
    myContext.subList(n, myContext.size()).clear();
    return null;
  }

  @Override
  public Boolean visitConstructor(Concrete.Constructor constructor, Void params) {
    int n = myContext.size();
    Boolean result = super.visitConstructor(constructor, params);
    if (checkNullAnchor()) return true;
    if (result != null) return result;
    myContext.subList(n, myContext.size()).clear();
    return null;
  }

  @Override
  public Boolean visitData(Concrete.DataDefinition def, Void params) {
    int n = myContext.size();
    Boolean result = super.visitData(def, params);
    if (checkNullAnchor()) return true;
    if (result != null) return result;
    myContext.subList(n, myContext.size()).clear();
    return null;
  }
}
