package org.arend.lib.meta.positivity;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Helpers shared by the search procedures of order metas such as {@code positivity} and {@code gcongr}.
 */
public abstract class OrderSearch {
  protected final ExpressionTypechecker typechecker;
  protected final ConcreteFactory factory;
  protected final ConcreteExpression marker;
  protected final PositivityMeta meta;
  protected final List<CoreBinding> bindings;
  private CoreExpression natInstance;
  private final Map<List<Object>, Object> implementationHeads = new HashMap<>();

  protected OrderSearch(ExpressionTypechecker typechecker, ConcreteExpression marker, PositivityMeta meta, List<CoreBinding> bindings) {
    this.typechecker = typechecker;
    this.factory = typechecker.getFactory().withData(marker);
    this.marker = marker;
    this.meta = meta;
    this.bindings = bindings;
  }

  protected CoreExpression getNatInstance() {
    if (natInstance == null) {
      TypedExpression result = typechecker.typecheck(factory.ref(meta.NatSemiring.getRef()), null);
      natInstance = result == null ? null : result.getExpression();
    }
    return natInstance;
  }

  protected @Nullable Relation parseRelation(CoreExpression type) {
    return Relation.parse(type, meta, getNatInstance());
  }

  /**
   * Compares terms without unfolding definitions; the comparison does not change the state of the typechecker.
   */
  protected boolean compareTerms(CoreExpression expr1, CoreExpression expr2) {
    return Boolean.TRUE.equals(Utils.tryTypecheck(typechecker, tc -> {
      boolean ok = tc.compare(expr1, expr2, CMP.EQ, marker, false, false, false);
      tc.loadSavedState();
      return ok ? true : null;
    }));
  }

  protected interface Attempt {
    @Nullable TypedExpression run(ExpressionTypechecker tc);
  }

  /**
   * @return a value that is passed to {@link #rollback} if an attempt fails.
   */
  protected int mark() {
    return 0;
  }

  /**
   * Restores auxiliary data after a failed attempt that was started at {@code mark}.
   */
  protected void rollback(int mark) {}

  /**
   * Runs {@code attempt} in a saved state; restores the state if it fails.
   */
  protected @Nullable TypedExpression attempt(Attempt attempt) {
    int mark = mark();
    TypedExpression result = Utils.tryTypecheck(typechecker, tc -> {
      TypedExpression res = attempt.run(tc);
      if (res == null) tc.loadSavedState();
      return res;
    });
    if (result == null) rollback(mark);
    return result;
  }

  protected @Nullable TypedExpression check(ExpressionTypechecker tc, ConcreteExpression expr, @Nullable CoreExpression type) {
    TypedExpression result = tc.typecheck(expr, type);
    return result == null || result.getExpression() instanceof CoreErrorExpression ? null : result;
  }

  protected ConcreteExpression core(CoreExpression expr) {
    return factory.core(expr.computeTyped());
  }

