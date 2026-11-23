package org.arend.lib.meta.gcongr;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.context.ContextHelper;
import org.arend.lib.error.IgnoredArgumentError;
import org.arend.lib.error.TypeError;
import org.arend.lib.meta.positivity.Positivity;
import org.arend.lib.meta.positivity.PositivityMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * {@code gcongr} extends {@link PositivityMeta} to inherit its dependencies, which are used to prove side conditions.
 */
public class GcongrMeta extends PositivityMeta {
  private final Supplier<? extends ArendRef> gcongrRef;

  // A reference to positivity makes definitions that use gcongr depend on lemmas marked with \attr positivity
  @Dependency(name = "positivity")                                  public ArendRef positivityRef;

  // +
  @Dependency(name = "PosetAddMonoid.<=_+")                         public ArendRef addLe;
  @Dependency(name = "OrderedAddMonoid.<_+-left")                   public ArendRef addLessLeft;
  @Dependency(name = "OrderedAddMonoid.<_+-right")                  public ArendRef addLessRight;
  @Dependency(name = "OrderedAddMonoid.<_+")                        public ArendRef addLess;
  @Dependency(name = "LinearlyOrderedAbMonoid.<=_+-left")           public ArendRef addLeLessLeft;
  @Dependency(name = "LinearlyOrderedAbMonoid.<=_+-right")          public ArendRef addLeLessRight;

  // negative, -
  @Dependency(name = "PosetAddGroup.negative_<=")                   public ArendRef negativeLe;
  @Dependency(name = "OrderedAddGroup.negative_<")                  public ArendRef negativeLess;
  @Dependency(name = "PosetAddGroup.<=_-")                          public ArendRef subLe;
  @Dependency(name = "OrderedAddGroup.<_--left")                    public ArendRef subLessLeft;
  @Dependency(name = "OrderedAddGroup.<_--right")                   public ArendRef subLessRight;
  @Dependency(name = "OrderedAddGroup.<_-")                         public ArendRef subLess;

  // *
  @Dependency(name = "PosetSemiring.<=_*_positive-left")            public ArendRef mulLeLeft;
  @Dependency(name = "PosetSemiring.<=_*_positive-right")           public ArendRef mulLeRight;
  @Dependency(name = "PosetSemiring.<=_*-monotone")                 public ArendRef mulLe;
  @Dependency(name = "OrderedSemiring.<_*_positive-left")           public ArendRef mulLessLeft;
  @Dependency(name = "OrderedSemiring.<_*_positive-right")          public ArendRef mulLessRight;
  @Dependency(name = "OrderedSemiring.<_*")                         public ArendRef mulLess;
  @Dependency(name = "OrderedSemiring.<_*_negative-left")           public ArendRef mulLessNegLeft;
  @Dependency(name = "OrderedSemiring.<_*_negative-right")          public ArendRef mulLessNegRight;

  // pow, scalars
  @Dependency(name = "PosetSemiring.pow_<=-monotone")               public ArendRef powLe;
  @Dependency(name = "LinearlyOrderedSemiring.pow_<-monotone")      public ArendRef powLess;
  @Dependency(name = "PosetAddMonoid.*n_<=")                        public ArendRef natMulLe;
  @Dependency(name = "LinearlyOrderedAbMonoid.*n_<")                public ArendRef natMulLess;
  @Dependency(name = "PosetSemiring.natCoef_<=")                    public ArendRef natCoefLe;
  @Dependency(name = "OrderedSemiring.natCoef_<")                   public ArendRef natCoefLess;
  @Dependency(name = "OrderedAAlgebra.coef_<=")                     public ArendRef coefLe;
  @Dependency(name = "OrderedAAlgebra.coef_<")                      public ArendRef coefLess;

  // inverses
  @Dependency(name = "OrderedField.pinv_<=")                        public ArendRef pinvLe;
  @Dependency(name = "OrderedField.pinv_<")                         public ArendRef pinvLess;
  @Dependency(name = "DiscreteOrderedField.finv_<=")                public ArendRef finvLe;
  @Dependency(name = "DiscreteOrderedField.finv_<")                 public ArendRef finvLess;

  // lattices
  @Dependency(name = "JoinSemilattice.join-monotone")               public ArendRef joinLe;
  @Dependency(name = "MeetSemilattice.meet-monotone")               public ArendRef meetLe;
  @Dependency(name = "BiorderedLattice.<_join-monotone")            public ArendRef joinLess;
  @Dependency(name = "BiorderedLattice.<_meet-monotone")            public ArendRef meetLess;
  @Dependency(name = "LatticeAbGroup.part+-mono")                   public ArendRef partLe;

  // sums
  @Dependency(name = "PosetAddMonoid.BigSum_<=")                    public ArendRef bigSumLe;
  @Dependency(name = "PosetAbMonoid.FinSum_<=")                     public ArendRef finSumLe;

