package org.arend.lib.meta.positivity;

import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.definition.*;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.context.ContextHelper;
import org.arend.lib.error.TypeError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class PositivityMeta extends BaseMetaDefinition {
  private final Supplier<? extends ArendRef> selfRef;

  @Dependency(name = "StrictPoset.<")                       public CoreClassField less;
  @Dependency(name = "StrictPoset.>")                       public CoreClassField greater;
  @Dependency(name = "Preorder.<=")                         public CoreClassField lessOrEquals;
  @Dependency(name = "Preorder.>=")                         public CoreClassField greaterOrEquals;
  @Dependency(name = "AddPointed.zro")                      public CoreClassField zro;
  @Dependency(name = "LinearOrder.<=")                      public ArendRef linearLessOrEquals;
  @Dependency(name = "OrderedAddGroup.<")                   public ArendRef addGroupLess;
  @Dependency(name = "NatOrder.<")                          public ArendRef natLess;
  @Dependency(name = "/=")                                  public ArendRef notEquals;
  @Dependency(name = "Int.pos")                             public ArendRef posRef;
  @Dependency                                               public CoreClassDefinition StrictPoset;
  @Dependency                                               public CoreClassDefinition Preorder;
  @Dependency                                               public CoreClassDefinition BiorderedSet;
  @Dependency                                               public CoreFunctionDefinition NatSemiring;

  @Dependency(name = "BiorderedSet.<=-less")                public ArendRef lessToLessOrEquals;
  @Dependency(name = "LinearOrder.<_<=")                    public ArendRef linearLessToLessOrEquals;
  @Dependency(name = "StrictPoset.>_/=")                    public ArendRef lessToNotEquals;
  @Dependency(name = "BiorderedSet.<-transitive-left")      public ArendRef lessTransLeft;
  @Dependency(name = "BiorderedSet.<-transitive-right")     public ArendRef lessTransRight;
  @Dependency(name = "Preorder.<=-transitive")              public ArendRef leTrans;
  @Dependency(name = "NatOrder.zero<suc")                   public ArendRef zeroLessSuc;

  // Builtin rules: 0 < t
  @Dependency(name = "OrderedSemiring.zro<ide")                     public ArendRef zroLessIde;
  @Dependency(name = "LinearlyOrderedSemiring.natCoef>0")           public ArendRef natCoefPos;
  @Dependency(name = "OrderedAddMonoid.<_+-positive")               public ArendRef addPos;
  @Dependency(name = "LinearlyOrderedAbMonoid.<_+-positive-left")   public ArendRef addPosLeft;
  @Dependency(name = "LinearlyOrderedAbMonoid.<_+-positive-right")  public ArendRef addPosRight;
  @Dependency(name = "OrderedSemiring.<_*_positive_positive")       public ArendRef mulPos;
  @Dependency(name = "OrderedSemiring.pow>0")                       public ArendRef powPos;
  @Dependency(name = "LinearlyOrderedAbMonoid.*n_>0")               public ArendRef natMulPos;
  @Dependency(name = "OrderedField.pinv>0")                         public ArendRef pinvPos;
  @Dependency(name = "DiscreteOrderedField.finv>0")                 public ArendRef finvPos;
  @Dependency(name = "BiorderedLattice.<_join-left")                public ArendRef joinPosLeft;
  @Dependency(name = "BiorderedLattice.<_join-right")               public ArendRef joinPosRight;
  @Dependency(name = "BiorderedLattice.<_meet-univ")                public ArendRef meetPos;

  // Builtin rules: 0 <= t
  @Dependency(name = "Preorder.<=-refl")                            public ArendRef leRefl;
  @Dependency(name = "zero<=_")                                     public ArendRef natNonneg;
  @Dependency(name = "IntRing.pos>=0")                              public ArendRef intPosNonneg;
  @Dependency(name = "PosetSemiring.natCoef>=0")                    public ArendRef natCoefNonneg;
  @Dependency(name = "PosetAddMonoid.<=_+-positive")                public ArendRef addNonneg;
  @Dependency(name = "LinearlyOrderedSemiring.square_>=0")          public ArendRef squareNonneg;
  @Dependency(name = "PosetSemiring.<=_*_positive_positive")        public ArendRef mulNonneg;
  @Dependency(name = "PosetSemiring.pow>=0")                        public ArendRef powNonneg;
  @Dependency(name = "PosetAddMonoid.*n_>=0")                       public ArendRef natMulNonneg;
  @Dependency(name = "DiscreteOrderedField.finv>=0")                public ArendRef finvNonneg;
  @Dependency(name = "LatticeAbGroup.abs>=0")                       public ArendRef absNonneg;
  @Dependency(name = "LatticeAbGroup.part+>=0")                     public ArendRef partNonneg;
  @Dependency(name = "JoinSemilattice.<=_join-left")                public ArendRef joinNonnegLeft;
  @Dependency(name = "JoinSemilattice.<=_join-right")               public ArendRef joinNonnegRight;
  @Dependency(name = "MeetSemilattice.meet-univ")                   public ArendRef meetNonneg;

  // Builtin rules: t /= 0
  @Dependency(name = "Domain.nonZero_*")                            public ArendRef mulNonzero;
  @Dependency(name = "DiscreteField.finv/=0")                       public ArendRef finvNonzero;

  private List<PositivityRule> builtinRules;
  private final Map<CoreDefinition, Optional<PositivityRule>> ruleCache = Collections.synchronizedMap(new WeakHashMap<>());

  public PositivityMeta(Supplier<? extends ArendRef> selfRef) {
    this.selfRef = selfRef;
  }

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { true };
  }

  @Override
  public int numberOfOptionalExplicitArguments() {
    return 1;
  }

  @Override
  public boolean requireExpectedType() {
    return true;
  }

  private List<ArendRef> getBuiltinRuleRefs() {
    return Arrays.asList(
      zroLessIde, natCoefPos, addPos, addPosLeft, addPosRight, mulPos, powPos, natMulPos, pinvPos, finvPos, joinPosLeft, joinPosRight, meetPos,
      leRefl, natNonneg, intPosNonneg, natCoefNonneg, addNonneg, squareNonneg, mulNonneg, powNonneg, natMulNonneg, finvNonneg, absNonneg, partNonneg, joinNonnegLeft, joinNonnegRight, meetNonneg,
      mulNonzero, finvNonzero);
  }

  synchronized List<PositivityRule> getBuiltinRules(ExpressionTypechecker typechecker) {
    if (builtinRules == null) {
      List<PositivityRule> rules = new ArrayList<>();
      for (ArendRef ref : getBuiltinRuleRefs()) {
        CoreDefinition definition = typechecker.getCoreDefinition(ref);
        PositivityRule rule = definition == null ? null : PositivityRule.make(definition, this);
        if (rule != null) rules.add(rule);
      }
      builtinRules = rules;
    }
    return builtinRules;
  }

  @Nullable PositivityRule getRule(CoreDefinition definition) {
    return ruleCache.computeIfAbsent(definition, def -> Optional.ofNullable(PositivityRule.make(def, this))).orElse(null);
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    CoreExpression expectedType = contextData.getExpectedType();
    ContextHelper helper = new ContextHelper(contextData.getArguments().isEmpty() ? null : contextData.getArguments().getFirst().getExpression());
    List<CoreBinding> bindings = new ArrayList<>(helper.getContextBindings(typechecker));
    bindings.addAll(helper.getAdditionalBindings(typechecker));

    Positivity positivity = new Positivity(typechecker, contextData.getMarker(), this, Positivity.getRules(this, typechecker, typechecker.getAttributedDefinitions(selfRef.get())), bindings);
    Positivity.Goal goal = positivity.makeGoal(expectedType);
    if (goal == null) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The goal should be of the form 0 < e, 0 <= e, or e /= 0", expectedType, contextData.getMarker()));
      return null;
    }

    TypedExpression result = positivity.prove(goal);
    if (result == null) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "positivity failed to prove the goal", expectedType, contextData.getMarker()));
    }
    return result;
  }
}
