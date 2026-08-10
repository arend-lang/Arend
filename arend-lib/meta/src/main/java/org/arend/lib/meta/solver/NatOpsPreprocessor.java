package org.arend.lib.meta.solver;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.definition.CoreFunctionDefinition;
import org.arend.ext.core.expr.CoreAppExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.core.expr.CoreProjExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.linear.Equation;
import org.arend.lib.util.RelationData;
import org.arend.lib.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects div/mod/-' subterms from a set of expressions and emits synthetic
 * hypotheses about them suitable for downstream consumption by LinearSolver
 * (Fourier–Motzkin over NatSemiring) or the cSemiring solver (Groebner over Nat).
 *
 * Emitted facts (NatSemiring instance):
 *
 *   Unconditional:
 *     For each (a, b) such that `a div b` or `a mod b` appears:
 *       EQUALS:    b * (a div b) + (a mod b) = a   (via Prelude.divModProp)
 *     For each (a, b) such that `a -' b` appears (only when the corresponding refs are set):
 *       LESS_OR_EQUALS:  a -' b <= a               (via -'<=id)
 *       LESS_OR_EQUALS:  a <= (a -' b) + b         (via -'_<=+)
 *
 *   Conditional (emitted when a witness for the precondition can be
 *   constructed from {@code suc/=0} for numeric-literal denominators, or located
 *   in the supplied context bindings):
 *     For (a, b) with mod and a witness of `b /= 0`:
 *       LESS:            a mod b < b               (via mod<right)
 *     For (a, b) with -' and a witness of `b <= a`:
 *       EQUALS:          b + (a -' b) = a          (via <=_exists)
 *     For (a, b) with -' and a witness of `a <= b`:
 *       EQUALS:          a -' b = 0                (via -'=0)
 *
 * Any {@link Refs} field that is {@code null} disables emission of the
 * corresponding hypothesis. The caller is free to filter the result list
 * (e.g. the cSemiring path takes only {@link Equation.Operation#EQUALS} entries).
 */
public class NatOpsPreprocessor {
  /** Refs and definitions required for emission. Any field may be null to disable the corresponding emission. */
  public record Refs(
      CoreFunctionDefinition truncMinus,
      ArendRef truncMinusRef,
      ArendRef truncMinusLEId,
      ArendRef truncMinusLEPlus,
      ArendRef truncMinusEqZero,
      ArendRef modLessRight,
      ArendRef sucNeqZero,
      ArendRef leqExists,
      ArendRef modZeroFromLDiv,
      ArendRef ldivDivEq,
      CoreFunctionDefinition natSemiring
  ) {}

  public static class SyntheticHypothesis {
    public final ConcreteExpression proof;
    /** May be {@code null} when {@link Refs#natSemiring()} is not provided. */
    public final CoreExpression instance;
    public final Equation.Operation op;
    public final CoreExpression lhs;
    public final CoreExpression rhs;

    public SyntheticHypothesis(ConcreteExpression proof, CoreExpression instance, Equation.Operation op, CoreExpression lhs, CoreExpression rhs) {
      this.proof = proof;
      this.instance = instance;
      this.op = op;
      this.lhs = lhs;
      this.rhs = rhs;
    }
  }

  private final ExpressionTypechecker typechecker;
  private final ConcreteFactory factory;
  private final Refs refs;
  private final CoreFunctionDefinition divDef;
  private final CoreFunctionDefinition modDef;
  private final CoreFunctionDefinition divModDef;
  private final CoreFunctionDefinition truncMinusDef;
  private final CoreExpression natSemiringInstance;

  public NatOpsPreprocessor(ExpressionTypechecker typechecker, ConcreteSourceNode marker, Refs refs) {
    this.typechecker = typechecker;
    this.refs = refs;
    this.factory = typechecker.getFactory().withData(marker);
    this.divDef = typechecker.getPrelude().getDiv();
    this.modDef = typechecker.getPrelude().getMod();
    this.divModDef = typechecker.getPrelude().getDivMod();
    this.truncMinusDef = refs.truncMinus();
    if (refs.natSemiring() != null) {
      TypedExpression instance = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(factory.ref(refs.natSemiring().getRef()), null));
      this.natSemiringInstance = instance == null ? null : instance.getExpression();
    } else {
      this.natSemiringInstance = null;
    }
  }

  /**
   * Returns the args of a saturated call to {@code def}, accepting both
   * {@code CoreFunCallExpression(def, [a, b])} and curried forms
   * {@code App(App(DefCall(def), a), b)}. No normalization is performed.
   */
  private List<CoreExpression> matchCall(CoreExpression expr, CoreFunctionDefinition def) {
    CoreExpression e = expr.getUnderlyingExpression();
    if (e instanceof CoreFunCallExpression fc && fc.getDefinition() == def && fc.getDefCallArguments().size() == 2) {
      return new ArrayList<>(fc.getDefCallArguments());
    }
    if (e instanceof CoreAppExpression app1) {
      CoreExpression fun1 = app1.getFunction().getUnderlyingExpression();
      if (fun1 instanceof CoreAppExpression app2) {
        CoreExpression fun2 = app2.getFunction().getUnderlyingExpression();
        if (fun2 instanceof CoreFunCallExpression fc && fc.getDefinition() == def && fc.getDefCallArguments().isEmpty()) {
          List<CoreExpression> args = new ArrayList<>(2);
          args.add(app2.getArgument());
          args.add(app1.getArgument());
          return args;
        }
      }
    }
    return null;
  }

  /**
   * Matches {@code Proj(divMod a b, i)} — the unfolded form of {@code a div b}
   * (i==0) or {@code a mod b} (i==1). Returns {@code [a, b]}.
   */
  private List<CoreExpression> matchDivModProj(CoreExpression expr) {
    CoreExpression e = expr.getUnderlyingExpression();
    if (!(e instanceof CoreProjExpression proj)) return null;
    return matchCall(proj.getExpression(), divModDef);
  }

  private static class ArgPair {
    final CoreExpression a;
    final CoreExpression b;
    ArgPair(CoreExpression a, CoreExpression b) { this.a = a; this.b = b; }
  }

  private static boolean containsPair(List<ArgPair> list, CoreExpression a, CoreExpression b) {
    for (ArgPair p : list) {
      if (p.a.compare(a, CMP.EQ) && p.b.compare(b, CMP.EQ)) return true;
    }
    return false;
  }

  /**
   * Walks the given expressions, collects unique div/mod/-' subterms,
   * and emits synthetic hypotheses about them. Context {@code bindings}
   * are scanned to discover preconditions enabling sharper synthetic facts.
   */
  public List<SyntheticHypothesis> collect(List<CoreExpression> expressions, List<? extends CoreBinding> bindings) {
    if (expressions == null || expressions.isEmpty()) return Collections.emptyList();
    if (bindings == null) bindings = Collections.emptyList();

    List<ArgPair> divModPairs = new ArrayList<>();
    List<ArgPair> truncMinusPairs = new ArrayList<>();

    for (CoreExpression expr : expressions) {
      if (expr == null) continue;
      expr.processSubexpression(sub -> {
        List<CoreExpression> args = matchCall(sub, divDef);
        if (args == null) args = matchCall(sub, modDef);
        if (args == null) args = matchDivModProj(sub);
        if (args != null) {
          CoreExpression a = args.get(0), b = args.get(1);
          if (!containsPair(divModPairs, a, b)) divModPairs.add(new ArgPair(a, b));
          return CoreExpression.FindAction.CONTINUE;
        }
        if (truncMinusDef != null) {
          args = matchCall(sub, truncMinusDef);
          if (args != null) {
            CoreExpression a = args.get(0), b = args.get(1);
            if (!containsPair(truncMinusPairs, a, b)) truncMinusPairs.add(new ArgPair(a, b));
          }
        }
        return CoreExpression.FindAction.CONTINUE;
      });
    }

    List<SyntheticHypothesis> result = new ArrayList<>();

    for (ArgPair pair : divModPairs) {
      SyntheticHypothesis h = makeDivModEquation(pair.a, pair.b);
      if (h != null) result.add(h);
      SyntheticHypothesis bound = makeModLessRight(pair.a, pair.b, bindings);
      if (bound != null) result.add(bound);
      SyntheticHypothesis modZero = makeModEqZero(pair.a, pair.b, bindings);
      if (modZero != null) result.add(modZero);
      SyntheticHypothesis ldivDiv = makeLDivDivEq(pair.a, pair.b, bindings);
      if (ldivDiv != null) result.add(ldivDiv);
    }

    for (ArgPair pair : truncMinusPairs) {
      SyntheticHypothesis h1 = makeTruncMinusUpperBound(pair.a, pair.b);
      if (h1 != null) result.add(h1);
      SyntheticHypothesis h2 = makeTruncMinusPlusLowerBound(pair.a, pair.b);
      if (h2 != null) result.add(h2);
      SyntheticHypothesis eq = makeTruncMinusEqAFromBLE(pair.a, pair.b, bindings);
      if (eq != null) result.add(eq);
      SyntheticHypothesis zero = makeTruncMinusEqZero(pair.a, pair.b, bindings);
      if (zero != null) result.add(zero);
    }

    return result;
  }

  // divModProp a b : b * (a div b) + (a mod b) = a
  private SyntheticHypothesis makeDivModEquation(CoreExpression a, CoreExpression b) {
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    ConcreteExpression proof = factory.app(factory.ref(typechecker.getPrelude().getDivModPropRef()), true, aRef, bRef);
    TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
    if (typedProof == null) return null;
    CoreFunCallExpression eq = typedProof.getType().toEquality();
    if (eq == null) return null;
    return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.EQUALS,
        eq.getDefCallArguments().get(1), eq.getDefCallArguments().get(2));
  }

  // -'<=id {a} {b} : a -' b <= a
  private SyntheticHypothesis makeTruncMinusUpperBound(CoreExpression a, CoreExpression b) {
    if (refs.truncMinusLEId() == null || refs.truncMinusRef() == null) return null;
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    ConcreteExpression proof = factory.appBuilder(factory.ref(refs.truncMinusLEId()))
        .app(aRef, false)
        .app(bRef, false)
        .build();
    TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
    if (typedProof == null) return null;
    CoreExpression lhs = typecheckExpr(factory.app(factory.ref(refs.truncMinusRef()), true, aRef, bRef));
    if (lhs == null) return null;
    return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.LESS_OR_EQUALS, lhs, a);
  }

  // -'_<=+ {a} {b} : a <= (a -' b) + b
  private SyntheticHypothesis makeTruncMinusPlusLowerBound(CoreExpression a, CoreExpression b) {
    if (refs.truncMinusLEPlus() == null || refs.truncMinusRef() == null) return null;
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    ConcreteExpression proof = factory.appBuilder(factory.ref(refs.truncMinusLEPlus()))
        .app(aRef, false)
        .app(bRef, false)
        .build();
    TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
    if (typedProof == null) return null;
    ConcreteExpression truncExpr = factory.app(factory.ref(refs.truncMinusRef()), true, aRef, bRef);
    ConcreteExpression plusExpr = factory.app(factory.ref(typechecker.getPrelude().getPlusRef()), true, truncExpr, bRef);
    CoreExpression rhs = typecheckExpr(plusExpr);
    if (rhs == null) return null;
    return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.LESS_OR_EQUALS, a, rhs);
  }

  /** Tries {@code mod<right {a} {b} candidate : a mod b < b}. */
  private SyntheticHypothesis makeModLessRight(CoreExpression a, CoreExpression b, List<? extends CoreBinding> bindings) {
    if (refs.modLessRight() == null) return null;
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    for (ConcreteExpression candidate : nonZeroCandidates(bindings)) {
      ConcreteExpression proof = factory.appBuilder(factory.ref(refs.modLessRight()))
          .app(aRef, false)
          .app(bRef, false)
          .app(candidate, true)
          .build();
      TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
      if (typedProof == null) continue;
      RelationData rel = RelationData.getRelationData(typedProof.getType());
      if (rel == null) continue;
      return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.LESS, rel.leftExpr, rel.rightExpr);
    }
    return null;
  }

  /**
   * Tries {@code ldiv*div=id {a} {b} candidate : b * (a div b) = a} for each
   * context binding (a witness of {@code LDiv b a}). Strictly stronger than
   * {@link #makeModEqZero} for goals that mention {@code b * (a div b)} but not
   * {@code (a mod b)} — the synth's LHS matches the goal's LHS directly.
   */
  private SyntheticHypothesis makeLDivDivEq(CoreExpression a, CoreExpression b, List<? extends CoreBinding> bindings) {
    if (refs.ldivDivEq() == null) return null;
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    for (CoreBinding binding : bindings) {
      ConcreteExpression proof = factory.appBuilder(factory.ref(refs.ldivDivEq()))
          .app(aRef, false)
          .app(bRef, false)
          .app(factory.ref(binding), true)
          .build();
      TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
      if (typedProof == null) continue;
      CoreFunCallExpression eq = typedProof.getType().toEquality();
      if (eq == null) continue;
      return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.EQUALS,
          eq.getDefCallArguments().get(1), eq.getDefCallArguments().get(2));
    }
    return null;
  }

  /**
   * Tries {@code div_mod {a} {b} candidate : a mod b = 0} for each context binding
   * (a witness of {@code LDiv b a}, i.e. {@code b | a}).
   */
  private SyntheticHypothesis makeModEqZero(CoreExpression a, CoreExpression b, List<? extends CoreBinding> bindings) {
    if (refs.modZeroFromLDiv() == null) return null;
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    for (CoreBinding binding : bindings) {
      ConcreteExpression proof = factory.appBuilder(factory.ref(refs.modZeroFromLDiv()))
          .app(aRef, false)
          .app(bRef, false)
          .app(factory.ref(binding), true)
          .build();
      TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
      if (typedProof == null) continue;
      CoreFunCallExpression eq = typedProof.getType().toEquality();
      if (eq == null) continue;
      return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.EQUALS,
          eq.getDefCallArguments().get(1), eq.getDefCallArguments().get(2));
    }
    return null;
  }

  /** Tries {@code <=_exists {b} {a} candidate : b + (a -' b) = a}. */
  private SyntheticHypothesis makeTruncMinusEqAFromBLE(CoreExpression a, CoreExpression b, List<? extends CoreBinding> bindings) {
    if (refs.leqExists() == null) return null;
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    for (CoreBinding binding : bindings) {
      ConcreteExpression proof = factory.appBuilder(factory.ref(refs.leqExists()))
          .app(bRef, false)
          .app(aRef, false)
          .app(factory.ref(binding), true)
          .build();
      TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
      if (typedProof == null) continue;
      CoreFunCallExpression eq = typedProof.getType().toEquality();
      if (eq == null) continue;
      return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.EQUALS,
          eq.getDefCallArguments().get(1), eq.getDefCallArguments().get(2));
    }
    return null;
  }

  /** Tries {@code -'=0 {a} {b} candidate : a -' b = 0}. */
  private SyntheticHypothesis makeTruncMinusEqZero(CoreExpression a, CoreExpression b, List<? extends CoreBinding> bindings) {
    if (refs.truncMinusEqZero() == null) return null;
    ConcreteExpression aRef = factory.core(a.computeTyped());
    ConcreteExpression bRef = factory.core(b.computeTyped());
    for (CoreBinding binding : bindings) {
      ConcreteExpression proof = factory.appBuilder(factory.ref(refs.truncMinusEqZero()))
          .app(aRef, false)
          .app(bRef, false)
          .app(factory.ref(binding), true)
          .build();
      TypedExpression typedProof = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(proof, null));
      if (typedProof == null) continue;
      CoreFunCallExpression eq = typedProof.getType().toEquality();
      if (eq == null) continue;
      return new SyntheticHypothesis(factory.core(typedProof), natSemiringInstance, Equation.Operation.EQUALS,
          eq.getDefCallArguments().get(1), eq.getDefCallArguments().get(2));
    }
    return null;
  }

  /** Candidate proofs of {@code _ /= 0}: {@code suc/=0}, then each binding. */
  private List<ConcreteExpression> nonZeroCandidates(List<? extends CoreBinding> bindings) {
    List<ConcreteExpression> result = new ArrayList<>(bindings.size() + 1);
    if (refs.sucNeqZero() != null) result.add(factory.ref(refs.sucNeqZero()));
    for (CoreBinding binding : bindings) {
      result.add(factory.ref(binding));
    }
    return result;
  }

  private CoreExpression typecheckExpr(ConcreteExpression expr) {
    TypedExpression typed = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(expr, null));
    return typed == null ? null : typed.getExpression();
  }
}
