package org.arend.lib.meta.positivity;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreInferenceVariable;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

/**
 * The search procedure of {@code positivity}.
 * <p>
 * A goal {@code 0 < t}, {@code 0 <= t}, or {@code t /= 0} is proved by (in this order)
 * hypotheses from the context, numerals, rule lemmas whose conclusion unifies with the goal (their premises are proved recursively),
 * and, for {@code 0 <= t} and {@code t /= 0}, by weakening a proof of {@code 0 < t}.
 * Every attempt is made in a saved typechecker state which is restored if the attempt fails.
 */
public class Positivity extends OrderSearch {
  private static final int MAX_DEPTH = 32;
  private static final int MAX_PARAMETERS = 16;

  private final List<PositivityRule> rules;
  private final List<Hypothesis> hypotheses = new ArrayList<>();
  private final List<MemoEntry> memo = new ArrayList<>();
  private final List<CoreExpression> inProgress = new ArrayList<>();

  public record Goal(Strictness strictness, CoreExpression type, CoreExpression term, CoreExpression zero, @Nullable CoreExpression instance, boolean linear) {}

  private record Hypothesis(CoreBinding binding, Relation relation, boolean zero) {}

  private record MemoEntry(CoreExpression type, @Nullable TypedExpression proof) {}

  public Positivity(ExpressionTypechecker typechecker, ConcreteExpression marker, PositivityMeta meta, List<PositivityRule> rules, List<CoreBinding> bindings) {
    super(typechecker, marker, meta, bindings);
    this.rules = rules;
    for (CoreBinding binding : bindings) {
      Relation relation = parseRelation(binding.getType());
      if (relation != null) {
        hypotheses.add(new Hypothesis(binding, relation, isZero(relation.other(), relation.instance())));
      }
    }
  }

  public @Nullable Goal makeGoal(CoreExpression type) {
    Relation relation = parseRelation(type);
    return relation == null || !isZero(relation.other(), relation.instance()) ? null : new Goal(relation.kind(), type, relation.term(), relation.other(), relation.instance(), relation.linear());
  }

  /**
   * Checks if {@code expr} is zero: syntactically, or definitionally equal to {@code zro} of {@code instance}.
   * The latter covers elaborations such as {@code fromRat (fromInt (pos 0))} of {@code (0 : Real)}.
   */
  private boolean isZero(CoreExpression expr, @Nullable CoreExpression instance) {
    if (isZeroSyntactically(expr)) return true;
    if (instance == null) return false;
    return Boolean.TRUE.equals(Utils.tryTypecheck(typechecker, tc -> {
      TypedExpression zero = tc.typecheck(factory.app(factory.ref(meta.zro.getRef()), factory.arg(core(instance), false)), null);
      boolean ok = zero != null && tc.compare(expr, zero.getExpression(), CMP.EQ, marker, false, true, false);
      tc.loadSavedState();
      return ok ? true : null;
    }));
  }

  private boolean isZeroSyntactically(CoreExpression expr) {
    for (int i = 0; i < 2; i++) {
      expr = Relation.deref(expr);
      switch (expr) {
        case CoreIntegerExpression intExpr -> {
          return intExpr.getBigInteger().equals(BigInteger.ZERO);
        }
        case CoreFieldCallExpression fieldCall when fieldCall.getDefinition() == meta.zro -> {
          return true;
        }
        case CoreConCallExpression conCall when conCall.getDefinition().getRef().equals(meta.posRef) && conCall.getDefCallArguments().size() == 1 -> {
          expr = conCall.getDefCallArguments().getFirst();
          continue;
        }
        default -> {}
      }
      CoreExpression normExpr = expr.normalize(NormalizationMode.WHNF);
      if (normExpr == expr) return false;
      expr = normExpr;
    }
    return false;
  }

  @Override
  protected int mark() {
    return memo.size();
  }

