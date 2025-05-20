package org.arend.term.concrete;

public interface ConcreteResolvableDefinitionVisitor<P, R> extends ConcreteDefinitionVisitor<P, R> {
  R visitMeta(Concrete.MetaDefinition def, P params);
}
