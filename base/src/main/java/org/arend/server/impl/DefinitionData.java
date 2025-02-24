package org.arend.server.impl;

import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteCompareVisitor;
import org.arend.util.list.PersistentList;

import java.util.Map;
import java.util.function.Consumer;

public record DefinitionData(Concrete.ResolvableDefinition definition, PersistentList<TCDefReferable> instances) {
  public boolean compare(DefinitionData other, Map<Object, Consumer<Concrete.SourceNode>> dataUpdater) {
    return definition.accept(new ConcreteCompareVisitor(dataUpdater), other.definition) && instances.equals(other.instances) && dataUpdater.isEmpty();
  }
}