  /**
   * Proofs found during a failed attempt may refer to its inference variables; failures remain valid.
   */
  @Override
  protected void rollback(int memoSize) {
    for (int i = memo.size() - 1; i >= memoSize; i--) {
      if (memo.get(i).proof != null) memo.remove(i);
    }
  }

  /**
   * Constructs the goal {@code 0 < term}, {@code 0 <= term}, or {@code term /= 0}.
   *
   * @param zeroExpr  zero of the type of {@code term}
   * @param instance  an order instance on the type of {@code term} or {@code null} if it should be found
   */
  private @Nullable Goal makeGoal(Strictness strictness, CoreExpression term, CoreExpression zeroExpr, @Nullable CoreExpression instance, boolean linear) {
    TypedExpression zero = zeroExpr.computeTyped();
    if (strictness == Strictness.NONZERO) {
      ConcreteExpression expr = factory.app(factory.ref(meta.notEquals), true, core(term), factory.core(zero));
      TypedExpression result = Utils.tryTypecheck(typechecker, tc -> check(tc, expr, null));
      return result == null ? null : new Goal(strictness, result.getExpression(), term, zeroExpr, null, false);
    }

    CoreClassDefinition instanceClass = strictness == Strictness.POSITIVE ? meta.StrictPoset : meta.Preorder;
    ConcreteExpression instanceExpr = instanceOf(instanceClass, term, instance);
    if (instanceExpr == null) return null;
    boolean isLinear = linear && strictness == Strictness.NONNEGATIVE;
    ConcreteExpression rel = isLinear
      ? factory.ref(meta.linearLessOrEquals)
      : factory.ref((strictness == Strictness.POSITIVE ? meta.less : meta.lessOrEquals).getRef());
    // The goal is not parsed from the result since the typechecker may unfold the relation of a concrete instance
    return Utils.tryTypecheck(typechecker, tc -> {
      TypedExpression typedInstance = check(tc, instanceExpr, null);
      if (typedInstance == null) return null;
      TypedExpression result = check(tc, factory.app(rel, factory.arg(factory.core(typedInstance), false), factory.arg(factory.core(zero), true), factory.arg(core(term), true)), null);
      return result == null ? null : new Goal(strictness, result.getExpression(), term, zeroExpr, typedInstance.getExpression(), isLinear);
    });
  }

  public @Nullable TypedExpression prove(Goal goal) {
    boolean ground = isGround(goal.type);
    if (ground) {
      for (MemoEntry entry : memo) {
        if (compareTerms(entry.type, goal.type)) {
          return entry.proof == null ? null : attempt(tc -> check(tc, factory.core(entry.proof), goal.type));
        }
      }
    }
    if (inProgress.size() >= MAX_DEPTH) return null;
    for (CoreExpression type : inProgress) {
      if (compareTerms(type, goal.type)) return null;
    }

    typechecker.checkCancelled();
    inProgress.add(goal.type);
    TypedExpression result;
    try {
      result = proveUncached(goal);
    } finally {
      inProgress.removeLast();
    }
    if (ground) memo.add(new MemoEntry(goal.type, result));
    return result;
  }

  private @Nullable TypedExpression proveUncached(Goal goal) {
    TypedExpression result = proveByHypothesis(goal);
    if (result != null) return result;

    result = proveNumber(goal);
    if (result != null) return result;

    Object head = Relation.head(goal.term);
    for (PositivityRule rule : rules) {
      if (rule.strictness() == goal.strictness && matchesHead(rule.head(), head, goal.term, goal.instance)) {
        result = applyRule(rule, goal);
        if (result != null) return result;
      }
    }

    if (goal.strictness != Strictness.POSITIVE) {
      return proveByWeakening(goal);
    }
    return null;
  }

