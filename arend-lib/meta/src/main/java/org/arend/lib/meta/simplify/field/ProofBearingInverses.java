package org.arend.lib.meta.simplify.field;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFieldCallExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.lib.util.Utils;
import org.arend.ext.typechecking.TypedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.arend.lib.meta.simplify.SimplifyMeta;

/**
 * The inverses of a commutative ring: projections {@code inv} of {@code Monoid.Inv}. They carry their witness.
 */
public class ProofBearingInverses implements FieldInverses {
  private final SimplifyMeta meta;
  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;

  public ProofBearingInverses(SimplifyMeta meta, ExpressionTypechecker typechecker, ContextData contextData) {
    this.meta = meta;
    this.typechecker = typechecker;
    this.factory = contextData.getFactory().withData(contextData.getReferenceExpression());
  }

  @Override
  public @NotNull CoreClassDefinition instanceClass() {
    return meta.CRing;
  }

  @Override
  public @NotNull Matcher matcherFor(@NotNull CRingInstance instance) {
    return this::match;
  }

  protected @Nullable Inverse match(@NotNull CoreExpression expression) {
    if (!(expression.getUnderlyingExpression() instanceof CoreFieldCallExpression fieldCall && fieldCall.getDefinition() == meta.invProjection)) return null;
    CoreExpression witness = fieldCall.getArgument();
    if (!(witness.computeType().normalize(NormalizationMode.WHNF) instanceof CoreClassCallExpression classCall && classCall.getDefinition().isSubClassOf(meta.invClass))) return null;
    TypedExpression denominator = typechecker.typecheck(factory.app(factory.ref(meta.invValue.getRef()), false, Utils.concrete(factory, witness)), null);
    return denominator == null ? null : new Inverse(denominator.getExpression(), witness);
  }
}
