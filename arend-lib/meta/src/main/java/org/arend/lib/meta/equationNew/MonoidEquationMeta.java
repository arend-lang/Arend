package org.arend.lib.meta.equationNew;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreFunCallExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.instance.SubclassSearchParameters;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.error.EquationError;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MonoidEquationMeta extends BaseMetaDefinition {
  @Dependency                                       ArendRef MonoidSolverModel;
  @Dependency                                       CoreClassDefinition Monoid;
  @Dependency(name = "BaseSet.E")                   CoreClassField carrier;
  @Dependency(name = "Pointed.ide")                 CoreClassField ide;
  @Dependency(name = "Semigroup.*")                 CoreClassField mul;
  @Dependency(name = "MonoidSolverModel.Term.var")  ArendRef varTerm;
  @Dependency(name = "MonoidSolverModel.Term.:ide") ArendRef ideTerm;
  @Dependency(name = "MonoidSolverModel.Term.:*")   ArendRef mulTerm;
  @Dependency(name = "SolverModel.terms-equality")  ArendRef termsEquality;

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

  private List<Integer> normalize(EquationTerm term) {
    List<Integer> result = new ArrayList<>();
    normalize(term, result);
    return result;
  }

  private void normalize(EquationTerm term, List<Integer> result) {
    switch (term) {
      case OpTerm opTerm -> {
        for (EquationTerm argument : opTerm.arguments()) {
          normalize(argument, result);
        }
      }
      case VarTerm varTerm1 -> result.add(varTerm1.index());
    }
  }

  private ConcreteExpression nfToConcrete(List<Integer> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    if (nf.isEmpty()) return factory.ref(ide.getRef());
    ConcreteExpression result = factory.core(values.getValue(nf.getLast()).computeTyped());
    for (int i = nf.size() - 2; i >= 0; i--) {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(mul.getRef()));
      if (instance != null) {
        builder.app(factory.core(instance), false);
      }
      result = builder.app(factory.core(values.getValue(nf.get(i)).computeTyped())).app(result).build();
    }
    return result;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ConcreteExpression marker = contextData.getMarker();
    CoreFunCallExpression equality = Utils.toEquality(contextData.getExpectedType().normalize(NormalizationMode.WHNF), typechecker.getErrorReporter(), marker);
    if (equality == null) {
      return null;
    }

    Pair<TypedExpression, CoreClassCallExpression> instance = Utils.findInstanceWithClassCall(new SubclassSearchParameters(Monoid), carrier, equality.getDefCallArguments().getFirst(), typechecker, marker, Monoid);
    if (instance == null) {
      return null;
    }

    Values<CoreExpression> values = new Values<>(typechecker,  marker);
    ConcreteFactory factory = contextData.getFactory();

    List<TermOperation> operations = Arrays.asList(
        new TermOperation(ideTerm, FunctionMatcher.makeFieldMatcher(instance.proj2, instance.proj1, ide, typechecker, factory, marker, 0)),
        new TermOperation(mulTerm, FunctionMatcher.makeFieldMatcher(instance.proj2, instance.proj1, mul, typechecker, factory, marker, 2))
    );
    EquationTerm left = EquationTerm.match(equality.getDefCallArguments().get(1), operations, values);
    EquationTerm right = EquationTerm.match(equality.getDefCallArguments().get(2), operations, values);
    List<Integer> leftNF = normalize(left);
    List<Integer> rightNF = normalize(right);

    ConcreteExpression proof;
    if (leftNF.equals(rightNF)) {
      proof = factory.ref(typechecker.getPrelude().getIdpRef());
      if (!contextData.getArguments().isEmpty()) {
        typechecker.getErrorReporter().report(new TypecheckingError(GeneralError.Level.WARNING_UNUSED, "Argument is ignored", contextData.getArguments().getFirst().getExpression()));
      }
    } else {
      if (contextData.getArguments().isEmpty()) {
        typechecker.getErrorReporter().report(new EquationError(leftNF, rightNF, values.getValues(), marker));
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

    return typechecker.typecheck(factory.appBuilder(factory.ref(termsEquality))
        .app(factory.app(factory.ref(MonoidSolverModel), true, factory.core(instance.proj1)), false)
        .app(Utils.makeArray(values.getValues().stream().map(it -> factory.core(it.computeTyped())).toList(), factory, typechecker.getPrelude()))
        .app(left.generateReflectedTerm(factory, varTerm))
        .app(right.generateReflectedTerm(factory, varTerm))
        .app(proof)
        .build(), null);
  }
}
