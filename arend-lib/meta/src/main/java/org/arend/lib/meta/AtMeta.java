package org.arend.lib.meta;

import org.arend.ext.FreeBindingsModifier;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.StdExtension;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AtMeta extends BaseMetaDefinition {
  private final StdExtension ext;

  public AtMeta(StdExtension ext) {
    this.ext = ext;
  }

  @Override
  public boolean[] argumentExplicitness() {
    return new boolean[] { true, true, true };
  }

  @Override
  public boolean allowExcessiveArguments() {
    return false;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    List<? extends ConcreteArgument> args = contextData.getArguments();
    CoreBinding binding = args.get(1).getExpression() instanceof ConcreteReferenceExpression ? typechecker.getFreeBinding(((ConcreteReferenceExpression) args.get(1).getExpression()).getReferent()) : null;
    if (binding == null) {
      typechecker.getErrorReporter().report(new TypecheckingError("Expected a local variable", args.get(1).getExpression()));
      return null;
    }

    ConcreteFactory factory = ext.factory.withData(contextData.getMarker().getData());
    ConcreteExpression cReplacement = args.get(1).getExpression();
    for (ConcreteExpression function : Utils.getArgumentList(args.get(0).getExpression())) {
      cReplacement = factory.app(function, true, cReplacement);
    }

    TypedExpression replacement = typechecker.typecheck(cReplacement, null);
    if (replacement == null) {
      return null;
    }

    return typechecker.withFreeBindings(new FreeBindingsModifier().replace(binding, replacement.makeEvaluatingBinding(binding.getName())), tc -> tc.typecheck(args.get(2).getExpression(), contextData.getExpectedType()));
  }
}
