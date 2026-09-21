package org.arend.lib.meta.simplify.field;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Says which expressions are inverses that may be cleared and where the justification comes from.
 *
 * <p>This is the only place where the ordinary {@code simplify} and {@code dfield_simp} differ.
 * A proof-bearing {@code Monoid.Inv.inv} carries its witness. A total {@code DiscreteField.finv} does not,
 * so its witness has to be built from evidence that the argument is nonzero.</p>
 */
public interface FieldInverses {
  /**
   * @param denominator the element that is being inverted.
   * @param witness     a proof of {@code Inv denominator} whose {@code inv} is the matched expression.
   */
  record Inverse(@NotNull CoreExpression denominator, @NotNull CoreExpression witness) {}

  interface Matcher {
    /**
     * @return the inverse if the expression is one that can be cleared.
     */
    @Nullable Inverse match(@NotNull CoreExpression expression);
  }

  /**
   * @return the class whose instances are searched for.
   */
  @NotNull CoreClassDefinition instanceClass();

  @NotNull Matcher matcherFor(@NotNull CRingInstance instance);
}
