package org.arend.lib.meta.positivity;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreInferenceVariable;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
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
public class Positivity {
  private static final int MAX_DEPTH = 32;
  private static final int MAX_PARAMETERS = 16;

  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;
  private final ConcreteExpression marker;
  private final PositivityMeta meta;
  private final List<PositivityRule> rules;
  private final List<CoreBinding> bindings;
  private final List<Hypothesis> hypotheses = new ArrayList<>();
  private final List<MemoEntry> memo = new ArrayList<>();
  private final List<CoreExpression> inProgress = new ArrayList<>();
  private CoreExpression natInstance;
  private final Map<List<Object>, Object> implementationHeads = new HashMap<>();

  public record Goal(Strictness strictness, CoreExpression type, CoreExpression term, CoreExpression zero, @Nullable CoreExpression instance, boolean linear) {}

  private record Hypothesis(CoreBinding binding, Relation relation, boolean zero) {}

  private record MemoEntry(CoreExpression type, @Nullable TypedExpression proof) {}

  public Positivity(ExpressionTypechecker typechecker, ConcreteExpression marker, PositivityMeta meta, List<PositivityRule> rules, List<CoreBinding> bindings) {
    this.typechecker = typechecker;
    this.factory = typechecker.getFactory().withData(marker);
    this.marker = marker;
    this.meta = meta;
    this.rules = rules;
    this.bindings = bindings;
    for (CoreBinding binding : bindings) {
      Relation relation = Relation.parse(binding.getType(), meta, getNatInstance());
      if (relation != null) {
        hypotheses.add(new Hypothesis(binding, relation, isZero(relation.other())));
      }
    }
  }

  private CoreExpression getNatInstance() {
    if (natInstance == null) {
      TypedExpression result = typechecker.typecheck(factory.ref(meta.NatSemiring.getRef()), null);
      natInstance = result == null ? null : result.getExpression();
    }
    return natInstance;
  }

  public @Nullable Goal makeGoal(CoreExpression type) {
    Relation relation = Relation.parse(type, meta, getNatInstance());
    return relation == null || !isZero(relation.other()) ? null : new Goal(relation.kind(), type, relation.term(), relation.other(), relation.instance(), relation.linear());
  }

  private boolean isZero(CoreExpression expr) {
    for (int i = 0; i < 2; i++) {
      expr = Relation.deref(expr);
      if (expr instanceof CoreIntegerExpression intExpr) return intExpr.getBigInteger().equals(BigInteger.ZERO);
      if (expr instanceof CoreFieldCallExpression fieldCall && fieldCall.getDefinition() == meta.zro) return true;
      if (expr instanceof CoreConCallExpression conCall && conCall.getDefinition().getRef().equals(meta.posRef) && conCall.getDefCallArguments().size() == 1) {
        expr = conCall.getDefCallArguments().getFirst();
        continue;
      }
      CoreExpression normExpr = expr.normalize(NormalizationMode.WHNF);
      if (normExpr == expr) return false;
      expr = normExpr;
    }
    return false;
  }

  private boolean compareTerms(CoreExpression expr1, CoreExpression expr2) {
    return Boolean.TRUE.equals(Utils.tryTypecheck(typechecker, tc -> {
      boolean ok = tc.compare(expr1, expr2, CMP.EQ, marker, false, false, false);
      tc.loadSavedState();
      return ok ? true : null;
    }));
  }

  private interface Attempt {
    @Nullable TypedExpression run(ExpressionTypechecker tc);
  }

  /**
   * Runs {@code attempt} in a saved state; restores the state and the memo table if it fails.
   */
  private @Nullable TypedExpression attempt(Attempt attempt) {
    int memoSize = memo.size();
    TypedExpression result = Utils.tryTypecheck(typechecker, tc -> {
      TypedExpression res = attempt.run(tc);
      if (res == null) tc.loadSavedState();
      return res;
    });
    if (result == null) {
      // Proofs found during a failed attempt may refer to its inference variables; failures remain valid
      for (int i = memo.size() - 1; i >= memoSize; i--) {
        if (memo.get(i).proof != null) memo.remove(i);
      }
    }
    return result;
  }

  private @Nullable TypedExpression check(ExpressionTypechecker tc, ConcreteExpression expr, CoreExpression type) {
    TypedExpression result = tc.typecheck(expr, type);
    return result == null || result.getExpression() instanceof CoreErrorExpression ? null : result;
  }

