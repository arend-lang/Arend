package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.MissingArgumentsError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.instance.SubclassSearchParameters;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseEquationMeta<NF> extends BaseMetaDefinition {
  @Dependency(name = "BaseSet.E")                       CoreClassField carrier;
  @Dependency(name = "SolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "SolverModel.terms-equality-conv") ArendRef termsEqualityConv;

  protected abstract CoreClassDefinition getClassDef();

  protected abstract List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker);

  protected abstract NF normalize(EquationTerm term);

  protected abstract TypecheckingError getError(NF nf1, NF nf2, List<CoreExpression> values, ConcreteExpression marker);

  protected abstract ConcreteExpression nfToConcrete(NF nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory);

  protected abstract ArendRef getSolverModel();

  protected abstract ArendRef getVarTerm();

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ConcreteExpression marker = contextData.getMarker();
    CoreFunCallExpression equality;
    boolean isForward = contextData.getExpectedType() == null;
    TypedExpression argTyped;

    if (isForward) {
      if (contextData.getArguments().isEmpty()) {
        typechecker.getErrorReporter().report(new MissingArgumentsError(1, marker));
        return null;
      }

      argTyped = typechecker.typecheck(contextData.getArguments().getFirst().getExpression(), null);
      if (argTyped == null) {
        return null;
      }

      equality = Utils.toEquality(argTyped.getType().normalize(NormalizationMode.WHNF), typechecker.getErrorReporter(), marker);
    } else {
      argTyped = null;
      equality = Utils.toEquality(contextData.getExpectedType().normalize(NormalizationMode.WHNF), typechecker.getErrorReporter(), marker);
    }

    if (equality == null) {
      return null;
    }

    Pair<TypedExpression, CoreClassCallExpression> instance = Utils.findInstanceWithClassCall(new SubclassSearchParameters(getClassDef()), carrier, equality.getDefCallArguments().getFirst(), typechecker, marker, getClassDef());
    if (instance == null) {
      return null;
    }

    Values<CoreExpression> values = new Values<>(typechecker,  marker);
    ConcreteFactory factory = contextData.getFactory();

    List<TermOperation> operations = getOperations(instance.proj1, instance.proj2, typechecker, factory, marker);
    EquationTerm left = EquationTerm.match(equality.getDefCallArguments().get(1), operations, values);
    EquationTerm right = EquationTerm.match(equality.getDefCallArguments().get(2), operations, values);
    NF leftNF = normalize(left);
    NF rightNF = normalize(right);

    ConcreteExpression proof;
    if (argTyped != null) {
      proof = factory.core(argTyped);
    } else if (leftNF.equals(rightNF)) {
      proof = factory.ref(typechecker.getPrelude().getIdpRef());
      if (!contextData.getArguments().isEmpty()) {
        typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is ignored", contextData.getArguments().getFirst().getExpression()));
      }
    } else {
      if (contextData.getArguments().isEmpty()) {
        typechecker.getErrorReporter().report(getError(leftNF, rightNF, values.getValues(), marker));
        return null;
      }

      TypedExpression type = typechecker.typecheckType(factory.app(factory.ref(typechecker.getPrelude().getEqualityRef()), true, nfToConcrete(leftNF, values, instance.proj1, factory), nfToConcrete(rightNF, values, instance.proj1, factory)));
      if (type == null) {
        return null;
      }

      TypedExpression proofCore = typechecker.typecheck(contextData.getArguments().getFirst().getExpression(), type.getExpression());
      if (proofCore == null) {
        return null;
      }

      proof = factory.core(proofCore);
    }

    return typechecker.typecheck(factory.appBuilder(factory.ref(isForward ? termsEqualityConv : termsEquality))
        .app(factory.app(factory.ref(getSolverModel()), true, factory.core(instance.proj1)), false)
        .app(Utils.makeArray(values.getValues().stream().map(it -> factory.core(it.computeTyped())).toList(), factory, typechecker.getPrelude()))
        .app(left.generateReflectedTerm(factory, getVarTerm()))
        .app(right.generateReflectedTerm(factory, getVarTerm()))
        .app(proof)
        .build(), null);
  }
}
