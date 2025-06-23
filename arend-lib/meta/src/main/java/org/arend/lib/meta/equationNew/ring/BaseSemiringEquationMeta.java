package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
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
import org.arend.lib.meta.equationNew.monoid.BaseCommutativeMonoidEquationMeta;
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

public abstract class BaseSemiringEquationMeta extends BaseEquationMeta<List<Monomial>> {
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
  @Dependency(name = "List.::")                         ArendRef cons;
  @Dependency(name = "List.nil")                        ArendRef nil;

  protected abstract boolean isCommutative();

  protected abstract @NotNull ArendRef getApplyAxiom();

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
          if (isCommutative()) {
            Monomial.multiplyComm(nf1, nf2, newNF);
          } else {
            Monomial.multiply(nf1, nf2, newNF);
          }
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

  protected static List<Monomial> addNF(List<Monomial> poly1, List<Monomial> poly2) {
    List<Monomial> result = new ArrayList<>(poly1.size() + poly2.size());
    result.addAll(poly1);
    result.addAll(poly2);
    return result;
  }

  protected static List<Monomial> mulCoefNF(int coef, List<Monomial> poly) {
    if (coef == 1) return poly;
    if (coef == 0) return Collections.emptyList();
    List<Monomial> result = new ArrayList<>();
    for (Monomial monomial : poly) {
      result.add(new Monomial(monomial.coefficient().multiply(BigInteger.valueOf(coef)), monomial.elements()));
    }
    return result;
  }

  protected static List<Monomial> normalizeNF(List<Monomial> poly) {
    Collections.sort(poly);
    return Monomial.collapse(poly);
  }

  @Override
  protected @NotNull List<Monomial> normalize(EquationTerm term) {
    List<Monomial> result = new ArrayList<>();
    normalize(term, result);
    return normalizeNF(result);
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

  private ConcreteExpression monomialToConcrete(Monomial monomial, ConcreteFactory factory) {
    ConcreteExpression result = factory.ref(nil);
    for (int i = monomial.elements().size() - 1; i >= 0; i--) {
      result = factory.app(factory.ref(cons), true, factory.number(monomial.elements().get(i)), result);
    }
    return factory.tuple(result, factory.number(monomial.coefficient()));
  }

  protected ConcreteExpression nfToConcrete(List<Monomial> nf, ConcreteFactory factory) {
    ConcreteExpression result = factory.ref(nil);
    for (int i = nf.size() - 1; i >= 0; i--) {
      result = factory.app(factory.ref(cons), true, monomialToConcrete(nf.get(i), factory), result);
    }
    return result;
  }

  @Override
  protected @Nullable Hint<List<Monomial>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    return BaseCommutativeMonoidEquationMeta.parseHint(hint, hintType, operations, values, typechecker, this);
  }

  protected record AbstractNFResult(List<Monomial> leftMultiplier, List<Monomial> rightMultiplier, List<Monomial> addition, List<Monomial> newNF) {}

  // This implementation applies a hint without multipliers
  protected @Nullable AbstractNFResult abstractNF(@NotNull BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> hint, @NotNull List<Monomial> nf) {
    List<Monomial> addition = new ArrayList<>(nf);
    for (Monomial monomial : hint.leftNF) {
      if (!addition.remove(monomial.multiply(hint.count))) {
        return null;
      }
    }

    return new AbstractNFResult(
        Collections.singletonList(new Monomial(BigInteger.valueOf(hint.count), Collections.emptyList())),
        Collections.singletonList(new Monomial(BigInteger.ONE, Collections.emptyList())),
        addition,
        normalizeNF(addNF(addition, mulCoefNF(hint.count, hint.rightNF))));
  }

  @Override
  protected @Nullable BaseEquationMeta.HintResult<List<Monomial>> applyHint(@NotNull BaseEquationMeta.Hint<List<Monomial>> hint, @NotNull List<Monomial> current, int[] position, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull ConcreteFactory factory) {
    position[0]++;
    BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>> myHint = (BaseCommutativeMonoidEquationMeta.MyHint<List<Monomial>>) hint;
    if (position[0] == 1 && !myHint.applyToLeft || position[0] == 2 && !myHint.applyToRight) {
      return null;
    }
    if (myHint.count == 1 && hint.leftNF.equals(current)) {
      return super.applyHint(hint, current, position, solverRef, envRef, factory);
    }

    AbstractNFResult result = abstractNF(myHint, current);
    if (result == null) return null;
    ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getApplyAxiom()))
        .app(factory.ref(envRef.get()))
        .app(hint.left.generateReflectedTerm(factory, getVarTerm()))
        .app(hint.right.generateReflectedTerm(factory, getVarTerm()))
        .app(factory.core(hint.typed));
    if (result.leftMultiplier != null) builder.app(nfToConcrete(result.leftMultiplier, factory));
    if (result.rightMultiplier != null) builder.app(nfToConcrete(result.rightMultiplier, factory));
    if (result.addition != null) builder.app(nfToConcrete(result.addition, factory));
    return new BaseEquationMeta.HintResult<>(builder.build(), result.newNF);
  }
}
