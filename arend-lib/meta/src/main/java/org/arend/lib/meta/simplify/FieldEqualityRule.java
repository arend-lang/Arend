package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.core.expr.CorePiExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.arend.lib.meta.simplify.field.CRingInstance;
import org.arend.lib.meta.simplify.field.FieldInverses;
import org.arend.lib.meta.simplify.field.FieldReifier;

/**
 * Eliminates denominators from an equality in a commutative ring: {@code a * x⁻¹ = b * y⁻¹} becomes
 * {@code a * y = b * x}. The whole equality is reflected at once, since an inverse changes the denominator of the
 * whole side and cannot be cleared by rewriting isolated subterms.
 *
 * <p>The rule is applicable only if some side contains an inverse recognized by {@link FieldInverses}.</p>
 */
public final class FieldEqualityRule implements TypeSimplificationRule {
  private final SimplifyMeta meta;
  private final FieldInverses inverseSource;
  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;
  private final ConcreteReferenceExpression marker;
  private @Nullable ArendRef normalizedEquality;
  private @Nullable ArendRef normalizedEqualityConv;
  private boolean requested;

  public FieldEqualityRule(SimplifyMeta meta, FieldInverses inverseSource, ExpressionTypechecker typechecker, ContextData contextData) {
    this.meta = meta;
    this.inverseSource = inverseSource;
    this.typechecker = typechecker;
    this.marker = contextData.getReferenceExpression();
    this.factory = contextData.getFactory().withData(marker);
  }

  /**
   * By default, the equality is proved from the equality of the cross-products as they are. The lemmas that compare
   * their normal forms, so that {@code idp} proves the result whenever the cross-products are equal as polynomials,
   * can be used when there is no argument.
   */
  public FieldEqualityRule normalizedBy(ArendRef equality, ArendRef conversion) {
    normalizedEquality = equality;
    normalizedEqualityConv = conversion;
    return this;
  }

  public FieldEqualityRule requested() {
    requested = true;
    return this;
  }

  @Override
  public boolean isRequested() {
    return requested;
  }

  @Override
  public @Nullable Result apply(@NotNull CoreExpression type, @NotNull Purpose purpose) {
    CoreFunCallExpression equality = type.normalize(NormalizationMode.WHNF).toEquality();
    if (equality == null) return null;

    CoreExpression carrier = equality.getDefCallArguments().getFirst();
    CRingInstance instance = CRingInstance.find(meta, inverseSource, typechecker, Utils.solveInferenceVariable(carrier.normalize(NormalizationMode.WHNF), typechecker), marker);
    if (instance == null) return null;

    Values<CoreExpression> values = new Values<>(typechecker, marker);
    var reifier = new FieldReifier(meta, typechecker, factory, marker, instance, inverseSource.matcherFor(instance), values);
    var left = reifier.reify(equality.getDefCallArguments().get(1));
    var right = reifier.reify(equality.getDefCallArguments().get(2));
    if (!reifier.hasInverses()) return null;

    TypedExpression env = reifier.environment(carrier);
    if (env == null) return null;
    ConcreteExpression envExpr = factory.core(env);

    // With a proof to be given, the cross-products are left as they are; otherwise they are compared in the normal form.
    boolean normalized = purpose == Purpose.CLOSE_GOAL && normalizedEquality != null && normalizedEqualityConv != null;
    ConcreteExpression backward = reifier.apply(normalized ? normalizedEquality : meta.fieldTermsEqualityRaw, envExpr, left, right);
    ConcreteExpression forward = reifier.apply(normalized ? normalizedEqualityConv : meta.fieldTermsEqualityRawConv, envExpr, left, right);

    Pair<TypedExpression, TypedExpression> checked = Utils.tryTypecheck(typechecker, tc -> {
      TypedExpression checkedBackward = tc.typecheck(backward, null);
      TypedExpression checkedForward = checkedBackward == null ? null : tc.typecheck(forward, null);
      return checkedForward == null ? null : new Pair<>(checkedBackward, checkedForward);
    });
    if (checked == null || !(checked.proj1.getType().normalize(NormalizationMode.WHNF) instanceof CorePiExpression pi)) return null;

    CoreExpression simplified = pi.getParameters().getBinding().getType();
    return new Result(normalized ? simplified : tidy(simplified), factory.core(checked.proj2), factory.core(checked.proj1), normalized);
  }

  /** The lemmas state the equality in terms of {@code interpret}; show the sides evaluated. */
  private CoreExpression tidy(CoreExpression type) {
    CoreFunCallExpression equality = type.normalize(NormalizationMode.WHNF).toEquality();
    if (equality == null) return type;
    var arguments = equality.getDefCallArguments();
    ConcreteExpression tidied = factory.appBuilder(factory.ref(factory.getPrelude().getEqualityRef()))
      .app(Utils.concrete(factory, arguments.get(0)), false)
      .app(Utils.concrete(factory, arguments.get(1).normalize(NormalizationMode.ENF)))
      .app(Utils.concrete(factory, arguments.get(2).normalize(NormalizationMode.ENF)))
      .build();
    TypedExpression result = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(tidied, null));
    return result == null ? type : result.getExpression();
  }
}
