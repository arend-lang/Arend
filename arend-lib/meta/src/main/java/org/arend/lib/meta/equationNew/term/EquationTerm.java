package org.arend.lib.meta.equationNew.term;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.reference.ArendRef;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public sealed interface EquationTerm permits OpTerm, VarTerm {
  @NotNull ConcreteExpression generateReflectedTerm(@NotNull ConcreteFactory factory, @NotNull ArendRef varRef);

  static EquationTerm match(CoreExpression expression, List<TermOperation> operations, Values<CoreExpression> values) {
    expression = expression.normalize(NormalizationMode.WHNF);

    for (TermOperation operation : operations) {
      List<CoreExpression> args = operation.matcher().match(expression);
      if (args != null) {
        List<EquationTerm> termArgs = new ArrayList<>(args.size());
        for (CoreExpression arg : args) {
          termArgs.add(match(arg, operations, values));
        }
        return new OpTerm(operation, termArgs);
      }
    }

    return new VarTerm(values.addValue(expression));
  }
}
