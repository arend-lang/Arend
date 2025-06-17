package org.arend.lib.meta.equationNew;

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
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.GroupNFPrettyPrinter;
import org.arend.lib.error.equation.NFPrettyPrinter;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.meta.equationNew.term.OpTerm;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.meta.equationNew.term.VarTerm;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseGroupEquationMeta extends BaseEquationMeta<List<Pair<Boolean,Integer>>> {
  @Dependency(name = "GroupSolverModel.Term.var")       ArendRef varTerm;
  @Dependency(name = "GroupSolverModel.Term.:ide")      ArendRef ideTerm;
  @Dependency(name = "GroupSolverModel.Term.:*")        ArendRef mulTerm;
  @Dependency(name = "GroupSolverModel.Term.:inverse")  ArendRef inverseTerm;

  protected abstract boolean isMultiplicative();

  protected abstract CoreClassField getIde();

  protected abstract CoreClassField getMul();

  protected abstract CoreClassField getInverse();

  @Override
  protected @NotNull List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker) {
    return Arrays.asList(
        new TermOperation(ideTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getIde(), typechecker, factory, marker, 0)),
        new TermOperation(mulTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getMul(), typechecker, factory, marker, 2)),
        new TermOperation(inverseTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getInverse(), typechecker, factory, marker, 1))
    );
  }

  private void normalize(EquationTerm term, List<Pair<Boolean,Integer>> result) {
    switch (term) {
      case OpTerm opTerm -> {
        if (opTerm.operation().reflectionRef().equals(inverseTerm)) {
          List<Pair<Boolean,Integer>> args = normalize(opTerm.arguments().getFirst());
          for (int i = args.size() - 1; i >= 0; i--) {
            result.add(new Pair<>(!args.get(i).proj1, args.get(i).proj2));
          }
        } else {
          for (EquationTerm argument : opTerm.arguments()) {
            normalize(argument, result);
          }
        }
      }
      case VarTerm varTerm1 -> result.add(new Pair<>(true, varTerm1.index()));
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

  @Override
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
  }

  private ConcreteExpression pairToConcrete(Pair<Boolean,Integer> pair, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    ConcreteExpression result = factory.core(values.getValue(pair.proj2).computeTyped());
    if (pair.proj1) return result;
    ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getInverse().getRef()));
    if (instance != null) {
      builder.app(factory.core(instance), false);
    }
    return builder.app(result).build();
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcrete(List<Pair<Boolean,Integer>> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
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
