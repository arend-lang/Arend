package org.arend.lib.meta.exists;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.concrete.expr.ConcreteTypedExpression;
import org.arend.ext.error.ArgumentExplicitnessError;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ContextDataChecker;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExistsResolver implements MetaResolver {
  private final GivenMeta.Kind kind;

  public ExistsResolver(GivenMeta.Kind kind) {
    this.kind = kind;
  }

  @Override
  public @Nullable ConcreteExpression resolvePrefix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
    if (!new ContextDataChecker().checkContextData(contextData, resolver.getErrorReporter())) {
      return null;
    }
    ConcreteFactory factory = contextData.getFactory();

    List<? extends ConcreteArgument> arguments = contextData.getArguments();
    for (int i = 0; i < arguments.size(); i++) {
      if (arguments.get(i).isExplicit() && arguments.get(i).getExpression() instanceof ConcreteReferenceExpression refExpr && !resolver.isLongUnresolvedReference(refExpr.getReferent())) {
        ArendRef ref = refExpr.getReferent();
        ref = resolver.isUnresolved(ref) ? resolver.resolveName(ref.getRefName()) : resolver.getOriginalRef(ref);
        if (ref instanceof MetaRef metaRef && metaRef.getResolver() instanceof ExistsResolver) {
          List<ConcreteArgument> newArgs = new ArrayList<>(i + 1);
          newArgs.addAll(arguments.subList(0, i));
          newArgs.add(factory.arg(factory.app(arguments.get(i).getExpression(), arguments.subList(i + 1, arguments.size())), true));
          arguments = newArgs;
          break;
        }
      }
    }

    if (arguments.isEmpty()) {
      if (kind == GivenMeta.Kind.PI) {
        resolver.getErrorReporter().report(new ArgumentExplicitnessError(true, contextData.getMarker()));
        return null;
      }
      return factory.sigma();
    }

    if (arguments.size() == 1) {
      if (!arguments.getFirst().isExplicit()) {
        resolver.getErrorReporter().report(new ArgumentExplicitnessError(true, arguments.getFirst().getExpression()));
        return null;
      }
      return factory.app(contextData.getReferenceExpression(), true, Collections.singletonList(resolver.resolve(arguments.getFirst().getExpression())));
    }

    ConcreteExpression codomain = null;
    List<ConcreteParameter> parameters = new ArrayList<>();
    for (int i = 0; i < arguments.size(); i++) {
      ConcreteArgument argument = arguments.get(i);
      ConcreteFactory argFactory = factory.withData(argument.getExpression().getData());
      if (kind == GivenMeta.Kind.PI) {
        if (i == arguments.size() - 1) {
          if (!argument.isExplicit()) {
            resolver.getErrorReporter().report(new ArgumentExplicitnessError(true, argument.getExpression()));
            return null;
          }
          if (argument.getExpression() instanceof ConcreteTypedExpression) {
            resolver.getErrorReporter().report(new TypecheckingError("Expected a type without variables", contextData.getMarker()));
            return null;
          }
        }
        if (argument.getExpression() instanceof ConcreteTypedExpression || argument.isExplicit()) {
          if (i == arguments.size() - 1) {
            codomain = argument.getExpression();
          } else {
            if (argument.getExpression() instanceof ConcreteReferenceExpression) {
              ConcreteParameter param = argFactory.param(Collections.singletonList(((ConcreteReferenceExpression) argument.getExpression()).getReferent()), argFactory.hole());
              parameters.add(argument.isExplicit() ? param : param.implicit());
            } else {
              ConcreteParameter param = Utils.expressionToParameter(argument.getExpression(), resolver, argFactory);
              if (param == null) {
                return null;
              }
              parameters.add(argument.isExplicit() ? param : param.implicit());
            }
          }
        } else {
          List<ArendRef> refs = Utils.getRefs(argument.getExpression(), resolver);
          if (refs == null) {
            return null;
          }
          ConcreteParameter param = argFactory.param(refs, argFactory.hole());
          parameters.add(argument.isExplicit() ? param : param.implicit());
        }
      } else if (argument.isExplicit()) {
        ConcreteParameter param = Utils.expressionToParameter(argument.getExpression(), resolver, argFactory);
        if (param == null) {
          return null;
        }
        parameters.add(param);
      } else {
        List<ArendRef> refs = Utils.getTuplesOfRefs(argument.getExpression(), resolver);
        if (refs == null) {
          return null;
        }
        parameters.add(argFactory.param(refs, argFactory.hole()));
      }
    }

    return factory.app(contextData.getReferenceExpression(), true, Collections.singletonList(resolver.resolve(codomain != null ? factory.pi(parameters, codomain) : factory.sigma(parameters))));
  }
}