  private @Nullable TypedExpression proveByHypothesis(Goal goal) {
    for (Hypothesis hypothesis : hypotheses) {
      if (hypothesis.zero && hypothesis.relation.kind() == goal.strictness && compareTerms(hypothesis.relation.term(), goal.term)) {
        TypedExpression result = attempt(tc -> check(tc, factory.ref(hypothesis.binding), goal.type));
        if (result != null) return result;
      }
    }

    // c < t or c <= t
    if (goal.strictness == Strictness.NONZERO) return null;
    for (Hypothesis hypothesis : hypotheses) {
      Relation relation = hypothesis.relation;
      if (hypothesis.zero || relation.kind() == Strictness.NONZERO || !compareTerms(relation.right(), goal.term)) {
        continue;
      }

      ArendRef lemma;
      CoreClassDefinition lemmaClass;
      Strictness subStrictness;
      if (goal.strictness == Strictness.POSITIVE) {
        lemma = relation.kind() == Strictness.POSITIVE ? meta.lessTransRight : meta.lessTransLeft;
        lemmaClass = meta.BiorderedSet;
        subStrictness = relation.kind() == Strictness.POSITIVE ? Strictness.NONNEGATIVE : Strictness.POSITIVE;
      } else {
        if (relation.kind() == Strictness.POSITIVE) continue; // covered by weakening of 0 < t
        lemma = meta.leTrans;
        lemmaClass = meta.Preorder;
        subStrictness = Strictness.NONNEGATIVE;
      }

      TypedExpression result = attempt(tc -> {
        Goal subgoal = makeGoal(subStrictness, relation.left(), goal.zero, relation.instance(), relation.linear());
        if (subgoal == null) return null;
        TypedExpression proof = prove(subgoal);
        if (proof == null) return null;
        return check(tc, appWithInstance(lemma, lemmaClass, relation.instance(), factory.core(proof), factory.ref(hypothesis.binding)), goal.type);
      });
      if (result != null) return result;
    }
    return null;
  }

  /**
   * Proves {@code 0 < n} for a positive numeral {@code n} of type Nat,
   * and {@code 0 < q} or {@code 0 <= q} for a rational constant {@code q} such as {@code ratio 1 2}.
   */
  private @Nullable TypedExpression proveNumber(Goal goal) {
    if (goal.strictness == Strictness.NONZERO) return null;
    CoreExpression term = goal.term.normalize(NormalizationMode.WHNF);
    if (term instanceof CoreConCallExpression conCall && conCall.getDefinition().getRef().equals(meta.ratCon) && conCall.getDefCallArguments().size() >= 2) {
      BigInteger nom = getInt(conCall.getDefCallArguments().get(0));
      if (nom == null || getInt(conCall.getDefCallArguments().get(1)) == null) return null;
      int sign = nom.signum();
      if (goal.strictness == Strictness.POSITIVE ? sign <= 0 : sign < 0) return null;
      ArendRef lemma = goal.strictness == Strictness.POSITIVE ? meta.ratLessDec : meta.ratLessOrEqualsDec;
      // Pass both sides explicitly so that the implicit {so} argument is solved immediately rather than after unification with the goal
      return attempt(tc -> check(tc, factory.app(factory.ref(lemma), factory.arg(core(goal.zero), false), factory.arg(core(term), false)), goal.type));
    }
    if (goal.strictness != Strictness.POSITIVE) return null;
    if (term instanceof CoreIntegerExpression intExpr && intExpr.getBigInteger().signum() > 0 || term instanceof CoreConCallExpression conCall && conCall.getDefinition() == typechecker.getPrelude().getSuc()) {
      return attempt(tc -> check(tc, factory.ref(meta.zeroLessSuc), goal.type));
    }
    return null;
  }

  /**
   * Returns the value of an integer literal of type Nat or Int, or {@code null} if {@code expr} is not a literal.
   */
  private @Nullable BigInteger getInt(CoreExpression expr) {
    expr = expr.normalize(NormalizationMode.WHNF);
    boolean negative = false;
    if (expr instanceof CoreConCallExpression conCall && (conCall.getDefinition() == typechecker.getPrelude().getPos() || conCall.getDefinition() == typechecker.getPrelude().getNeg())) {
      negative = conCall.getDefinition() == typechecker.getPrelude().getNeg();
      expr = conCall.getDefCallArguments().getFirst().normalize(NormalizationMode.WHNF);
    }
    if (!(expr instanceof CoreIntegerExpression intExpr)) return null;
    return negative ? intExpr.getBigInteger().negate() : intExpr.getBigInteger();
  }

