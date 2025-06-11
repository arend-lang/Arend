package org.arend.lib.meta.equationNew.term;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.jetbrains.annotations.NotNull;

public record VarTerm(int index) implements EquationTerm {
  @Override
  public @NotNull ConcreteExpression generateReflectedTerm(@NotNull ConcreteFactory factory, @NotNull ArendRef varRef) {
    return factory.app(factory.ref(varRef), true, factory.number(index));
  }
}
