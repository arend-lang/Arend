package org.arend.lib.meta.gcongr;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreInferenceVariable;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreInferenceReferenceExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.positivity.OrderSearch;
import org.arend.lib.meta.positivity.Positivity;
import org.arend.lib.meta.positivity.Relation;
import org.arend.lib.meta.positivity.Strictness;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The search procedure of {@code gcongr}.
 * <p>
 * A goal {@code l R r}, where {@code R} is {@code <} or {@code <=}, is proved (in this order)
 * by a hypothesis (a proof of {@code l < r} is weakened to {@code l <= r}), by reflexivity if {@code l} and {@code r} coincide,
 * and by a monotonicity rule whose conclusion unifies with the goal.
 * Main premises of the rule are proved recursively and side conditions are proved by {@code positivity} or by hypotheses.
 * <p>
 * Rules are first tried in the strict mode, in which every subgoal must be proved.
 * If no rule succeeds, then the first rule that applies is used and the subgoals that cannot be proved become <i>leaves</i>,
 * which are proved by the argument of {@code gcongr}.
 */
public class Gcongr extends OrderSearch {
  private static final int MAX_DEPTH = 32;

  private final List<GcongrRule> rules;
  private final Positivity positivity;
  private final List<Hypothesis> hypotheses = new ArrayList<>();
  private final List<Leaf> leaves = new ArrayList<>();
  private final List<CoreExpression> failures = new ArrayList<>();
  private int depth;

  public record Goal(Strictness strictness, CoreExpression type, Relation relation) {
    CoreExpression left() {
      return relation.left();
    }

    CoreExpression right() {
      return relation.right();
    }
  }

  /**
   * A subgoal that is not proved by {@code gcongr}.
   */
  public record Leaf(CoreInferenceVariable variable, CoreExpression type) {}

  private record Hypothesis(CoreBinding binding, Relation relation) {}

  public Gcongr(ExpressionTypechecker typechecker, ConcreteExpression marker, GcongrMeta meta, List<GcongrRule> rules, Positivity positivity, List<CoreBinding> bindings) {
    super(typechecker, marker, meta, bindings);
    this.rules = rules;
    this.positivity = positivity;
    for (CoreBinding binding : bindings) {
      Relation relation = parseRelation(binding.getType());
      if (relation != null && relation.kind() != Strictness.NONZERO) {
        hypotheses.add(new Hypothesis(binding, relation));
      }
    }
  }

  public List<Leaf> getLeaves() {
    return leaves;
  }

  public @Nullable Goal makeGoal(CoreExpression type) {
    Relation relation = parseRelation(type);
    return relation == null || relation.kind() == Strictness.NONZERO ? null : new Goal(relation.kind(), type, relation);
  }

  @Override
  protected int mark() {
    return leaves.size();
  }

  @Override
  protected void rollback(int leavesSize) {
    leaves.subList(leavesSize, leaves.size()).clear();
  }

  /**
   * Proves the goal; subgoals that cannot be proved are added to {@link #getLeaves()}.
   *
   * @return a proof or {@code null} if the goal is not closed by a hypothesis and no rule applies.
   */
  public @Nullable TypedExpression prove(Goal goal) {
    TypedExpression result = proveClosed(goal);
    if (result != null) return result;
    result = proveByRules(goal, false);
    return result != null ? result : proveByRules(goal, true);
  }

  /**
   * Proves a main goal. In the lenient mode, a goal that cannot be proved becomes a leaf.
   */
  private @Nullable TypedExpression proveMain(Goal goal, boolean lenient) {
    TypedExpression result = proveClosed(goal);
    if (result != null) return result;
    result = proveByRules(goal, false);
    if (result != null || !lenient) return result;
    result = proveByRules(goal, true);
    return result != null ? result : makeLeaf(goal.type);
  }

  private TypedExpression makeLeaf(CoreExpression type) {
    CoreInferenceReferenceExpression leaf = typechecker.generateNewInferenceVariable("goal", type, marker, false);
    leaves.add(new Leaf(leaf.getVariable(), type));
    return leaf.computeTyped();
  }

  /**
   * Proves a goal by a hypothesis or by reflexivity.
   */
  private @Nullable TypedExpression proveClosed(Goal goal) {
    for (Hypothesis hypothesis : hypotheses) {
      Relation relation = hypothesis.relation;
      if (relation.kind() == Strictness.POSITIVE && goal.strictness == Strictness.NONNEGATIVE || relation.kind() == goal.strictness) {
        if (!compareTerms(relation.left(), goal.left()) || !compareTerms(relation.right(), goal.right())) continue;
        List<ConcreteExpression> candidates = new ArrayList<>(2);
        ConcreteExpression ref = factory.ref(hypothesis.binding);
        if (relation.kind() == goal.strictness) {
          candidates.add(ref);
        } else {
          candidates.add(appWithInstance(meta.lessToLessOrEquals, meta.BiorderedSet, relation.instance(), ref));
          candidates.add(factory.app(factory.ref(meta.linearLessToLessOrEquals), true, ref));
        }
        TypedExpression result = tryCandidates(candidates, goal.type);
        if (result != null) return result;
      }
    }

    if (goal.strictness == Strictness.NONNEGATIVE && compareTerms(goal.left(), goal.right())) {
      return tryCandidates(List.of(factory.ref(meta.leRefl), appWithInstance(meta.leRefl, meta.Preorder, goal.relation.instance())), goal.type);
    }
    return null;
  }