  protected @Nullable ConcreteExpression instanceOf(CoreClassDefinition instanceClass, CoreExpression term, @Nullable CoreExpression instance) {
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
  protected ConcreteExpression appWithInstance(ArendRef lemma, CoreClassDefinition lemmaClass, @Nullable CoreExpression instance, ConcreteExpression... args) {
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

  protected static boolean isGround(CoreExpression expr) {
    return !expr.processSubexpression(e -> e instanceof CoreInferenceReferenceExpression ? CoreExpression.FindAction.STOP : CoreExpression.FindAction.CONTINUE);
  }

  /**
   * Checks if the head {@code ruleHead} of the conclusion of a rule can match {@code termHead}.
   * A field can match a function if the function is its implementation in the instance on the type of {@code term}.
   */
  protected boolean matchesHead(@Nullable Object ruleHead, @Nullable Object termHead, CoreExpression term, @Nullable CoreExpression instance) {
    if (ruleHead == null || ruleHead.equals(termHead)) return true;
    if (!(ruleHead instanceof CoreClassField field) || termHead instanceof CoreClassField) return false;
    if (termHead == null) return false;
    return termHead.equals(getImplementationHead(field, term, instance));
  }

  private @Nullable Object getImplementationHead(CoreClassField field, CoreExpression term, @Nullable CoreExpression instance) {
    Object instanceHead = instance == null ? term.computeType() : Relation.head(instance);
    List<Object> key = List.of(field, instanceHead == null ? term.computeType() : instanceHead);
    if (implementationHeads.containsKey(key)) return implementationHeads.get(key);
    Object result = Utils.tryTypecheck(typechecker, tc -> {
      ConcreteExpression instanceExpr = instanceOf(field.getParentClass(), term, instance);
      if (instanceExpr == null) return null;
      TypedExpression typed = check(tc, factory.app(factory.ref(field.getRef()), factory.arg(instanceExpr, false)), null);
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

  /**
   * How a parameter of a rule is instantiated.
   */
  public enum ArgumentMode {
    /** By a hole that is solved by unification. */
    HOLE,
    /** By a new inference variable that is not solvable from equations; it is solved later by a proof. */
    PREMISE
  }

  /**
   * The result of {@link #instantiate}: the instantiated rule and inference variables for its premises.
   */
  protected record Instance(TypedExpression typed, List<CoreInferenceReferenceExpression> premises) {}

  /**
   * Instantiates parameters of a rule one by one.
   *
   * @param fun     the rule, possibly applied to its instance
   * @param mode    the mode of a parameter given its index and explicitness, or {@code null} to stop
   */
  protected @Nullable Instance instantiate(ExpressionTypechecker tc, ConcreteExpression fun, int maxParameters, ModeChooser mode) {
    TypedExpression typed = tc.typecheck(fun, null);
    if (typed == null || typed.getExpression() instanceof CoreErrorExpression) return null;
    // Typechecking a reference may insert leading implicit arguments
    // (for example, all of them if the first one is an instance parameter), so we skip the corresponding parameters
    int start = maxParameters - countParameters(typed.getType());
    if (start < 0) return null;
    List<CoreInferenceReferenceExpression> premises = new ArrayList<>();
    for (int i = start; typed != null && !(typed.getExpression() instanceof CoreErrorExpression) && i < maxParameters; i++) {
      CoreExpression type = Relation.deref(typed.getType());
      if (!(type instanceof CorePiExpression) && Relation.parseSyntactically(type, meta, getNatInstance()) != null) break;
      if (!(type instanceof CorePiExpression)) type = type.normalize(NormalizationMode.WHNF);
      if (!(type instanceof CorePiExpression piType)) break;
      CoreParameter parameter = piType.getParameters();
      ArgumentMode argMode = mode.choose(i, parameter, type);
      if (argMode == null) break;
      ConcreteExpression arg;
      if (argMode == ArgumentMode.PREMISE) {
        CoreInferenceReferenceExpression premise = tc.generateNewInferenceVariable("p" + (premises.size() + 1), parameter.getType(), marker, false);
        premises.add(premise);
        arg = factory.core(premise.computeTyped());
      } else {
        arg = factory.hole();
      }
      typed = tc.typecheck(factory.app(factory.core(typed), factory.arg(arg, parameter.isExplicit())), null);
    }
    if (typed == null || typed.getExpression() instanceof CoreErrorExpression) return null;
    return new Instance(typed, premises);
  }

  /**
   * Counts the parameters of {@code type} before its conclusion, which is a relation.
   */
  private int countParameters(CoreExpression type) {
    int count = 0;
    while (true) {
      type = Relation.deref(type);
      if (!(type instanceof CorePiExpression) && Relation.parseSyntactically(type, meta, getNatInstance()) != null) return count;
      if (!(type instanceof CorePiExpression)) type = type.normalize(NormalizationMode.WHNF);
      if (!(type instanceof CorePiExpression piType)) return count;
      for (CoreParameter param = piType.getParameters(); param.hasNext(); param = param.getNext()) {
        count++;
      }
      type = piType.getCodomain();
    }
  }

  protected interface ModeChooser {
    /**
     * @param index     the index of the parameter
     * @param parameter the parameter
     * @param type      the Pi type of the rule
     */
    @Nullable ArgumentMode choose(int index, CoreParameter parameter, CoreExpression type);
  }
}