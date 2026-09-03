package org.arend.lib.meta.field;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteAppExpression;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.expr.CoreAppExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreDefCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFieldCallExpression;
import org.arend.ext.core.expr.CoreInferenceReferenceExpression;
import org.arend.ext.core.expr.CoreIntegerExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.NumberTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Reifies one complete field expression and constructs its {@code Eval}
 * certificate at the same time.  This is deliberately a non-local rule:
 * inverses affect the denominator of the whole expression, so applying it to
 * isolated subexpressions would not implement denominator clearing.
 *
 * <p>An {@code inv} projection already carries its {@code Monoid.Inv}
 * witness.  A total {@code DiscreteField.finv x} is accepted only if one of
 * the user-supplied templates or local bindings can be elaborated as evidence
 * that this particular {@code x} is nonzero.  Thus total inverses are never
 * cleared unconditionally.</p>
 */
public final class FieldSimpRule {
  enum Kind { VAR, ZRO, IDE, ADD, MUL, NEGATIVE, COEF, INVERSE }

  /** A reflected FieldTerm together with the data needed for its Eval proof. */
  public record Result(
      @NotNull EquationTerm term,
      @NotNull Kind kind,
      @NotNull List<Result> arguments,
      @Nullable CoreExpression inverseWitness,
      @NotNull CoreExpression value) {
  }

  private final FieldSimpMeta meta;
  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;
  private final TypedExpression ringInstance;
  private final Values<CoreExpression> values;
  private final List<TermOperation> operations;
  private final TermOperation inverseOperation;
  private final @Nullable FunctionMatcher finvMatcher;
  private final List<? extends ConcreteExpression> evidenceTemplates;
  private final List<CoreBinding> localBindings;

  FieldSimpRule(
      @NotNull FieldSimpMeta meta,
      @NotNull ExpressionTypechecker typechecker,
      @NotNull ConcreteFactory factory,
      @NotNull TypedExpression ringInstance,
      @NotNull Values<CoreExpression> values,
      @NotNull List<TermOperation> ringOperations,
      @Nullable FunctionMatcher finvMatcher,
      @NotNull List<? extends ConcreteExpression> evidenceTemplates,
      @NotNull List<CoreBinding> localBindings) {
    this.meta = meta;
    this.typechecker = typechecker;
    this.factory = factory;
    this.ringInstance = ringInstance;
    this.values = values;
    this.operations = new ArrayList<>();
    this.finvMatcher = finvMatcher;
    this.evidenceTemplates = evidenceTemplates;
    this.localBindings = localBindings;

    for (TermOperation operation : ringOperations) {
      Kind kind = meta.kindOfRingOperation(operation.data());
      if (kind != null) {
        operations.add(meta.toFieldOperation(kind, operation));
      }
    }
    inverseOperation = meta.inverseOperation();
  }

  public @NotNull Result reify(@NotNull CoreExpression expression) {
    // A class-field application can reduce to the concrete implementation in
    // WHNF (notably RatField.finv).  Inspect the unreduced syntax first so the
    // captured DiscreteField instance is not lost.
    Result inverseResult = reifyInverse(expression);
    if (inverseResult != null) {
      return inverseResult;
    }

    CoreExpression normalized = expression.normalize(NormalizationMode.WHNF);
    if (normalized != expression) {
      inverseResult = reifyInverse(normalized);
      if (inverseResult != null) {
        return inverseResult;
      }
    }

    for (TermOperation operation : operations) {
      List<CoreExpression> arguments = operation.matcher().match(normalized);
      if (arguments == null) {
        continue;
      }

      Kind kind = (Kind) operation.data();
      switch (kind) {
        case ZRO, IDE -> {
          if (arguments.isEmpty()) {
            return new Result(new OpTerm(operation, List.of()), kind, List.of(), null, normalized);
          }
        }
        case COEF -> {
          if (arguments.size() == 1 &&
              arguments.getFirst().normalize(NormalizationMode.WHNF) instanceof CoreIntegerExpression integer) {
            return new Result(
                new OpTerm(operation, List.of(new NumberTerm(integer.getBigInteger()))),
                kind,
                List.of(),
                null,
                normalized);
          }
        }
        case NEGATIVE -> {
          if (arguments.size() == 1) {
            Result argument = reify(arguments.getFirst());
            return new Result(
                new OpTerm(operation, List.of(argument.term())),
                kind,
                List.of(argument),
                null,
                normalized);
          }
        }
        case ADD, MUL -> {
          if (arguments.size() == 2) {
            Result left = reify(arguments.get(0));
            Result right = reify(arguments.get(1));
            return new Result(
                new OpTerm(operation, List.of(left.term(), right.term())),
                kind,
                List.of(left, right),
                null,
                normalized);
          }
        }
        case INVERSE -> throw new IllegalStateException("Inverse is matched separately");
      }
    }

    int index = values.addValue(normalized);
    return new Result(new VarTerm(index), Kind.VAR, List.of(), null, normalized);
  }

