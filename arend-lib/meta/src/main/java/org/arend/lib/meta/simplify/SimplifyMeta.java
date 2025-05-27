package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.*;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.StdExtension;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SimplifyMeta extends BaseMetaDefinition {
  private final StdExtension ext;

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
  @Dependency(name = "BaseSet.E")                   CoreClassField carrier;
  @Dependency(name = "AddGroup.negative")           CoreClassField negative;
  @Dependency(name = "Group.inverse")               CoreClassField inverse;
  @Dependency(name = "Semigroup.*")                 CoreClassField mul;
  @Dependency(name = "AddMonoid.+")                 CoreClassField plus;
  @Dependency(name = "Pointed.ide")                 CoreClassField ide;
  @Dependency(name = "AddPointed.zro")              CoreClassField zro;
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

  @Dependency(name = "NatData")                     public ArendRef GroupData;
  @Dependency(name = "CGroupData")                  public ArendRef CGroupData;

  @Dependency(name = "GroupTerm.var")               ArendRef varGTerm;
  @Dependency(name = "GroupTerm.:ide")              ArendRef ideGTerm;
  @Dependency(name = "GroupTerm.:*")                ArendRef mulGTerm;
  @Dependency(name = "GroupTerm.:inv")              ArendRef invGTerm;
  @Dependency(name = "CGroupData.simplify-correct") ArendRef simplifyCorrectAbInv;
  @Dependency(name = "NatData.simplify-correct")    ArendRef simplifyCorrectInv;

  public SimplifyMeta(StdExtension ext) {
    this.ext = ext;
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
  public TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    var refExpr = contextData.getReferenceExpression();
    boolean isForward = contextData.getExpectedType() == null;
    CoreExpression expectedType = contextData.getExpectedType();
    List<? extends ConcreteArgument> args = contextData.getArguments();

    if (isForward && args.isEmpty()) {
      return null;
    }

    ConcreteFactory factory = contextData.getFactory();
    var expression = args.isEmpty() ? factory.ref(typechecker.getPrelude().getIdpRef()) : args.getFirst().getExpression();
    CoreExpression type;

    if (isForward) {
      var checkedExpr = typechecker.typecheck(expression, null);
      type = checkedExpr == null ? null : checkedExpr.getType();
    } else {
      type = expectedType == null ? null : expectedType.getUnderlyingExpression();
    }

    if (type == null) {
      return Utils.typecheckWithAdditionalArguments(expression, typechecker, factory, 0, false);
    }

    var transportedExpr = new Simplifier(ext, this, typechecker, refExpr, factory, typechecker.getErrorReporter()).simplifyTypeOfExpression(expression, type, isForward);
    return transportedExpr == null ? null : typechecker.typecheck(transportedExpr, expectedType);
  }
}
