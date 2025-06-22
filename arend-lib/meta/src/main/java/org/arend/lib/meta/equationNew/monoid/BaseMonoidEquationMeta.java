package org.arend.lib.meta.equationNew.monoid;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.util.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseMonoidEquationMeta<NF> extends BaseEquationMeta<NF> {
  @Dependency(name = "MonoidSolverModel.Term.var")  ArendRef varTerm;
  @Dependency(name = "MonoidSolverModel.Term.:ide") ArendRef ideTerm;
  @Dependency(name = "MonoidSolverModel.Term.:*")   ArendRef mulTerm;
  @Dependency(name = "SolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "SolverModel.terms-equality-conv") ArendRef termsEqualityConv;

  protected abstract boolean isMultiplicative();

  protected abstract CoreClassField getIde();

  protected abstract CoreClassField getMul();

  @Override
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEquality(@NotNull Lazy<ArendRef> solverRef, @Nullable ConcreteExpression solver, @NotNull ConcreteFactory factory) {
    return factory.app(factory.ref(termsEquality), false, solver == null || solverRef.isUsed() ? factory.ref(solverRef.get()) : solver);
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEqualityConv(@NotNull Lazy<ArendRef> solverRef, @NotNull ConcreteFactory factory) {
    return factory.app(factory.ref(termsEqualityConv), false, factory.ref(solverRef.get()));
  }

  @Override
  protected @NotNull List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker) {
    return Arrays.asList(
        new TermOperation(ideTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getIde(), typechecker, factory, marker, 0), Collections.emptyList()),
        new TermOperation(mulTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getMul(), typechecker, factory, marker, 2), Arrays.asList(TermOperation.Type.TERM, TermOperation.Type.TERM))
    );
  }
}