  private @Nullable TypedExpression applyRule(PositivityRule rule, Goal goal) {
    return attempt(tc -> {
      ConcreteExpression fun = factory.ref(rule.definition().getRef());
      if (rule.instanceClass() != null) {
        ConcreteExpression instance = instanceOf(rule.instanceClass(), goal.term, goal.instance);
        if (instance == null) return null;
        fun = factory.app(fun, factory.arg(instance, false));
      }

      // Instantiate parameters: implicit ones with holes and explicit ones (premises) with inference variables
      Instance inst = instantiate(tc, fun, MAX_PARAMETERS, (i, parameter, type) -> parameter.isExplicit() ? ArgumentMode.PREMISE : ArgumentMode.HOLE);
      if (inst == null) return null;
      TypedExpression typed = inst.typed();
      if (!tc.compare(typed.getType(), goal.type, CMP.LE, marker, true, true, false)) return null;

      for (CoreInferenceReferenceExpression premise : inst.premises()) {
        CoreInferenceVariable variable = premise.getVariable();
        if (variable == null) return null;
        TypedExpression proof = provePremise(premise.computeType());
        if (proof == null || !tc.solveInferenceVariable(variable, proof.getExpression())) return null;
      }
      return check(tc, factory.core(typed), goal.type);
    });
  }

  private @Nullable TypedExpression provePremise(CoreExpression type) {
    // The subgoal is rebuilt from the instantiated term, so that it does not refer to inference variables of the rule
    Relation relation = parseRelation(type);
    Goal subgoal = relation == null || !isZero(relation.other(), relation.instance()) ? null : makeGoal(relation.kind(), Relation.deref(relation.term()), Relation.deref(relation.other()), relation.instance() == null ? null : Relation.deref(relation.instance()), relation.linear());
    if (subgoal != null) {
      TypedExpression proof = prove(subgoal);
      if (proof != null) return proof;
    }

    // A side condition can be proved by a hypothesis
    for (CoreBinding binding : bindings) {
      TypedExpression proof = attempt(tc -> check(tc, factory.ref(binding), type));
      if (proof != null) return proof;
    }
    return null;
  }

  private @Nullable TypedExpression proveByWeakening(Goal goal) {
    Goal posGoal = makeGoal(Strictness.POSITIVE, goal.term, goal.zero, goal.instance, false);
    if (posGoal == null) return null;
    TypedExpression proof = prove(posGoal);
    if (proof == null) return null;

    List<ConcreteExpression> candidates = new ArrayList<>(2);
    if (goal.strictness == Strictness.NONZERO) {
      candidates.add(appWithInstance(meta.lessToNotEquals, meta.StrictPoset, posGoal.instance, factory.core(proof)));
    } else {
      candidates.add(appWithInstance(meta.lessToLessOrEquals, meta.BiorderedSet, posGoal.instance, factory.core(proof)));
      candidates.add(factory.app(factory.ref(meta.linearLessToLessOrEquals), true, factory.core(proof)));
    }

    for (ConcreteExpression candidate : candidates) {
      TypedExpression result = attempt(tc -> check(tc, candidate, goal.type));
      if (result != null) return result;
    }
    return null;
  }

  public static List<PositivityRule> getRules(PositivityMeta meta, ExpressionTypechecker typechecker, @Nullable List<? extends CoreDefinition> attributed) {
    List<PositivityRule> result = new ArrayList<>(meta.getBuiltinRules(typechecker));
    if (attributed != null) {
      for (CoreDefinition definition : attributed) {
        PositivityRule rule = meta.getRule(definition);
        if (rule != null) result.add(rule);
      }
    }
    return result;
  }
}
