package org.arend.lib.meta.equationNew.group;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.GroupNFPrettyPrinter;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseNonCommutativeGroupEquationMeta extends BaseGroupEquationMeta<List<Pair<Boolean,Integer>>> {
  @Dependency(name = "SolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "SolverModel.terms-equality-conv") ArendRef termsEqualityConv;

  @Override
  protected @NotNull ConcreteExpression getTermsEquality(@NotNull Lazy<ArendRef> solverRef, @Nullable ConcreteExpression solver, @NotNull ConcreteFactory factory) {
    return factory.app(factory.ref(termsEquality), false, solver == null || solverRef.isUsed() ? factory.ref(solverRef.get()) : solver);
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEqualityConv(@NotNull Lazy<ArendRef> solverRef, @NotNull ConcreteFactory factory) {
    return factory.app(factory.ref(termsEqualityConv), false, factory.ref(solverRef.get()));
  }

  private void normalize(EquationTerm term, List<Pair<Boolean,Integer>> result) {
    switch (term) {
      case OpTerm(var operation, var arguments) -> {
        if (operation.reflectionRef().equals(inverseTerm)) {
          List<Pair<Boolean,Integer>> args = normalize(arguments.getFirst());
          for (int i = args.size() - 1; i >= 0; i--) {
            result.add(new Pair<>(!args.get(i).proj1, args.get(i).proj2));
          }
        } else {
          for (EquationTerm argument : arguments) {
            normalize(argument, result);
          }
        }
      }
      case VarTerm(int index) -> result.add(new Pair<>(true, index));
      default -> throw new IllegalStateException();
    }
  }

  @Override
  protected @NotNull List<Pair<Boolean,Integer>> normalize(EquationTerm term) {
    List<Pair<Boolean,Integer>> list = new ArrayList<>();
    normalize(term, list);
    if (list.isEmpty()) return list;

    List<Pair<Boolean,Integer>> result = new ArrayList<>();
    result.add(list.getFirst());
    for (int i = 1; i < list.size(); i++) {
      var curr = list.get(i);
      if (result.isEmpty()) {
        result.add(curr);
      } else {
        var last = result.getLast();
        if (last.proj2.equals(curr.proj2) && !last.proj1.equals(curr.proj1)) {
          result.removeLast();
        } else {
          result.add(curr);
        }
      }
    }
    return result;
  }

  private ConcreteExpression pairToConcrete(Pair<Boolean,Integer> pair, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    return indexToConcrete(pair.proj1, pair.proj2, values, instance, factory);
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcreteTerm(List<Pair<Boolean,Integer>> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    if (nf.isEmpty()) return factory.ref(getIde().getRef());
    ConcreteExpression result = pairToConcrete(nf.getLast(), values, instance, factory);
    for (int i = nf.size() - 2; i >= 0; i--) {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getMul().getRef()));
      if (instance != null) {
        builder.app(factory.core(instance), false);
      }
      result = builder.app(pairToConcrete(nf.get(i), values, instance, factory)).app(result).build();
    }
    return result;
  }

  @Override
  protected @NotNull NFPrettyPrinter<List<Pair<Boolean, Integer>>> getNFPrettyPrinter() {
    return new GroupNFPrettyPrinter(isMultiplicative());
  }
}
