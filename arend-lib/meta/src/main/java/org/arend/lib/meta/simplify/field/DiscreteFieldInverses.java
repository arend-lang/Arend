package org.arend.lib.meta.simplify.field;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.concrete.expr.ConcreteAppExpression;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Inverses of a discrete field. A total {@code DiscreteField.finv x} is an inverse only if {@code x /= 0}, so it is
 * cleared only if some evidence for this particular {@code x} is available. The evidence is taken from the
 * expressions given by the user and, if none of them fits, from the local context. Proof-bearing inverses
 * are still recognized.
 */
final class DiscreteFieldInverses implements FieldInverses {
  private final DFieldSimpMeta meta;
  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;
  private final ConcreteSourceNode marker;
  private final List<? extends ConcreteExpression> evidence;
  private final List<CoreBinding> localBindings;
  private final ProofBearingInverses proofBearing;

  private static final int MAX_CANDIDATES = 32;

  /**
   * The evidence is the implicit argument of the meta; the local context is searched afterwards.
   */
  DiscreteFieldInverses(DFieldSimpMeta meta, ExpressionTypechecker typechecker, ContextData contextData) {
    var arguments = contextData.getArguments();
    this.meta = meta;
    this.typechecker = typechecker;
    this.marker = contextData.getReferenceExpression();
    this.factory = contextData.getFactory().withData(marker);
    this.evidence = arguments.isEmpty() || arguments.getFirst().isExplicit() ? List.of() : Utils.getArgumentList(arguments.getFirst().getExpression());
    this.localBindings = typechecker.getFreeBindingsList();
    this.proofBearing = new ProofBearingInverses(meta, typechecker, contextData);
  }

  @Override
  public @NotNull CoreClassDefinition instanceClass() {
    return meta.DiscreteField;
  }

  @Override
  public @NotNull Matcher matcherFor(@NotNull CRingInstance instance) {
    // The matcher uses the original view of the instance: finv of another instance on the same carrier is not ours.
    FunctionMatcher finvMatcher = FunctionMatcher.makeFieldMatcher(instance.original().classCall(), instance.original().expression(), meta.finv, typechecker, factory, marker, 1);
    return expression -> {
      Inverse result = matchFinv(expression, finvMatcher, instance);
      return result != null ? result : proofBearing.match(expression);
    };
  }

  private @Nullable Inverse matchFinv(CoreExpression expression, FunctionMatcher finvMatcher, CRingInstance instance) {
    List<CoreExpression> arguments = finvMatcher.match(expression);
    if (arguments == null || arguments.size() != 1) return null;

    CoreExpression source = preferSyntacticArgument(expression, arguments.getFirst());
    if (!isSelectedFinv(expression, source, instance)) return null;
    CoreExpression witness = findNonZeroInverse(instance, source);
    return witness == null ? null : new Inverse(source, witness);
  }