  /** Matches a proof-bearing inverse, or a {@code finv} justified by evidence. */
  private @Nullable Result reifyInverse(@NotNull CoreExpression expression) {
    if (expression instanceof CoreFieldCallExpression fieldCall &&
        fieldCall.getDefinition() == meta.invProjection()) {
      return reifyProofBearingInverse(expression, fieldCall);
    }

    List<CoreExpression> arguments = finvMatcher == null ? null : finvMatcher.match(expression);
    if (arguments == null || arguments.size() != 1) {
      return null;
    }

    CoreExpression discreteFieldInstance = ringInstance.getExpression();
    CoreExpression source = preferSyntacticArgument(expression, arguments.getFirst());
    if (!isSelectedFinv(expression, source)) {
      return null;
    }
    CoreExpression witness = findNonZeroInverse(discreteFieldInstance, source);
    if (witness == null) {
      return null;
    }

    Result argument = reify(source);
    return new Result(
        new OpTerm(inverseOperation, List.of(argument.term())),
        Kind.INVERSE,
        List.of(argument),
        witness,
        expression);
  }

  /** Prevents a generic field matcher from accepting another instance's finv. */
  private boolean isSelectedFinv(
      @NotNull CoreExpression expression,
      @NotNull CoreExpression source) {
    TypedExpression selectedFinv = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(
        factory.appBuilder(factory.ref(meta.finv().getRef()))
            .app(factory.core(ringInstance), false)
            .app(core(source))
            .build(),
        null));
    return selectedFinv != null && Utils.safeCompare(
        typechecker,
        selectedFinv.getExpression(),
        expression,
        CMP.EQ,
        null,
        false,
        true,
        true);
  }

  /**
   * ExpressionFunctionMatcher may solve its argument metavariable with a WHNF
   * of the actual argument.  Keep an unreduced direct argument when possible;
   * reusable evidence such as {@code fac/=0} relies on seeing
   * {@code fac (suc n)}, not its reduct {@code suc n * fac n}.
   */
  private @NotNull CoreExpression preferSyntacticArgument(
      @NotNull CoreExpression expression,
      @NotNull CoreExpression matchedArgument) {
    CoreExpression underlying = expression.getUnderlyingExpression();
    if (underlying instanceof CoreAppExpression app &&
        Utils.safeCompare(typechecker, app.getArgument(), matchedArgument, CMP.EQ, null, false, true, true)) {
      return app.getArgument();
    }
    if (underlying instanceof CoreDefCallExpression defCall) {
      List<? extends CoreExpression> arguments = defCall.getDefCallArguments();
      for (int i = arguments.size() - 1; i >= 0; i--) {
        CoreExpression candidate = arguments.get(i);
        if (Utils.safeCompare(typechecker, candidate, matchedArgument, CMP.EQ, null, false, true, true)) {
          return candidate;
        }
      }
    }
    return matchedArgument;
  }

  private @Nullable Result reifyProofBearingInverse(
      @NotNull CoreExpression expression,
      @NotNull CoreFieldCallExpression fieldCall) {

    CoreExpression witness = fieldCall.getArgument();
    CoreExpression witnessType = witness.computeType().normalize(NormalizationMode.WHNF);
    if (!(witnessType instanceof CoreClassCallExpression classCall) ||
        !classCall.getDefinition().isSubClassOf(meta.invClass())) {
      return null;
    }

    TypedExpression source = typechecker.typecheck(
        factory.app(
            factory.ref(meta.invValue().getRef()),
            false,
            factory.core(witness.computeTyped())),
        null);
    if (source == null) {
      return null;
    }

    Result argument = reify(source.getExpression());
    return new Result(
        new OpTerm(inverseOperation, List.of(argument.term())),
        Kind.INVERSE,
        List.of(argument),
        witness,
        expression);
  }

  /**
   * Tries every candidate in an isolated typechecker state.  Supplying the
   * captured DiscreteField instance and source explicitly makes the check
   * instance-aware and gives each reusable template fresh inference
   * variables for this occurrence of {@code finv}.
   */
  private @Nullable CoreExpression findNonZeroInverse(
      @NotNull CoreExpression discreteFieldInstance,
      @NotNull CoreExpression source) {
    for (ConcreteExpression template : evidenceTemplates) {
      CoreExpression witness = tryNonZeroInverse(template, discreteFieldInstance, source);
      if (witness != null) {
        return witness;
      }
      witness = tryInstantiatedTemplate(template, discreteFieldInstance, source);
      if (witness != null) {
        return witness;
      }
    }
    for (CoreBinding binding : localBindings) {
      CoreExpression witness = tryNonZeroInverse(factory.ref(binding), discreteFieldInstance, source);
      if (witness != null) {
        return witness;
      }
    }
    return null;
  }

  /**
   * If reduction erased a useful pattern from the denominator, instantiate
   * implicit arguments occurring inside a template with subterms of that
   * denominator.  For example, {@code fac (suc n)} reduces to
   * {@code suc n * fac n}; this lets the inner {@code fac/=0} in
   * {@code natRat/=0 fac/=0} be retried explicitly at {@code suc n}.
   */
  private @Nullable CoreExpression tryInstantiatedTemplate(
      @NotNull ConcreteExpression template,
      @NotNull CoreExpression discreteFieldInstance,
      @NotNull CoreExpression source) {
    List<CoreExpression> candidates = new ArrayList<>();
    Set<CoreExpression> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    source.processSubexpression(subexpression -> {
      if (candidates.size() < 32 && seen.add(subexpression)) {
        candidates.add(subexpression);
      }
      return CoreExpression.FindAction.CONTINUE;
    });

    for (CoreExpression candidate : candidates) {
      ConcreteExpression concreteCandidate = core(candidate);
      for (ConcreteExpression instantiated : addImplicitArgumentVariants(template, concreteCandidate)) {
        CoreExpression witness = tryNonZeroInverse(instantiated, discreteFieldInstance, source);
        if (witness != null) {
          return witness;
        }
      }
    }
    return null;
  }

  /** Returns variants in which exactly one application node gains an implicit argument. */
  private @NotNull List<ConcreteExpression> addImplicitArgumentVariants(
      @NotNull ConcreteExpression expression,
      @NotNull ConcreteExpression implicitArgument) {
    List<ConcreteExpression> result = new ArrayList<>();
    result.add(factory.app(expression, false, implicitArgument));

    if (expression instanceof ConcreteAppExpression app) {
      for (ConcreteExpression functionVariant :
          addImplicitArgumentVariants(app.getFunction(), implicitArgument)) {
        result.add(factory.app(functionVariant, app.getArguments()));
      }
      for (int i = 0; i < app.getArguments().size(); i++) {
        ConcreteArgument oldArgument = app.getArguments().get(i);
        for (ConcreteExpression argumentVariant :
            addImplicitArgumentVariants(oldArgument.getExpression(), implicitArgument)) {
          List<ConcreteArgument> arguments = new ArrayList<>(app.getArguments());
          arguments.set(i, factory.arg(argumentVariant, oldArgument.isExplicit()));
          result.add(factory.app(app.getFunction(), arguments));
        }
      }
    }
    return result;
  }

  private @Nullable CoreExpression tryNonZeroInverse(
      @NotNull ConcreteExpression evidence,
      @NotNull CoreExpression discreteFieldInstance,
      @NotNull CoreExpression source) {
    TypedExpression typed = Utils.tryTypecheck(typechecker, tc -> {
      TypedExpression result = tc.typecheck(
          factory.appBuilder(factory.ref(meta.nonZeroInv()))
              .app(core(discreteFieldInstance), false)
              .app(core(source), false)
              .app(evidence, true)
              .build(),
          null);
      if (result != null && hasUnsolvedInference(result)) {
        tc.loadSavedState();
        return null;
      }
      return result;
    });
    return typed == null ? null : typed.getExpression();
  }

  private boolean hasUnsolvedInference(@NotNull TypedExpression expression) {
    boolean[] found = { false };
    expression.getExpression().processSubexpression(subexpression -> {
      if (subexpression instanceof CoreInferenceReferenceExpression inference &&
          inference.getSubstExpression() == null) {
        found[0] = true;
        return CoreExpression.FindAction.STOP;
      }
      return CoreExpression.FindAction.CONTINUE;
    });
    if (!found[0]) {
      expression.getType().processSubexpression(subexpression -> {
        if (subexpression instanceof CoreInferenceReferenceExpression inference &&
            inference.getSubstExpression() == null) {
          found[0] = true;
          return CoreExpression.FindAction.STOP;
        }
        return CoreExpression.FindAction.CONTINUE;
      });
    }
    return found[0];
  }

  public @NotNull ConcreteExpression reflectedTerm(@NotNull Result result) {
    return result.term().generateReflectedTerm(factory, meta.fieldVar());
  }

  /**
   * Builds a typed Eval certificate using the public backend constructors.
   * Each helper receives the already selected ring instance, so the universe
   * level is inherited from that typed expression instead of being recreated
   * or constrained by Java.
   */
  public @NotNull ConcreteExpression evaluation(
      @NotNull Result result,
      @NotNull ConcreteExpression env,
      int variableCount) {
    ConcreteAppBuilder builder = factory.appBuilder(factory.ref(switch (result.kind()) {
      case VAR -> meta.evalVar();
      case COEF -> meta.evalCoef();
      case ZRO -> meta.evalZro();
      case IDE -> meta.evalIde();
      case NEGATIVE -> meta.evalNegative();
      case ADD -> meta.evalAdd();
      case MUL -> meta.evalMul();
      case INVERSE -> meta.evalInverse();
    }))
        .app(factory.core(ringInstance), false)
        .app(factory.number(variableCount), false)
        .app(env, false);

    switch (result.kind()) {
      case VAR -> builder.app(factory.number(((VarTerm) result.term()).index()), false);
      case COEF -> {
        NumberTerm coefficient = (NumberTerm) ((OpTerm) result.term()).arguments().getFirst();
        builder.app(factory.number(coefficient.number()), false);
      }
      case ZRO, IDE -> {
      }
      case NEGATIVE -> {
        Result argument = result.arguments().getFirst();
        builder
            .app(reflectedTerm(argument), false)
            .app(core(argument.value()), false)
            .app(evaluation(argument, env, variableCount));
      }
      case ADD, MUL -> {
        Result left = result.arguments().get(0);
        Result right = result.arguments().get(1);
        builder
            .app(reflectedTerm(left), false)
            .app(reflectedTerm(right), false)
            .app(core(left.value()), false)
            .app(core(right.value()), false)
            .app(evaluation(left, env, variableCount))
            .app(evaluation(right, env, variableCount));
      }
      case INVERSE -> {
        Result argument = result.arguments().getFirst();
        assert result.inverseWitness() != null;
        builder
            .app(reflectedTerm(argument), false)
            .app(core(argument.value()), false)
            .app(evaluation(argument, env, variableCount))
            .app(core(result.inverseWitness()));
      }
    }
    return builder.build();
  }

  private @NotNull ConcreteExpression core(@NotNull CoreExpression expression) {
    return factory.core(expression.computeTyped());
  }
}
