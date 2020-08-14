package org.arend.lib.meta;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.NameResolverError;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.typechecking.*;
import org.arend.lib.StdExtension;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class RunMeta implements MetaDefinition, MetaResolver {
  private final StdExtension ext;

  public RunMeta(StdExtension ext) {
    this.ext = ext;
  }

  @Override
  public boolean checkArguments(@NotNull List<? extends ConcreteArgument> arguments) {
    return arguments.size() == 1 && !arguments.get(0).isExplicit();
  }

  @Override
  public boolean checkContextData(@NotNull ContextData contextData, @NotNull ErrorReporter errorReporter) {
    List<? extends ConcreteArgument> args = contextData.getArguments();
    if (args.size() != 1 || args.get(0).isExplicit()) {
      errorReporter.report(new NameResolverError("Expected 1 implicit argument", args.isEmpty() ? contextData.getMarker() : args.get(0).getExpression()));
      return false;
    }
    return true;
  }

  private ConcreteExpression getConcreteRepresentation(List<? extends ConcreteArgument> arguments, ConcreteSourceNode marker) {
    List<? extends ConcreteExpression> args = Utils.getArgumentList(arguments.get(0).getExpression());
    ConcreteFactory factory = ext.factory.withData(marker);
    ConcreteExpression result = args.get(args.size() - 1);
    for (int i = args.size() - 2; i >= 0; i--) {
      ConcreteExpression arg = args.get(i);
      if (arg instanceof ConcreteLetExpression && ((ConcreteLetExpression) arg).getExpression() instanceof ConcreteIncompleteExpression) {
        ConcreteLetExpression let = (ConcreteLetExpression) arg;
        result = factory.letExpr(let.isStrict(), let.getClauses(), result);
      } else if (arg instanceof ConcreteLamExpression && ((ConcreteLamExpression) arg).getBody() instanceof ConcreteIncompleteExpression) {
        result = factory.lam(((ConcreteLamExpression) arg).getParameters(), result);
      } else {
        result = factory.app(arg, true, Collections.singletonList(result));
      }
    }
    return result;
  }

  @Override
  public @Nullable ConcreteExpression getConcreteRepresentation(@NotNull List<? extends ConcreteArgument> arguments) {
    return getConcreteRepresentation(arguments, null);
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    if (contextData.getExpectedType() != null) {
      return typechecker.defer(new MetaDefinition() {
        @Override
        public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
          return typechecker.typecheck(RunMeta.this.getConcreteRepresentation(contextData.getArguments(), contextData.getMarker()), contextData.getExpectedType());
        }
      }, contextData, contextData.getExpectedType());
    } else {
      return typechecker.typecheck(getConcreteRepresentation(contextData.getArguments(), contextData.getMarker()), contextData.getExpectedType());
    }
  }

  @Override
  public @Nullable ConcreteExpression resolvePrefix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
    ConcreteReferenceExpression refExpr = contextData.getReferenceExpression();
    List<? extends ConcreteArgument> arguments = contextData.getArguments();
    if (!checkArguments(arguments)) {
      return null;
    }

    ConcreteExpression repr = getConcreteRepresentation(arguments, refExpr);
    ConcreteExpression result = resolver.resolve(repr);
    return result == repr ? ext.factory.withData(refExpr.getData()).app(refExpr, arguments) : result;
  }
}
