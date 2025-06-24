package org.arend.lib.meta.equationNew.term;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreIntegerExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.reference.ArendRef;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public sealed interface EquationTerm permits OpTerm, VarTerm, NumberTerm {
  @NotNull ConcreteExpression generateReflectedTerm(@NotNull ConcreteFactory factory, @NotNull ArendRef varRef);

  static EquationTerm match(CoreExpression expression, List<TermOperation> operations, Values<CoreExpression> values) {
    expression = expression.normalize(NormalizationMode.WHNF);

    loop:
    for (TermOperation operation : operations) {
      List<CoreExpression> args = operation.matcher().match(expression);
      if (args != null && args.size() == operation.argTypes().size()) {
        List<EquationTerm> termArgs = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
          switch (operation.argTypes().get(i)) {
            case TermType.OpType(var newOperations) -> termArgs.add(match(args.get(i), newOperations == null ? operations : newOperations, values));
            case TermType.NatType ignored -> {
              if (args.get(i).normalize(NormalizationMode.WHNF) instanceof CoreIntegerExpression intExpr) {
                termArgs.add(new NumberTerm(intExpr.getBigInteger()));
              } else {
                break loop;
              }
            }
          }
        }
        return new OpTerm(operation, termArgs);
      }
    }

    return new VarTerm(values.addValue(expression));
  }
}
