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
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.term.*;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BooleanRingEquationMeta extends BaseEquationMeta<List<List<Boolean>>> {
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

  private static boolean getIndex(List<Boolean> monomial, int i) {
    return i < monomial.size() && monomial.get(i);
  }

  private static List<Boolean> multiplyMonomial(List<Boolean> monomial1, List<Boolean> monomial2) {
    int s = Math.max(monomial1.size(), monomial2.size());
    List<Boolean> result = new ArrayList<>(s);
    for (int i = 0; i < s; i++) {
      result.add(getIndex(monomial1, i) || getIndex(monomial2, i));
    }
    return result;
  }

  private static void multiplyPoly(List<List<Boolean>> list1, List<List<Boolean>> list2, List<List<Boolean>> result) {
    if (list2.isEmpty()) return;
    for (List<Boolean> monomial1 : list1) {
      for (List<Boolean> monomial2 : list2) {
        result.add(multiplyMonomial(monomial1, monomial2));
      }
    }
  }

  private void normalize(EquationTerm term, List<List<Boolean>> result) {
    switch (term) {
      case OpTerm(var operation, var arguments) -> {
        Object data = operation.data();
        if (data.equals(addTerm) || data.equals(negativeTerm)) {
          for (EquationTerm argument : arguments) {
            normalize(argument, result);
          }
        } else if (data.equals(mulTerm)) {
          List<List<Boolean>> nf1 = new ArrayList<>(), nf2 = new ArrayList<>();
          normalize(arguments.get(0), nf1);
          normalize(arguments.get(1), nf2);
          multiplyPoly(nf1, nf2, result);
        }
      }
      case VarTerm(int index) -> {
        List<Boolean> monomial = new ArrayList<>();
        while (index > monomial.size()) {
          monomial.add(false);
        }
        monomial.add(true);
        result.add(monomial);
      }
      default -> throw new IllegalStateException();
    }
  }

  private List<List<Boolean>> collapse(List<List<Boolean>> nf) {
    List<List<Boolean>> result = new ArrayList<>();
    for (int i = 0; i < nf.size(); i++) {
      if (i + 1 < nf.size() && nf.get(i).equals(nf.get(i + 1))) {
        i++;
      } else {
        result.add(nf.get(i));
      }
    }
    return result;
  }

  private List<List<Boolean>> normalizeNF(List<List<Boolean>> nf) {
    nf.sort((l1, l2) -> {
      int s = Math.max(l1.size(), l2.size());
      for (int i = 0; i < s; i++) {
        int c = Boolean.compare(getIndex(l1, i), getIndex(l2, i));
        if (c < 0) return 1;
        if (c > 0) return -1;
      }
      return 0;
    });
    return collapse(nf);
  }

  @Override
  protected @NotNull List<List<Boolean>> normalize(EquationTerm term) {
    List<List<Boolean>> result = new ArrayList<>();
    normalize(term, result);
    return normalizeNF(result);
  }

  @Override
  protected @NotNull NFPrettyPrinter<List<List<Boolean>>> getNFPrettyPrinter() {
    return new BooleanRingNFPrettyPrinter();
  }

  private ConcreteExpression monomialToConcrete(List<Boolean> monomial, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    List<Integer> nf = new ArrayList<>();
    for (int i = 0; i < monomial.size(); i++) {
      if (monomial.get(i)) {
        nf.add(i);
      }
    }

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
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<List<Boolean>> poly, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
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
  protected @Nullable Pair<HintResult<List<List<Boolean>>>, HintResult<List<List<Boolean>>>> applyHints(@NotNull List<Hint<List<List<Boolean>>>> hints, @NotNull List<List<Boolean>> left, @NotNull List<List<Boolean>> right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    List<List<Boolean>> newLeft = new ArrayList<>();
    newLeft.addAll(left);
    newLeft.addAll(right);
    return new Pair<>(new HintResult<>(null, normalizeNF(newLeft)), new HintResult<>(null, Collections.emptyList()));
  }
}
