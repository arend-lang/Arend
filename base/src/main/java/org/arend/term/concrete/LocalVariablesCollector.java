package org.arend.term.concrete;

import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LocalVariablesCollector extends SearchConcreteVisitor<Void, Boolean> {
  private final Object myAnchor;
  private final List<Referable> myContext = new ArrayList<>();
  private List<Referable> myResult;

  public LocalVariablesCollector(Object anchor) {
    myAnchor = anchor;
  }

  public static List<Referable> getLocalReferables(@Nullable Concrete.ResolvableDefinition definition, Object anchor) {
    List<Referable> localReferables = new ArrayList<>();
    if (definition != null) {
      LocalVariablesCollector collector = new LocalVariablesCollector(anchor);
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
    myContext.remove(referable);
  }

  @Override
  public void freeEliminatedParameters(List<? extends Concrete.Parameter> parameters, List<? extends Concrete.ReferenceExpression> eliminated, Void params) {
    if (eliminated.isEmpty()) return;
    Set<Referable> elimRefs = new HashSet<>();
    for (Concrete.ReferenceExpression reference : eliminated) {
      elimRefs.add(reference.getReferent());
    }
    myContext.removeAll(elimRefs);
  }

  @Override
  public Boolean visitFunction(Concrete.BaseFunctionDefinition def, Void params) {
    int n = myContext.size();
    Boolean result = super.visitFunction(def, params);
    if (result != null) return result;
    myContext.subList(n, myContext.size()).clear();
    return null;
  }

  @Override
  public Boolean visitConstructor(Concrete.Constructor constructor, Void params) {
    int n = myContext.size();
    Boolean result = super.visitConstructor(constructor, params);
    if (result != null) return result;
    myContext.subList(n, myContext.size()).clear();
    return null;
  }

  @Override
  public Boolean visitData(Concrete.DataDefinition def, Void params) {
    int n = myContext.size();
    Boolean result = super.visitData(def, params);
    if (result != null) return result;
    myContext.subList(n, myContext.size()).clear();
    return null;
  }
}
