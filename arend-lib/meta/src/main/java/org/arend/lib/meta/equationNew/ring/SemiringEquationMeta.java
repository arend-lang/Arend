package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.error.equation.RingNFPrettyPrinter;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.monoid.NonCommutativeMonoidEquationMeta;
import org.arend.lib.meta.equationNew.term.*;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SemiringEquationMeta extends BaseEquationMeta<List<Monomial>> {
  @Dependency                                           CoreClassDefinition Semiring;
  @Dependency                                           ArendRef SemiringSolverModel;
  @Dependency(name = "SolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "SolverModel.terms-equality-conv") ArendRef termsEqualityConv;
  @Dependency(name = "AddPointed.zro")                  CoreClassField zro;
  @Dependency(name = "Pointed.ide")                     CoreClassField ide;
  @Dependency(name = "AddMonoid.+")                     CoreClassField add;
  @Dependency(name = "Semigroup.*")                     CoreClassField mul;
  @Dependency(name = "Semiring.natCoef")                CoreClassField natCoef;
  @Dependency(name = "SemiringSolverModel.Term.:zro")   ArendRef zroTerm;
  @Dependency(name = "SemiringSolverModel.Term.:ide")   ArendRef ideTerm;
  @Dependency(name = "SemiringSolverModel.Term.:+")     ArendRef addTerm;
  @Dependency(name = "SemiringSolverModel.Term.:*")     ArendRef mulTerm;
  @Dependency(name = "SemiringSolverModel.Term.coef")   ArendRef coefTerm;
  @Dependency(name = "SemiringSolverModel.Term.var")    ArendRef varTerm;

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return Semiring;
  }

  @Override
  protected @NotNull List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker) {
    return Arrays.asList(
        new TermOperation(zroTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, zro, typechecker, factory, marker, 0), Collections.emptyList()),
        new TermOperation(ideTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, ide, typechecker, factory, marker, 0), Collections.emptyList()),
        new TermOperation(addTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, add, typechecker, factory, marker, 2), Arrays.asList(TermOperation.Type.TERM, TermOperation.Type.TERM)),
        new TermOperation(mulTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, mul, typechecker, factory, marker, 2), Arrays.asList(TermOperation.Type.TERM, TermOperation.Type.TERM)),
        new TermOperation(coefTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, natCoef, typechecker, factory, marker, 1), Collections.singletonList(TermOperation.Type.NAT))
    );
  }

  private void normalize(EquationTerm term, List<Monomial> result) {
    switch (term) {
      case OpTerm(var operation, var arguments) -> {
        if (operation.reflectionRef().equals(ideTerm)) {
          result.add(new Monomial(BigInteger.ONE, Collections.emptyList()));
        } else if (operation.reflectionRef().equals(addTerm)) {
          for (EquationTerm argument : arguments) {
            normalize(argument, result);
          }
        } else if (operation.reflectionRef().equals(mulTerm)) {
          List<Monomial> nf1 = new ArrayList<>(), nf2 = new ArrayList<>();
          normalize(arguments.get(0), nf1);
          normalize(arguments.get(1), nf2);
          List<Monomial> newNF = new ArrayList<>();
          Monomial.multiply(nf1, nf2, newNF);
          Collections.sort(newNF);
          result.addAll(Monomial.collapse(newNF));
        } else if (operation.reflectionRef().equals(coefTerm)) {
          result.add(new Monomial(((NumberTerm) arguments.getFirst()).number(), Collections.emptyList()));
        }
      }
      case VarTerm(int index) -> result.add(new Monomial(BigInteger.ONE, Collections.singletonList(index)));
      default -> throw new IllegalStateException();
    }
  }

  @Override
  protected @NotNull List<Monomial> normalize(EquationTerm term) {
    List<Monomial> result = new ArrayList<>();
    normalize(term, result);
    Collections.sort(result);
    return Monomial.collapse(result);
  }

  @Override
  protected @NotNull NFPrettyPrinter<List<Monomial>> getNFPrettyPrinter() {
    return new RingNFPrettyPrinter();
  }

  private ConcreteExpression monomialToConcrete(Monomial monomial, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    if (monomial.elements().isEmpty()) {
      return factory.app(factory.ref(natCoef.getRef()), true, factory.number(monomial.coefficient()));
    }

    ConcreteExpression result = NonCommutativeMonoidEquationMeta.nfToConcreteTerm(monomial.elements(), values, instance, factory, ide.getRef(), mul.getRef());
    return monomial.coefficient().equals(BigInteger.ONE) ? result : factory.app(factory.ref(mul.getRef()), true, factory.app(factory.ref(natCoef.getRef()), true, factory.number(monomial.coefficient())), result);
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<Monomial> poly, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    if (poly.isEmpty()) return factory.ref(zro.getRef());
    ConcreteExpression result = monomialToConcrete(poly.getLast(), values, instance, factory);
    for (int i = poly.size() - 2; i >= 0; i--) {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(add.getRef()));
      if (instance != null) {
        builder.app(factory.core(instance), false);
      }
      result = builder.app(monomialToConcrete(poly.get(i), values, instance, factory)).app(result).build();
    }
    return result;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return SemiringSolverModel;
  }

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
}
