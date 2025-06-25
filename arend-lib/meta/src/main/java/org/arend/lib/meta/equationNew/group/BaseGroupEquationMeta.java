package org.arend.lib.meta.equationNew.group;

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
import org.arend.lib.meta.equation.binop_matcher.DefinitionFunctionMatcher;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.meta.equationNew.term.TermType;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseGroupEquationMeta<NF> extends BaseEquationMeta<NF> {
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
    List<TermType> same2 = Arrays.asList(new TermType.OpType(null), new TermType.OpType(null));
    List<TermOperation> operations = new ArrayList<>(5);
    operations.add(new TermOperation(ideTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getIde(), typechecker, factory, marker, 0), Collections.emptyList()));
    operations.add(new TermOperation(mulTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getMul(), typechecker, factory, marker, 2), same2));
    operations.add(new TermOperation(inverseTerm, FunctionMatcher.makeFieldMatcher(instanceType, instance, getInverse(), typechecker, factory, marker, 1), Collections.singletonList(new TermType.OpType(null))));

    if (isMultiplicative()) {
      return operations;
    }

    if (isIntInstance(instance.getExpression())) {
      TermType natType = new TermType.OpType(Collections.singletonList(new TermOperation(mulTerm, new DefinitionFunctionMatcher(typechecker.getPrelude().getPlus(), 2), same2)));
      operations.add(new TermOperation(POS_TAG, new DefinitionFunctionMatcher(typechecker.getPrelude().getPos(), 1), Collections.singletonList(natType)));
      operations.add(new TermOperation(inverseTerm, new DefinitionFunctionMatcher(typechecker.getPrelude().getNeg(), 1), Collections.singletonList(natType)));
      operations.add(new TermOperation(MINUS_TAG, (factory1, args) -> {
        if (args.size() != 2) throw new IllegalStateException();
        return factory.app(factory1.ref(mulTerm), true, args.get(0), factory1.app(factory.ref(inverseTerm), true, args.get(1)));
      }, new DefinitionFunctionMatcher(typechecker.getPrelude().getMinus(), 2), Arrays.asList(natType, natType)));
    }

    return operations;
  }

  @Override
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
  }

  ConcreteExpression indexToConcrete(boolean isPos, int index, Values<CoreExpression> values, TypedExpression instance, ConcreteFactory factory) {
    ConcreteExpression result = factory.core(values.getValue(index).computeTyped());
    if (isPos) return result;
    ConcreteAppBuilder builder = factory.appBuilder(factory.ref(getInverse().getRef()));
    if (instance != null) {
      builder.app(factory.core(instance), false);
    }
    return builder.app(result).build();
  }
}