  // Nat, Int, Rat
  @Dependency(name = "suc<=suc")                                    public ArendRef sucLe;
  @Dependency(name = "-'-monotone-left")                            public ArendRef truncSubLeLeft;
  @Dependency(name = "-'-monotone-right")                           public ArendRef truncSubLeRight;
  @Dependency(name = "div-monotone")                                public ArendRef divLe;
  @Dependency(name = "pos<=pos")                                    public ArendRef posLe;
  @Dependency(name = "pos<pos")                                     public ArendRef posLess;
  @Dependency(name = "neg<=neg")                                    public ArendRef negLe;
  @Dependency(name = "neg<neg")                                     public ArendRef negLess;
  @Dependency(name = "fromInt_<=")                                  public ArendRef fromIntLe;
  @Dependency(name = "fromInt_<")                                   public ArendRef fromIntLess;

  private List<GcongrRule> builtinGcongrRules;
  private final Map<CoreDefinition, Optional<GcongrRule>> gcongrRuleCache = Collections.synchronizedMap(new WeakHashMap<>());

  public GcongrMeta(Supplier<? extends ArendRef> gcongrRef) {
    super(() -> null);
    this.gcongrRef = gcongrRef;
  }

  private List<ArendRef> getBuiltinGcongrRuleRefs() {
    return Arrays.asList(
      addLe, addLessLeft, addLessRight, addLess, addLeLessLeft, addLeLessRight,
      negativeLe, negativeLess, subLe, subLessLeft, subLessRight, subLess,
      mulLeLeft, mulLeRight, mulLe, mulLessLeft, mulLessRight, mulLess, mulLessNegLeft, mulLessNegRight,
      powLe, powLess, natMulLe, natMulLess, natCoefLe, natCoefLess, coefLe, coefLess,
      pinvLe, pinvLess, finvLe, finvLess,
      joinLe, meetLe, joinLess, meetLess, partLe,
      bigSumLe, finSumLe,
      sucLe, truncSubLeLeft, truncSubLeRight, divLe, posLe, posLess, negLe, negLess, fromIntLe, fromIntLess);
  }

  synchronized List<GcongrRule> getBuiltinGcongrRules(ExpressionTypechecker typechecker) {
    if (builtinGcongrRules == null) {
      List<GcongrRule> rules = new ArrayList<>();
      for (ArendRef ref : getBuiltinGcongrRuleRefs()) {
        CoreDefinition definition = typechecker.getCoreDefinition(ref);
        GcongrRule rule = definition == null ? null : GcongrRule.make(definition, this);
        if (rule != null) rules.add(rule);
      }
      builtinGcongrRules = rules;
    }
    return builtinGcongrRules;
  }

  @Nullable GcongrRule getGcongrRule(CoreDefinition definition) {
    return gcongrRuleCache.computeIfAbsent(definition, def -> Optional.ofNullable(GcongrRule.make(def, this))).orElse(null);
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    CoreExpression expectedType = contextData.getExpectedType();
    List<CoreBinding> bindings = new ArrayList<>(new ContextHelper(null).getContextBindings(typechecker));

    Positivity positivity = new Positivity(typechecker, contextData.getMarker(), this, Positivity.getRules(this, typechecker, typechecker.getAttributedDefinitions(positivityRef)), bindings);
    Gcongr gcongr = new Gcongr(typechecker, contextData.getMarker(), this, Gcongr.getRules(this, typechecker, typechecker.getAttributedDefinitions(gcongrRef.get())), positivity, bindings);
    Gcongr.Goal goal = gcongr.makeGoal(expectedType);
    if (goal == null) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The goal should be of the form e1 < e2 or e1 <= e2", expectedType, contextData.getMarker()));
      return null;
    }

    TypedExpression proof = gcongr.prove(goal);
    if (proof == null) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "gcongr cannot find a lemma that applies to the goal", expectedType, contextData.getMarker()));
      return null;
    }

    List<Gcongr.Leaf> leaves = gcongr.getLeaves();
    List<? extends ConcreteArgument> args = contextData.getArguments();
    if (leaves.isEmpty()) {
      if (!args.isEmpty()) {
        typechecker.getErrorReporter().report(new IgnoredArgumentError(args.getFirst().getExpression()));
      }
      return proof;
    }

    ConcreteFactory factory = contextData.getFactory();
    CoreExpression subgoalsType;
    if (leaves.size() == 1) {
      subgoalsType = leaves.getFirst().type();
    } else {
      List<ConcreteParameter> params = new ArrayList<>(leaves.size());
      for (Gcongr.Leaf leaf : leaves) {
        params.add(factory.param(true, factory.core(leaf.type().computeTyped())));
      }
      TypedExpression sigma = typechecker.typecheck(factory.sigma(params), null);
      if (sigma == null) return null;
      subgoalsType = sigma.getExpression();
    }

    if (args.isEmpty()) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "gcongr cannot prove " + (leaves.size() == 1 ? "a subgoal" : "subgoals") + "; pass " + (leaves.size() == 1 ? "its proof" : "a tuple of their proofs") + " as an argument", subgoalsType, contextData.getMarker()));
      return null;
    }

    TypedExpression subproofs = typechecker.typecheck(args.getFirst().getExpression(), subgoalsType);
    if (subproofs == null) return null;
    for (int i = 0; i < leaves.size(); i++) {
      TypedExpression subproof = leaves.size() == 1 ? subproofs : typechecker.typecheck(factory.proj(factory.core(subproofs), i), null);
      if (subproof == null || !typechecker.solveInferenceVariable(leaves.get(i).variable(), subproof.getExpression())) return null;
    }
    return typechecker.typecheck(factory.core(proof), expectedType);
  }
}