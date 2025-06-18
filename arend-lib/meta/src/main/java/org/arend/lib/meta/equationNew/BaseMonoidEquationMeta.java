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
import org.arend.lib.error.equation.MonoidNFPrettyPrinter;
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
import java.util.Collections;
import java.util.List;

public abstract class BaseMonoidEquationMeta extends BaseEquationMeta<List<Integer>> {
  @Dependency(name = "MonoidSolverModel.Term.var")      ArendRef varTerm;
  @Dependency(name = "MonoidSolverModel.Term.:ide")     ArendRef ideTerm;
  @Dependency(name = "MonoidSolverModel.Term.:*")       ArendRef mulTerm;

  private final boolean isCommutative;

  protected BaseMonoidEquationMeta(boolean isCommutative) {
    this.isCommutative = isCommutative;
  }

  protected abstract CoreClassField getIde();

  protected abstract CoreClassField getMul();

  @Override
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
  }

  @Override
  protected @NotNull List<TermOperation> getOperations(TypedExpression instance, CoreClassCallExpression instanceType, ExpressionTypechecker typechecker, ConcreteFactory factory, ConcreteExpression marker) {
    return Arrays.asList(
        new TermOperation(ideTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getIde(), typechecker, factory, marker, 0)),
        new TermOperation(mulTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getMul(), typechecker, factory, marker, 2))
    );
  }

  @Override
  protected @NotNull List<Integer> normalize(EquationTerm term) {
    List<Integer> result = new ArrayList<>();
    normalize(term, result);
    if (isCommutative) {
      Collections.sort(result);
    }
    return result;
  }

  private void normalize(EquationTerm term, List<Integer> result) {
    switch (term) {
      case OpTerm opTerm -> {
        for (EquationTerm argument : opTerm.arguments()) {
          normalize(argument, result);
        }
      }
      case VarTerm(int index) -> result.add(index);
    }
  }

  @Override
  protected @NotNull ConcreteExpression nfToConcrete(List<Integer> nf, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    if (nf.isEmpty()) return factory.ref(getIde().getRef());
    ConcreteExpression result = factory.core(values.getValue(nf.getLast()).computeTyped());
    for (int i = nf.size() - 2; i >= 0; i--) {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getMul().getRef()));
      if (instance != null) {
        builder.app(factory.core(instance), false);
      }
      result = builder.app(factory.core(values.getValue(nf.get(i)).computeTyped())).app(result).build();
    }
    return result;
  }

  @Override
  protected @NotNull NFPrettyPrinter<List<Integer>> getNFPrettyPrinter() {
    return new MonoidNFPrettyPrinter(isMultiplicative());
  }
}
