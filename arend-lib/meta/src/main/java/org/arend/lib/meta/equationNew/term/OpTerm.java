package org.arend.lib.meta.equationNew.term;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record OpTerm(TermOperation operation, List<EquationTerm> arguments) implements EquationTerm {
  @Override
  public @NotNull ConcreteExpression generateReflectedTerm(@NotNull ConcreteFactory factory, @NotNull ArendRef varRef) {
    List<ConcreteExpression> args = new ArrayList<>(arguments.size());
    for (EquationTerm argument : arguments) {
      args.add(argument.generateReflectedTerm(factory, varRef));
    }
    return operation.generator().apply(factory, args);
  }
}