  private @Nullable TypedExpression tryCandidates(List<ConcreteExpression> candidates, CoreExpression type) {
    for (ConcreteExpression candidate : candidates) {
      TypedExpression result = attempt(tc -> check(tc, candidate, type));
      if (result != null) return result;
    }
    return null;
  }

  private @Nullable TypedExpression proveByRules(Goal goal, boolean lenient) {
    if (depth >= MAX_DEPTH) return null;
    GcongrRule.Spine left = GcongrRule.Spine.make(goal.left()), right = GcongrRule.Spine.make(goal.right());
    if (left == null || right == null) return null;

    boolean ground = !lenient && isGround(goal.type);
    if (ground) {
      for (CoreExpression failure : failures) {
        if (compareTerms(failure, goal.type)) return null;
      }
    }

    typechecker.checkCancelled();
    depth++;
    try {
      for (GcongrRule rule : rules) {
        if (rule.strictness() == goal.strictness && matchesHead(rule.head(), left.head(), goal.left(), goal.relation.instance()) && matchesHead(rule.head(), right.head(), goal.right(), goal.relation.instance())) {
          TypedExpression result = applyRule(rule, goal, lenient);
          if (result != null) return result;
        }
      }
    } finally {
      depth--;
    }

    if (ground) failures.add(goal.type);
    return null;
  }

  private @Nullable TypedExpression applyRule(GcongrRule rule, Goal goal, boolean lenient) {
    return attempt(tc -> {
      ConcreteExpression fun = factory.ref(rule.definition().getRef());
      if (rule.instanceClass() != null) {
        ConcreteExpression instance = instanceOf(rule.instanceClass(), goal.left(), goal.relation.instance());
        if (instance == null) return null;
        fun = factory.app(fun, factory.arg(instance, false));
      }

      List<GcongrRule.Parameter> parameters = rule.parameters();
      List<GcongrRule.Kind> premiseKinds = new ArrayList<>();
      Instance inst = instantiate(tc, fun, parameters.size(), (i, parameter, type) -> {
        GcongrRule.Parameter param = parameters.get(i);
        if (param.explicit() != parameter.isExplicit()) return null;
        if (param.kind() == GcongrRule.Kind.UNIFY) return ArgumentMode.HOLE;
        premiseKinds.add(param.kind());
        return ArgumentMode.PREMISE;
      });
      if (inst == null || premiseKinds.size() != inst.premises().size()) return null;
      TypedExpression typed = inst.typed();
      if (!tc.compare(typed.getType(), goal.type, CMP.LE, marker, true, true, false)) return null;

      for (int i = 0; i < premiseKinds.size(); i++) {
        CoreInferenceReferenceExpression premise = inst.premises().get(i);
        CoreInferenceVariable variable = premise.getVariable();
        if (variable == null) return null;
        CoreExpression type = premise.computeType();
        TypedExpression proof = premiseKinds.get(i) == GcongrRule.Kind.MAIN ? proveMainPremise(type, lenient) : proveSidePremise(type, lenient);
        if (proof == null || !tc.solveInferenceVariable(variable, proof.getExpression())) return null;
      }
      return check(tc, factory.core(typed), goal.type);
    });
  }

  private @Nullable TypedExpression proveMainPremise(CoreExpression type, boolean lenient) {
    Goal subgoal = makeGoal(type);
    if (subgoal == null) {
      // A premise under binders is proved by a hypothesis
      TypedExpression result = proveByBinding(type);
      return result != null || !lenient ? result : makeLeaf(type);
    }
    // A strict relation between equal terms cannot be proved
    if (subgoal.strictness == Strictness.POSITIVE && compareTerms(subgoal.left(), subgoal.right())) return null;
    return proveMain(subgoal, lenient);
  }

  private @Nullable TypedExpression proveSidePremise(CoreExpression type, boolean lenient) {
    Positivity.Goal positivityGoal = positivity.makeGoal(type);
    if (positivityGoal != null) {
      TypedExpression result = positivity.prove(positivityGoal);
      if (result != null) return result;
    }
    TypedExpression result = proveByBinding(type);
    return result != null || !lenient ? result : makeLeaf(type);
  }

  private @Nullable TypedExpression proveByBinding(CoreExpression type) {
    for (CoreBinding binding : bindings) {
      TypedExpression result = attempt(tc -> check(tc, factory.ref(binding), type));
      if (result != null) return result;
    }
    return null;
  }

  public static List<GcongrRule> getRules(GcongrMeta meta, ExpressionTypechecker typechecker, @Nullable List<? extends org.arend.ext.core.definition.CoreDefinition> attributed) {
    List<GcongrRule> result = new ArrayList<>(meta.getBuiltinGcongrRules(typechecker));
    if (attributed != null) {
      for (org.arend.ext.core.definition.CoreDefinition definition : attributed) {
        GcongrRule rule = meta.getGcongrRule(definition);
        if (rule != null) result.add(rule);
      }
    }
    result.sort(Comparator.comparingInt(GcongrRule::mainCount));
    return result;
  }
}