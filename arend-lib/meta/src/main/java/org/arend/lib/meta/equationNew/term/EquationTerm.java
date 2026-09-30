package org.arend.lib.meta.equationNew.term;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public sealed interface EquationTerm permits OpTerm, VarTerm, NumberTerm {
  @NotNull ConcreteExpression generateReflectedTerm(@NotNull ConcreteFactory factory, @NotNull ArendRef varRef);

  static EquationTerm match(CoreExpression expression, List<TermOperation> operations, Values<CoreExpression> values) {
    return new TermMatcher(operations, values).match(expression);
  }
}
