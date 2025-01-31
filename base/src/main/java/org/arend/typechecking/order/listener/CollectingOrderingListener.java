package org.arend.typechecking.order.listener;

import org.arend.term.concrete.Concrete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectingOrderingListener implements OrderingListener {
  public interface Element {
    void feedTo(OrderingListener listener);
    default Concrete.ResolvableDefinition getAnyDefinition() {
      return getAllDefinitions().get(0);
    }
    List<? extends Concrete.ResolvableDefinition> getAllDefinitions();
  }

  private record MyHeader(Concrete.ResolvableDefinition definition) implements Element {
    @Override
    public void feedTo(OrderingListener listener) {
      listener.headerFound(definition);
    }

    @Override
    public Concrete.ResolvableDefinition getAnyDefinition() {
      return definition;
    }

    @Override
    public List<? extends Concrete.ResolvableDefinition> getAllDefinitions() {
      return Collections.singletonList(definition);
    }
  }

  private record MyUnit(Concrete.ResolvableDefinition definition, boolean withLoops) implements Element {
    @Override
    public void feedTo(OrderingListener listener) {
      listener.unitFound(definition, withLoops);
    }

    @Override
    public Concrete.ResolvableDefinition getAnyDefinition() {
      return definition;
    }

    @Override
    public List<? extends Concrete.ResolvableDefinition> getAllDefinitions() {
      return Collections.singletonList(definition);
    }
  }

  private record MyDefinitions(List<? extends Concrete.ResolvableDefinition> definitions, CollectingOrderingListener.MyDefinitions.Kind kind) implements Element {
    enum Kind { CYCLE, INSTANCE_CYCLE, PRE_BODIES, BODIES }

    @SuppressWarnings("unchecked")
    @Override
    public void feedTo(OrderingListener listener) {
      switch (kind) {
        case CYCLE -> listener.cycleFound((List<Concrete.ResolvableDefinition>) definitions, false);
        case INSTANCE_CYCLE -> listener.cycleFound((List<Concrete.ResolvableDefinition>) definitions, true);
        case PRE_BODIES -> listener.preBodiesFound((List<Concrete.ResolvableDefinition>) definitions);
        case BODIES -> listener.bodiesFound((List<Concrete.ResolvableDefinition>) definitions);
      }
    }

    @Override
    public List<? extends Concrete.ResolvableDefinition> getAllDefinitions() {
      return definitions;
    }
  }

  private final List<Element> myElements = new ArrayList<>();

  public boolean isEmpty() {
    return myElements.isEmpty();
  }

  public void clear() {
    myElements.clear();
  }

  public List<Element> getElements() {
    return myElements;
  }

  @Override
  public void unitFound(Concrete.ResolvableDefinition unit, boolean recursive) {
    myElements.add(new MyUnit(unit, recursive));
  }

  @Override
  public void cycleFound(List<Concrete.ResolvableDefinition> definitions, boolean isInstance) {
    myElements.add(new MyDefinitions(definitions, isInstance ? MyDefinitions.Kind.INSTANCE_CYCLE : MyDefinitions.Kind.CYCLE));
  }

  @Override
  public void preBodiesFound(List<Concrete.ResolvableDefinition> definitions) {
    myElements.add(new MyDefinitions(definitions, MyDefinitions.Kind.PRE_BODIES));
  }

  @Override
  public void headerFound(Concrete.ResolvableDefinition definition) {
    myElements.add(new MyHeader(definition));
  }

  @Override
  public void bodiesFound(List<Concrete.ResolvableDefinition> bodies) {
    myElements.add(new MyDefinitions(bodies, MyDefinitions.Kind.BODIES));
  }

  public void feed(OrderingListener listener) {
    for (Element element : myElements) {
      element.feedTo(listener);
    }
  }
}
