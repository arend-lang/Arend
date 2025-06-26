package org.arend.lib.meta.equationNew.term;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public record NumberTerm(@NotNull BigInteger number) implements EquationTerm {
  @Override
  public @NotNull ConcreteExpression generateReflectedTerm(@NotNull ConcreteFactory factory, @NotNull ArendRef varRef) {
    return factory.number(number);
  }
}
