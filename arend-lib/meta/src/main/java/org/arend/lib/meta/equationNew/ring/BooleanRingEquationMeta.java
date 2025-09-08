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
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.BooleanRingNFPrettyPrinter;
import org.arend.lib.error.equation.EquationFindError;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.group.BaseCommutativeGroupEquationMeta;
import org.arend.lib.meta.equationNew.term.*;
import org.arend.lib.ring.BooleanMonomial;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BooleanRingEquationMeta extends BaseEquationMeta<List<BooleanMonomial>> {
  @Dependency                                                       CoreClassDefinition BooleanRing;
  @Dependency(name = "AddPointed.zro")                              CoreClassField zro;
  @Dependency(name = "AddMonoid.+")                                 CoreClassField add;
  @Dependency(name = "Semigroup.*")                                 CoreClassField mul;
  @Dependency(name = "AddGroup.negative")                           CoreClassField negative;

  @Dependency(name = "BooleanRingSolverModel.Term.:zro")            ArendRef zroTerm;
  @Dependency(name = "BooleanRingSolverModel.Term.:+")              ArendRef addTerm;
  @Dependency(name = "BooleanRingSolverModel.Term.:*")              ArendRef mulTerm;
  @Dependency(name = "BooleanRingSolverModel.Term.:negative")       ArendRef negativeTerm;
  @Dependency(name = "BooleanRingSolverModel.Term.var")             ArendRef varTerm;

  @Dependency                                                       ArendRef BooleanRingSolverModel;
  @Dependency(name = "BooleanRingSolverModel.terms-equality")       ArendRef termsEquality;
  @Dependency(name = "BooleanRingSolverModel.terms-equality-conv")  ArendRef termsEqualityConv;
  @Dependency(name = "BooleanRingSolverModel.apply-axioms")         ArendRef applyAxioms;

  @Dependency(name = "List.::")                                     ArendRef cons;
  @Dependency(name = "List.nil")                                    ArendRef nil;
  @Dependency(name = "true")                                        ArendRef true_;
  @Dependency(name = "false")                                       ArendRef false_;

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return BooleanRing;
  }

  @Override
  protected @NotNull List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker) {
    List<TermType> same2 = Arrays.asList(new TermType.OpType(null), new TermType.OpType(null));
    return Arrays.asList(
        new TermOperation(zroTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, zro, typechecker, factory, marker, 0), Collections.emptyList()),
        new TermOperation(addTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, add, typechecker, factory, marker, 2), same2),
        new TermOperation(mulTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, mul, typechecker, factory, marker, 2), same2),
        new TermOperation(negativeTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, negative, typechecker, factory, marker, 1), Collections.singletonList(new TermType.OpType(null)))
    );
  }

  private void normalize(EquationTerm term, List<BooleanMonomial> result) {
    switch (term) {
      case OpTerm(var operation, var arguments) -> {
        Object data = operation.data();
        if (data.equals(addTerm) || data.equals(negativeTerm)) {
          for (EquationTerm argument : arguments) {
            normalize(argument, result);
          }
        } else if (data.equals(mulTerm)) {
          List<BooleanMonomial> nf1 = new ArrayList<>(), nf2 = new ArrayList<>();
          normalize(arguments.get(0), nf1);
          normalize(arguments.get(1), nf2);
          BooleanMonomial.multiplyPoly(nf1, nf2, result);
        }
      }
      case VarTerm(int index) -> result.add(BooleanMonomial.singleton(index));
      default -> throw new IllegalStateException();
    }
  }

  private static List<BooleanMonomial> normalizeNF(List<BooleanMonomial> nf) {
    Collections.sort(nf);
    return BooleanMonomial.collapse(nf);
  }

  @Override
  protected @NotNull List<BooleanMonomial> normalize(EquationTerm term) {
    List<BooleanMonomial> result = new ArrayList<>();
    normalize(term, result);
    return normalizeNF(result);
  }

  @Override
  protected @NotNull NFPrettyPrinter<List<BooleanMonomial>> getNFPrettyPrinter() {
    return new BooleanRingNFPrettyPrinter();
  }

  private ConcreteExpression monomialToConcrete(BooleanMonomial monomial, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    List<Integer> nf = new ArrayList<>();
    monomial.getIndices(nf);
    if (nf.isEmpty()) return factory.ref(zro.getRef());
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
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<BooleanMonomial> poly, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
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
    return BooleanRingSolverModel;
  }

  @Override
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEquality(@NotNull Lazy<ArendRef> solverRef, @Nullable ConcreteExpression solver, @NotNull ConcreteFactory factory) {
    return factory.ref(termsEquality);
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEqualityConv(@NotNull Lazy<ArendRef> solverRef, @NotNull ConcreteFactory factory) {
    return factory.ref(termsEqualityConv);
  }

  @Override
  protected boolean isIntInstance(CoreExpression instance) {
    return false;
  }

  @Override
  protected @Nullable BaseCommutativeGroupEquationMeta.MyHint<List<BooleanMonomial>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    return BaseCommutativeGroupEquationMeta.parseCoefHint(hint, hintType, operations, values, typechecker, this);
  }

  private Integer getHintCoefficient(Hint<List<BooleanMonomial>> hint) {
    return ((BaseCommutativeGroupEquationMeta.MyHint<List<BooleanMonomial>>) hint).coefficient;
  }

  private ConcreteExpression monomialToConcrete(BooleanMonomial monomial, int size, ConcreteFactory factory) {
    List<ConcreteExpression> concreteList = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      concreteList.add(factory.ref(monomial.getElement(i) ? true_ : false_));
    }
    return Utils.makeArray(concreteList, factory);
  }

  protected ConcreteExpression nfToConcrete(List<BooleanMonomial> nf, int size, ConcreteFactory factory) {
    ConcreteExpression result = factory.ref(nil);
    for (int i = nf.size() - 1; i >= 0; i--) {
      result = factory.app(factory.ref(cons), true, monomialToConcrete(nf.get(i), size, factory), result);
    }
    return result;
  }

  protected ConcreteExpression getConcreteAxiom(List<BooleanMonomial> factor, Hint<List<BooleanMonomial>> hint, int size, ConcreteFactory factory) {
    return factory.tuple(
        nfToConcrete(factor, size, factory),
        hint.left.generateReflectedTerm(factory, getVarTerm()),
        hint.right.generateReflectedTerm(factory, getVarTerm()),
        factory.core(hint.typed));
  }

  protected ConcreteExpression applyHint(Hint<List<BooleanMonomial>> hint, List<BooleanMonomial> newNF, int size, ConcreteFactory factory) {
    newNF.addAll(hint.leftNF);
    newNF.addAll(hint.rightNF);
    return getConcreteAxiom(Collections.singletonList(new BooleanMonomial(Collections.emptyList())), hint, size, factory);
  }

  @Override
  protected @Nullable Pair<HintResult<List<BooleanMonomial>>, HintResult<List<BooleanMonomial>>> applyHints(@NotNull List<Hint<List<BooleanMonomial>>> hints, @NotNull List<BooleanMonomial> left, @NotNull List<BooleanMonomial> right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    List<BooleanMonomial> newNF = new ArrayList<>();
    newNF.addAll(left);
    newNF.addAll(right);

    List<ConcreteExpression> axioms = new ArrayList<>();
    for (Hint<List<BooleanMonomial>> hint : hints) {
      Integer c = getHintCoefficient(hint);
      if (c == null && !hint.leftNF.isEmpty()) {
        newNF = normalizeNF(newNF);
        var divRem = BooleanMonomial.divideAndRemainder(newNF, hint.leftNF);
        if (divRem.proj1.isEmpty()) {
          typechecker.getErrorReporter().report(new EquationFindError<>(getNFPrettyPrinter(), newNF, Collections.emptyList(), hint.leftNF, values.getValues(), hint.originalExpression));
          return null;
        }
        axioms.add(getConcreteAxiom(divRem.proj1, hint, values.getValues().size(), factory));
        newNF = new ArrayList<>();
        BooleanMonomial.multiplyPoly(hint.rightNF, divRem.proj1, newNF);
        newNF.addAll(divRem.proj2);
      } else {
        axioms.add(applyHint(hint, newNF, values.getValues().size(), factory));
      }
    }

    newNF = normalizeNF(newNF);
    return new Pair<>(new BaseEquationMeta.HintResult<>(factory.app(factory.ref(applyAxioms), true, factory.ref(envRef.get()), Utils.makeArray(axioms, factory), nfToConcrete(newNF, values.getValues().size(), factory)), newNF), new BaseEquationMeta.HintResult<>(null, Collections.emptyList()));
  }
}
