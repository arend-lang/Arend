package org.arend.lib.meta.equationNew.term;

import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreIntegerExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reflects an expression into an {@link EquationTerm}: subexpressions that are applications of the given operations
 * become {@link OpTerm}s and all others become variables, which are stored in {@code values}.
 *
 * <p>The way to extend it is to override {@link #match} and {@link #matchOperation}. For example, a subclass may
 * recognize an operation that carries additional data or inspect an expression before it is reduced.</p>
 */
public class TermMatcher {
  private final List<TermOperation> operations;
  protected final Values<CoreExpression> values;

  public TermMatcher(@NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values) {
    this.operations = operations;
    this.values = values;
  }

  public @NotNull EquationTerm match(@NotNull CoreExpression expression) {
    return match(expression, operations);
  }

  /**
   * Reduces the expression to the weak head normal form and matches it.
   *
   * @param operations  the operations that are allowed at this position.
   */
  protected @NotNull EquationTerm match(@NotNull CoreExpression expression, @NotNull List<TermOperation> operations) {
    expression = expression.normalize(NormalizationMode.WHNF);
    EquationTerm term = matchOperation(expression, operations);
    return term != null ? term : new VarTerm(values.addValue(expression));
  }

  /**
   * Matches the expression as it is, without reducing it. The arguments of an operation are matched by {@link #match}.
   *
   * @return null if the expression is not an application of one of the operations.
   */
  protected @Nullable EquationTerm matchOperation(@NotNull CoreExpression expression, @NotNull List<TermOperation> operations) {
    for (TermOperation operation : operations) {
      List<CoreExpression> args = operation.matcher().match(expression);
      if (args != null && args.size() == operation.argTypes().size()) {
        List<EquationTerm> termArgs = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
          switch (operation.argTypes().get(i)) {
            case TermType.OpType(var newOperations) -> termArgs.add(match(args.get(i), newOperations == null ? operations : newOperations));
            case TermType.NatType ignored -> {
              if (args.get(i).normalize(NormalizationMode.WHNF) instanceof CoreIntegerExpression intExpr) {
                termArgs.add(new NumberTerm(intExpr.getBigInteger()));
              } else {
                return null;
              }
            }
          }
        }
        return new OpTerm(operation, termArgs);
      }
    }
    return null;
  }
}
