package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.*;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import org.arend.lib.meta.simplify.field.ProofBearingInverses;

public class SimplifyMeta extends BaseMetaDefinition {
  @Dependency                                       ArendRef transport;
  @Dependency(name = "*>")                          ArendRef concat;
  @Dependency                                       ArendRef inv;

  @Dependency                                       CoreClassDefinition Monoid;
  @Dependency                                       CoreClassDefinition AddMonoid;
  @Dependency                                       CoreClassDefinition Semiring;
  @Dependency                                       CoreClassDefinition Ring;
  @Dependency                                       CoreClassDefinition Group;
  @Dependency                                       CoreClassDefinition CGroup;
  @Dependency                                       CoreClassDefinition AddGroup;
  @Dependency                                       CoreClassDefinition AbGroup;
  @Dependency                                       public CoreClassDefinition CRing;
  @Dependency(name = "BaseSet.E")                   public CoreClassField carrier;
  @Dependency(name = "AddGroup.negative")           public CoreClassField negative;
  @Dependency(name = "Group.inverse")               CoreClassField inverse;
  @Dependency(name = "Semigroup.*")                 public CoreClassField mul;
  @Dependency(name = "AddMonoid.+")                 public CoreClassField plus;
  @Dependency(name = "Pointed.ide")                 public CoreClassField ide;
  @Dependency(name = "AddPointed.zro")              public CoreClassField zro;
  @Dependency(name = "AddGroup.negative-isInv")     ArendRef negIsInv;
  @Dependency(name = "Group.inverse-isInv")         ArendRef invIsInv;
  @Dependency(name = "Group.inverse_*")             ArendRef inverseMul;
  @Dependency(name = "AddGroup.negative_+")         ArendRef negativePlus;
  @Dependency(name = "Ring.negative_*-left")        ArendRef negMulLeft;
  @Dependency(name = "Ring.negative_*-right")       ArendRef negMulRight;
  @Dependency(name = "PseudoSemiring.zro_*-right")  ArendRef zeroMulRight;
  @Dependency(name = "PseudoSemiring.zro_*-left")   ArendRef zeroMulLeft;
  @Dependency(name = "AddMonoid.zro-left")          ArendRef addMonZroLeft;
  @Dependency(name = "AddMonoid.zro-right")         ArendRef addMonZroRight;
  @Dependency(name = "Monoid.ide-left")             ArendRef ideLeft;
  @Dependency(name = "Monoid.ide-right")            ArendRef ideRight;
  @Dependency(name = "AddGroup.toGroup")            ArendRef fromAddGroupToGroup;
  @Dependency(name = "AbGroup.toCGroup")            ArendRef fromAbGroupToCGroup;
  @Dependency(name = "AddGroup.negative_zro")       ArendRef negativeZro;
  @Dependency(name = "Group.inverse_ide")           ArendRef invIde;

  @Dependency(name = "Semiring.natCoef")            public CoreClassField natCoef;
  @Dependency(name = "Monoid.Inv")                  public CoreClassDefinition invClass;
  @Dependency(name = "Monoid.DivBase.val")          public CoreClassField invValue;
  @Dependency(name = "Monoid.DivBase.inv")          public CoreClassField invProjection;

  @Dependency(name = "RingSolverModel.Term.var")     public ArendRef ringVar;
  @Dependency(name = "RingSolverModel.Term.coef")    public ArendRef ringCoef;
  @Dependency(name = "RingSolverModel.Term.:zro")    public ArendRef ringZro;
  @Dependency(name = "RingSolverModel.Term.:ide")    public ArendRef ringIde;
  @Dependency(name = "RingSolverModel.Term.:negative") public ArendRef ringNegative;
  @Dependency(name = "RingSolverModel.Term.:+")      public ArendRef ringAdd;
  @Dependency(name = "RingSolverModel.Term.:*")      public ArendRef ringMul;

  @Dependency(name = "FieldSolverModel.InverseOf.inverse-of") public ArendRef inverseOf;
  @Dependency(name = "FieldSolverModel.Witnesses.witnesses-nil") public ArendRef witnessesNil;
  @Dependency(name = "FieldSolverModel.Witnesses.witnesses-cons") public ArendRef witnessesCons;

  @Dependency(name = "FieldSolverModel.terms-equality-raw") ArendRef fieldTermsEqualityRaw;
  @Dependency(name = "FieldSolverModel.terms-equality-raw-conv") ArendRef fieldTermsEqualityRawConv;

  @Dependency(name = "NatData")                     public ArendRef GroupData;
  @Dependency(name = "CGroupData")                  public ArendRef CGroupData;

  @Dependency(name = "GroupTerm.var")               ArendRef varGTerm;
  @Dependency(name = "GroupTerm.:ide")              ArendRef ideGTerm;
  @Dependency(name = "GroupTerm.:*")                ArendRef mulGTerm;
  @Dependency(name = "GroupTerm.:inv")              ArendRef invGTerm;
  @Dependency(name = "CGroupData.simplify-correct") ArendRef simplifyCorrectAbInv;
  @Dependency(name = "NatData.simplify-correct")    ArendRef simplifyCorrectInv;

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { true };
  }

  @Override
  public int numberOfOptionalExplicitArguments() {
    return 1;
  }

  /**
   * @return rules that simplify the type itself; the rules for its subterms are applied afterwards.
   */
  protected @NotNull List<TypeSimplificationRule> createTypeRules(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    return List.of(new FieldEqualityRule(this, new ProofBearingInverses(this, typechecker, contextData), typechecker, contextData));
  }

  @Override
  public TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    var refExpr = contextData.getReferenceExpression();
    boolean isForward = contextData.getExpectedType() == null;
    CoreExpression expectedType = contextData.getExpectedType();
    List<? extends ConcreteArgument> args = contextData.getArguments();
    ConcreteExpression argument = args.isEmpty() || !args.getLast().isExplicit() ? null : args.getLast().getExpression();

    if (isForward && argument == null) {
      return null;
    }

    ConcreteFactory factory = contextData.getFactory();
    var expression = argument == null ? factory.ref(typechecker.getPrelude().getIdpRef()) : argument;
    CoreExpression type;

    if (isForward) {
      var checkedExpr = typechecker.typecheck(expression, null);
      type = checkedExpr == null ? null : checkedExpr.getType();
    } else {
      type = expectedType == null ? null : expectedType.getUnderlyingExpression();
    }

    if (type == null) {
      return Utils.typecheckWithAdditionalArguments(expression, typechecker, 0, false);
    }

    var simplifier = new Simplifier(this, typechecker, refExpr, factory, typechecker.getErrorReporter(), createTypeRules(typechecker, contextData));
    var transportedExpr = simplifier.simplifyTypeOfExpression(expression, type, isForward, !isForward && argument == null);
    return transportedExpr == null ? null : typechecker.typecheck(transportedExpr, expectedType);
  }
}