  /** Prevents a generic field matcher from accepting the inverse of another instance. */
  private boolean isSelectedFinv(CoreExpression expression, CoreExpression source, CRingInstance instance) {
    TypedExpression selected = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(factory.appBuilder(factory.ref(meta.finv.getRef())).app(factory.core(instance.original().expression()), false).app(Utils.concrete(factory, source)).build(), null));
    return selected != null && Utils.safeCompare(typechecker, selected.getExpression(), expression, CMP.EQ, null, false, true, true);
  }

  /**
   * The matcher may solve its argument with the weak head normal form of the actual argument. Keep the argument as it
   * is written when possible: evidence such as {@code fac/=0} is stated for {@code fac (suc n)}, not for its reduct.
   */
  private CoreExpression preferSyntacticArgument(CoreExpression expression, CoreExpression matched) {
    CoreExpression underlying = expression.getUnderlyingExpression();
    if (underlying instanceof CoreAppExpression app && Utils.safeCompare(typechecker, app.getArgument(), matched, CMP.EQ, null, false, true, true)) {
      return app.getArgument();
    }
    if (underlying instanceof CoreDefCallExpression defCall) {
      List<? extends CoreExpression> arguments = defCall.getDefCallArguments();
      for (int i = arguments.size() - 1; i >= 0; i--) {
        if (Utils.safeCompare(typechecker, arguments.get(i), matched, CMP.EQ, null, false, true, true)) return arguments.get(i);
      }
    }
    return matched;
  }

  private @Nullable CoreExpression findNonZeroInverse(CRingInstance instance, CoreExpression source) {
    for (ConcreteExpression template : evidence) {
      CoreExpression witness = tryEvidence(template, instance, source);
      if (witness == null) witness = tryInstantiated(template, instance, source);
      if (witness != null) return witness;
    }
    for (CoreBinding binding : localBindings) {
      CoreExpression witness = tryEvidence(factory.ref(binding), instance, source);
      if (witness != null) return witness;
    }
    return null;
  }

  /**
   * Reduction may erase the pattern that the evidence is stated for: {@code fac (suc n)} reduces to
   * {@code suc n * fac n}, and evidence such as {@code natRat/=0 fac/=0} with open implicit arguments no longer fits.
   * Then the implicit arguments of the evidence are instantiated with the subterms of the denominator.
   */
  private @Nullable CoreExpression tryInstantiated(ConcreteExpression template, CRingInstance instance, CoreExpression source) {
    List<CoreExpression> candidates = new ArrayList<>();
    Set<CoreExpression> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    source.processSubexpression(subexpression -> {
      if (candidates.size() < MAX_CANDIDATES && seen.add(subexpression)) candidates.add(subexpression);
      return CoreExpression.FindAction.CONTINUE;
    });

    for (CoreExpression candidate : candidates) {
      for (ConcreteExpression variant : withImplicitArgument(template, Utils.concrete(factory, candidate))) {
        CoreExpression witness = tryEvidence(variant, instance, source);
        if (witness != null) return witness;
      }
    }
    return null;
  }

  /** @return the variants of the expression in which exactly one application gains the implicit argument. */
  private List<ConcreteExpression> withImplicitArgument(ConcreteExpression expression, ConcreteExpression argument) {
    List<ConcreteExpression> result = new ArrayList<>();
    result.add(factory.app(expression, false, argument));
    if (expression instanceof ConcreteAppExpression app) {
      for (ConcreteExpression function : withImplicitArgument(app.getFunction(), argument)) {
        result.add(factory.app(function, app.getArguments()));
      }
      for (int i = 0; i < app.getArguments().size(); i++) {
        ConcreteArgument old = app.getArguments().get(i);
        for (ConcreteExpression variant : withImplicitArgument(old.getExpression(), argument)) {
          List<ConcreteArgument> arguments = new ArrayList<>(app.getArguments());
          arguments.set(i, factory.arg(variant, old.isExplicit()));
          result.add(factory.app(app.getFunction(), arguments));
        }
      }
    }
    return result;
  }

  /**
   * Evidence is a proof of {@code x /= 0}, of {@code Inv x}, or of {@code x # 0}. Each shape is only a way of
   * elaborating the same {@code nonZero-Inv} application, whose typechecking decides whether the evidence fits.
   */
  private @Nullable CoreExpression tryEvidence(ConcreteExpression evidence, CRingInstance instance, CoreExpression source) {
    CoreExpression result = tryNonZeroInverse(evidence, instance, source);
    if (result != null) return result;
    result = tryNonZeroInverse(factory.app(factory.ref(meta.invNonZero), true, evidence), instance, source);
    if (result != null) return result;
    return tryNonZeroInverse(factory.appBuilder(factory.ref(meta.apartNotEqual)).app(factory.core(instance.original().expression()), false).app(evidence).build(), instance, source);
  }

  private @Nullable CoreExpression tryNonZeroInverse(ConcreteExpression nonZero, CRingInstance instance, CoreExpression source) {
    TypedExpression result = Utils.tryTypecheck(typechecker, tc -> {
      TypedExpression checked = tc.typecheck(factory.appBuilder(factory.ref(meta.nonZeroInv)).app(factory.core(instance.original().expression()), false).app(Utils.concrete(factory, source), false).app(nonZero, true).build(), null);
      // A mismatch may leave an error in the result instead of failing. Evidence with unsolved inference variables
      // would fit any denominator.
      if (checked != null && (isIncomplete(checked.getExpression()) || isIncomplete(checked.getType()))) {
        tc.loadSavedState();
        return null;
      }
      return checked;
    });
    return result == null ? null : result.getExpression();
  }

  private static boolean isIncomplete(CoreExpression expression) {
    boolean[] found = { false };
    expression.processSubexpression(subexpression -> {
      if (subexpression instanceof CoreErrorExpression || subexpression instanceof CoreInferenceReferenceExpression inference && inference.getSubstExpression() == null) {
        found[0] = true;
        return CoreExpression.FindAction.STOP;
      }
      return CoreExpression.FindAction.CONTINUE;
    });
    return found[0];
  }

}