  private ConcreteExpression core(CoreExpression expr) {
    return factory.core(expr.computeTyped());
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

  private @Nullable ConcreteExpression instanceOf(CoreClassDefinition instanceClass, CoreExpression term, @Nullable CoreExpression instance) {
    if (instance != null) {
      CoreClassCallExpression classCall = Utils.getClassCall(instance.computeType());
      if (classCall != null && classCall.getDefinition().isSubClassOf(instanceClass)) {
        return core(instance);
      }
    }
    CoreExpression carrier = term.computeType().normalize(NormalizationMode.WHNF);
    return Utils.tryTypecheck(typechecker, tc -> tc.findInstance(instanceClass, carrier, marker));
  }

  /**
   * Applies a dynamic lemma of {@code lemmaClass} to {@code args}; {@code instance} is passed explicitly if it is an instance of this class.
   */
  private ConcreteExpression appWithInstance(ArendRef lemma, CoreClassDefinition lemmaClass, @Nullable CoreExpression instance, ConcreteExpression... args) {
    List<ConcreteArgument> arguments = new ArrayList<>(args.length + 1);
    if (instance != null) {
      CoreClassCallExpression classCall = Utils.getClassCall(instance.computeType());
      if (classCall != null && classCall.getDefinition().isSubClassOf(lemmaClass)) {
        arguments.add(factory.arg(core(instance), false));
      }
    }
    for (ConcreteExpression arg : args) {
      arguments.add(factory.arg(arg, true));
    }
    return factory.app(factory.ref(lemma), arguments);
  }

  private static boolean isGround(CoreExpression expr) {
    return !expr.processSubexpression(e -> e instanceof CoreInferenceReferenceExpression ? CoreExpression.FindAction.STOP : CoreExpression.FindAction.CONTINUE);
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
      if (rule.strictness() == goal.strictness && matchesHead(rule, head, goal)) {
        result = applyRule(rule, goal);
        if (result != null) return result;
      }
    }

    if (goal.strictness != Strictness.POSITIVE) {
      return proveByWeakening(goal);
    }
    return null;
  }

  /**
   * Checks if the head of the conclusion of {@code rule} can match {@code termHead}.
   * A field can match a function if the function is its implementation in the instance of the goal.
   */
  private boolean matchesHead(PositivityRule rule, @Nullable Object termHead, Goal goal) {
    Object ruleHead = rule.head();
    if (ruleHead == null || ruleHead.equals(termHead)) return true;
    if (!(ruleHead instanceof CoreClassField field) || termHead instanceof CoreClassField) return false;
    if (termHead == null) return false;
    return termHead.equals(getImplementationHead(field, goal));
  }

  private @Nullable Object getImplementationHead(CoreClassField field, Goal goal) {
    Object instanceHead = goal.instance == null ? goal.term.computeType() : Relation.head(goal.instance);
    List<Object> key = List.of(field, instanceHead == null ? goal.term.computeType() : instanceHead);
    if (implementationHeads.containsKey(key)) return implementationHeads.get(key);
    Object result = Utils.tryTypecheck(typechecker, tc -> {
      ConcreteExpression instance = instanceOf(field.getParentClass(), goal.term, goal.instance);
      if (instance == null) return null;
      TypedExpression typed = check(tc, factory.app(factory.ref(field.getRef()), factory.arg(instance, false)), null);
      if (typed == null) return null;
      CoreExpression expr = typed.getExpression().normalize(NormalizationMode.WHNF);
      while (expr instanceof CoreLamExpression lamExpr) {
        expr = lamExpr.getBody();
      }
      Object head = Relation.head(expr);
      tc.loadSavedState();
      return head;
    });
    implementationHeads.put(key, result);
    return result;
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
   * Proves {@code 0 < n} for a positive numeral {@code n} of type Nat.
   */
  private @Nullable TypedExpression proveNumber(Goal goal) {
    if (goal.strictness != Strictness.POSITIVE) return null;
    CoreExpression term = goal.term.normalize(NormalizationMode.WHNF);
    if (term instanceof CoreIntegerExpression intExpr && intExpr.getBigInteger().signum() > 0 || term instanceof CoreConCallExpression conCall && conCall.getDefinition() == typechecker.getPrelude().getSuc()) {
      return attempt(tc -> check(tc, factory.ref(meta.zeroLessSuc), goal.type));
    }
    return null;
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
      TypedExpression typed = tc.typecheck(fun, null);
      List<CoreInferenceReferenceExpression> premises = new ArrayList<>();
      for (int i = 0; typed != null && !(typed.getExpression() instanceof CoreErrorExpression) && i < MAX_PARAMETERS; i++) {
        CoreExpression type = Relation.deref(typed.getType());
        if (Relation.parseSyntactically(type, meta, getNatInstance()) != null) break;
        if (!(type instanceof CorePiExpression)) type = type.normalize(NormalizationMode.WHNF);
        if (!(type instanceof CorePiExpression piType)) break;
        CoreParameter parameter = piType.getParameters();
        ConcreteExpression arg;
        if (parameter.isExplicit()) {
          CoreInferenceReferenceExpression premise = tc.generateNewInferenceVariable("p" + (premises.size() + 1), parameter.getType(), marker, false);
          premises.add(premise);
          arg = factory.core(premise.computeTyped());
        } else {
          arg = factory.hole();
        }
        typed = tc.typecheck(factory.app(factory.core(typed), factory.arg(arg, parameter.isExplicit())), null);
      }
      if (typed == null || typed.getExpression() instanceof CoreErrorExpression) return null;
      if (!tc.compare(typed.getType(), goal.type, CMP.LE, marker, true, true, false)) return null;

      for (CoreInferenceReferenceExpression premise : premises) {
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
    Relation relation = Relation.parse(type, meta, getNatInstance());
    Goal subgoal = relation == null || !isZero(relation.other()) ? null : makeGoal(relation.kind(), Relation.deref(relation.term()), Relation.deref(relation.other()), relation.instance() == null ? null : Relation.deref(relation.instance()), relation.linear());
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
