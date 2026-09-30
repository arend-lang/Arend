package org.arend.lib.meta.simplify.field;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import org.arend.lib.meta.simplify.SimplifyMeta;
import org.arend.lib.meta.simplify.FieldEqualityRule;
import org.arend.lib.meta.simplify.TypeSimplificationRule;

/**
 * {@code simplify} for discrete fields. The total inverse {@code DiscreteField.finv} can be cleared only under the
 * assumption that its argument is nonzero, so the meta takes the evidence as an implicit argument:
 * {@code dfield_simp {x/=0, y/=0} p}. The evidence is also searched for in the local context.
 *
 * <p>Everything else is inherited from {@link SimplifyMeta}: only the recognition of inverses is different. Since no
 * definition that {@code simplify} depends on uses this meta, the equality of the cross-products can be
 * compared in the normal form of the ring solver. Thus, without an argument, the goal is closed by {@code idp}
 * whenever it follows from clearing the denominators and a ring identity.</p>
 */
public final class DFieldSimpMeta extends SimplifyMeta {
  @Dependency                                             CoreClassDefinition DiscreteField;
  @Dependency(name = "DiscreteField.finv")                CoreClassField finv;
  @Dependency(name = "DiscreteField.nonZero-Inv")         ArendRef nonZeroInv;
  @Dependency(name = "NonZeroSemiring.inv-nonZero")       ArendRef invNonZero;
  @Dependency(name = "Set#.apartNotEqual")                ArendRef apartNotEqual;
  @Dependency(name = "FieldNormalSolverModel.terms-equality-normalized") ArendRef fieldTermsEquality;
  @Dependency(name = "FieldNormalSolverModel.terms-equality-normalized-conv") ArendRef fieldTermsEqualityConv;

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { false, true };
  }

  @Override
  public boolean allowExcessiveArguments() {
    return false;
  }

  @Override
  protected @NotNull List<TypeSimplificationRule> createTypeRules(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    return List.of(new FieldEqualityRule(this, new DiscreteFieldInverses(this, typechecker, contextData), typechecker, contextData).requested().normalizedBy(fieldTermsEquality, fieldTermsEqualityConv));
  }
}
