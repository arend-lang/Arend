package org.arend.lib.meta;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.meta.util.MetaInvocationMeta;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class RepeatMeta extends MetaInvocationMeta {
  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { false, true, true };
  }

  @Override
  protected boolean keepMetaArgument() {
    return true;
  }

  @Override
  protected boolean allowNonMeta() {
    return false;
  }

  private ConcreteExpression computeConcrete(int steps, List<? extends ConcreteArgument> args, ConcreteFactory factory) {
    ConcreteExpression result = args.get(1).getExpression();
    for (int i = 0; i < steps; i++) {
      result = factory.app(args.getFirst().getExpression(), true, Collections.singletonList(result));
    }
    return factory.app(result, args.subList(2, args.size()));
  }

  @Override
  public TypedExpression invokeMeta(MetaDefinition meta, List<ConcreteExpression> implicitArguments, ExpressionTypechecker typechecker, ContextData contextData) {
    ErrorReporter errorReporter = typechecker.getErrorReporter();
    List<? extends ConcreteArgument> args = contextData.getArguments();
    ConcreteFactory factory = contextData.getFactory();

    Integer steps = null;
    if (!implicitArguments.isEmpty()) {
      steps = Utils.getNumber(implicitArguments.getFirst(), errorReporter, true);
      if (steps == null) {
        return null;
      }
    }

    if (steps == null) {
      typechecker.checkCancelled();

      TypedExpression result = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(factory.app(args.getFirst().getExpression(), true, Collections.singletonList(factory.app(contextData.getReferenceExpression(), args.subList(0, 2)))), args.size() <= 2 ? contextData.getExpectedType() : null));
      if (result == null) {
        return typechecker.typecheck(factory.app(args.get(1).getExpression(), args.subList(2, args.size())), contextData.getExpectedType());
      }
      if (args.size() <= 2) {
        return result;
      }
      return typechecker.typecheck(factory.app(factory.core("repeat _", result), args.subList(2, args.size())), contextData.getExpectedType());
    } else {
      return typechecker.typecheck(computeConcrete(steps, args, factory), contextData.getExpectedType());
    }
  }
}
