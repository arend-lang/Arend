package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A non-local simplification rule for the type itself.
 *
 * <p>A {@link SimplificationRule} rewrites a term and returns a path between the old and the new term.
 * A type rule replaces the whole type by an equivalent one, for example an equality of fractions by an equality
 * of cross-products. Since equivalence of types is not a path, the rule returns a pair of conversions instead.
 * The simplifier uses them in both directions: to convert an argument in the forward mode and to convert the
 * proof of the simplified type into a proof of the original one in the backward mode.</p>
 */
public interface TypeSimplificationRule {
  enum Purpose {
    /** There is no argument, so the simplified type is going to be proved by {@code idp}. */
    CLOSE_GOAL,
    /** The simplified type is going to be proved by the user or is going to be shown to them. */
    TRANSFORM
  }

  /**
   * @param simplifiedType  the simplified type.
   * @param forward         a function {@code type -> simplifiedType}.
   * @param backward        a function {@code simplifiedType -> type}.
   * @param isFinal         true if the simplified type is in the form that is to be proved by {@code idp}, so that
   *                        its subterms must not be simplified further.
   */
  record Result(@NotNull CoreExpression simplifiedType, @NotNull ConcreteExpression forward, @NotNull ConcreteExpression backward, boolean isFinal) {}

  /**
   * A rule that is not requested is applied only if the rules for terms have nothing to simplify. This keeps the
   * behavior of {@code simplify} on the types where it worked before the rule was added.
   *
   * @return true if the user asked for this rule by using a meta that is built around it.
   */
  default boolean isRequested() {
    return false;
  }

  /**
   * @return the simplified type or null if the rule is not applicable.
   */
  @Nullable Result apply(@NotNull CoreExpression type, @NotNull Purpose purpose);
}
