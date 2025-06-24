package org.arend.lib.meta.equationNew.term;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;

import java.util.List;
import java.util.function.BiFunction;

public record TermOperation(Object data, BiFunction<ConcreteFactory, List<ConcreteExpression>, ConcreteExpression> generator, FunctionMatcher matcher, List<TermType> argTypes) {
  public TermOperation(ArendRef reflectionRef, FunctionMatcher matcher, List<TermType> argTypes) {
    this(reflectionRef, (factory, args) -> factory.app(factory.ref(reflectionRef), true, args), matcher, argTypes);
  }

  public TermOperation(FunctionMatcher matcher, List<TermType> argTypes) {
    this(null, (factory, args) -> {
      if (args.size() != 1) throw new IllegalStateException();
      return args.getFirst();
    }, matcher, argTypes);
  }
}
