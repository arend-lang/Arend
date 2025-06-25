package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreIntegerExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.error.equation.RingNFPrettyPrinter;
import org.arend.lib.meta.equation.binop_matcher.DefinitionFunctionMatcher;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.monoid.NonCommutativeMonoidEquationMeta;
import org.arend.lib.meta.equationNew.term.*;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseAlgebraEquationMeta extends BaseEquationMeta<List<Monomial>> {
  @Dependency(name = "AddPointed.zro")    CoreClassField zro;
  @Dependency(name = "Pointed.ide")       CoreClassField ide;
  @Dependency(name = "AddMonoid.+")       CoreClassField add;
  @Dependency(name = "Semigroup.*")       CoreClassField mul;
  @Dependency(name = "Semiring.natCoef")  CoreClassField natCoef;
  @Dependency(name = "List.::")           ArendRef cons;
  @Dependency(name = "List.nil")          ArendRef nil;

  protected abstract boolean isCommutative();

  protected abstract @NotNull ArendRef getZroTerm();
  protected abstract @NotNull ArendRef getIdeTerm();
  protected abstract @NotNull ArendRef getAddTerm();
  protected abstract @NotNull ArendRef getMulTerm();
  protected abstract @NotNull ArendRef getCoefTerm();

  protected @Nullable ArendRef getNegativeTerm() {
    return null;
  }

  protected CoreClassField getNegative() {
    return null;
  }

  @Override
  protected @NotNull List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker) {
    List<TermType> same2 = Arrays.asList(new TermType.OpType(null), new TermType.OpType(null));
    List<TermOperation> result = new ArrayList<>();
    result.add(new TermOperation(getZroTerm(), FunctionMatcher.makeFieldMatcher(instanceType, instance, zro, typechecker, factory, marker, 0), Collections.emptyList()));
    result.add(new TermOperation(getIdeTerm(), FunctionMatcher.makeFieldMatcher(instanceType, instance, ide, typechecker, factory, marker, 0), Collections.emptyList()));
    result.add(new TermOperation(getAddTerm(), FunctionMatcher.makeFieldMatcher(instanceType, instance, add, typechecker, factory, marker, 2), same2));
    result.add(new TermOperation(getMulTerm(), FunctionMatcher.makeFieldMatcher(instanceType, instance, mul, typechecker, factory, marker, 2), same2));
    if (getNegativeTerm() != null && getNegative() != null) {
      result.add(new TermOperation(getNegativeTerm(), FunctionMatcher.makeFieldMatcher(instanceType, instance, getNegative(), typechecker, factory, marker, 1), Collections.singletonList(new TermType.OpType(null))));
    }

    if (isIntInstance(instance.getExpression())) {
      TermType natType = new TermType.OpType(Arrays.asList(
          new TermOperation(getAddTerm(), new DefinitionFunctionMatcher(typechecker.getPrelude().getPlus(), 2), same2),
          new TermOperation(getMulTerm(), new DefinitionFunctionMatcher(typechecker.getPrelude().getMul(), 2), same2),
          new TermOperation(SUC_TAG, (factory1, args) -> {
            if (args.size() != 1) throw new IllegalStateException();
            return factory.app(factory1.ref(getAddTerm()), true, args.getFirst(), factory1.app(factory.ref(getCoefTerm()), true, factory.number(1)));
          }, new DefinitionFunctionMatcher(typechecker.getPrelude().getSuc(), 1), Collections.singletonList(new TermType.OpType(null))),
          new TermOperation(getCoefTerm(), expr -> expr instanceof CoreIntegerExpression ? Collections.singletonList(expr) : null, Collections.singletonList(new TermType.NatType()))
      ));

      result.add(new TermOperation(POS_TAG, new DefinitionFunctionMatcher(typechecker.getPrelude().getPos(), 1), Collections.singletonList(natType)));
      if (getNegativeTerm() != null) {
        result.add(new TermOperation(getNegativeTerm(), new DefinitionFunctionMatcher(typechecker.getPrelude().getNeg(), 1), Collections.singletonList(natType)));
        result.add(new TermOperation(MINUS_TAG, (factory1, args) -> {
          if (args.size() != 2) throw new IllegalStateException();
          return factory.app(factory1.ref(getAddTerm()), true, args.get(0), factory1.app(factory.ref(getNegativeTerm()), true, args.get(1)));
        }, new DefinitionFunctionMatcher(typechecker.getPrelude().getMinus(), 2), Arrays.asList(natType, natType)));
      }
    } else {
      result.add(new TermOperation(getCoefTerm(), FunctionMatcher.makeFieldMatcher(instanceType, instance, natCoef, typechecker, factory, marker, 1), Collections.singletonList(new TermType.NatType())));
    }

    return result;
  }

  private void normalize(EquationTerm term, List<Monomial> result) {
    switch (term) {
      case OpTerm(var operation, var arguments) -> {
        Object data = operation.data();
        if (data.equals(getIdeTerm())) {
          result.add(new Monomial(BigInteger.ONE, Collections.emptyList()));
        } else if (data.equals(getAddTerm()) || data.equals(POS_TAG)) {
          for (EquationTerm argument : arguments) {
            normalize(argument, result);
          }
        } else if (data.equals(SUC_TAG)) {
          for (EquationTerm argument : arguments) {
            normalize(argument, result);
          }
          result.add(new Monomial(BigInteger.ONE, Collections.emptyList()));
        } else if (data.equals(getMulTerm())) {
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
        } else if (data.equals(getNegativeTerm())) {
          List<Monomial> nf = new ArrayList<>();
          normalize(arguments.getFirst(), nf);
          for (Monomial monomial : nf) {
            result.add(monomial.negate());
          }
        } else if (data.equals(MINUS_TAG)) {
          normalize(arguments.get(0), result);
          List<Monomial> nf = new ArrayList<>();
          normalize(arguments.get(1), nf);
          for (Monomial monomial : nf) {
            result.add(monomial.negate());
          }
        } else if (data.equals(getCoefTerm())) {
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

  private ConcreteExpression numberToConcrete(BigInteger number, ConcreteFactory factory) {
    BigInteger pos = number.abs();
    ConcreteExpression result = pos.equals(BigInteger.ONE) ? factory.ref(ide.getRef()) : factory.app(factory.ref(natCoef.getRef()), true, factory.number(pos));
    return number.signum() < 0 ? factory.app(factory.ref(getNegative().getRef()), true, result) : result;
  }

  private ConcreteExpression monomialToConcrete(Monomial monomial, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    if (monomial.elements().isEmpty()) {
      return numberToConcrete(monomial.coefficient(), factory);
    }

    ConcreteExpression result = NonCommutativeMonoidEquationMeta.nfToConcreteTerm(monomial.elements(), values, instance, factory, ide.getRef(), mul.getRef());
    return monomial.coefficient().equals(BigInteger.ONE) ? result : factory.app(factory.ref(mul.getRef()), true, numberToConcrete(monomial.coefficient(), factory), result);
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
}
