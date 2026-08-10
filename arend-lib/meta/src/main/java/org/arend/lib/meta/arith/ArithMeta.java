package org.arend.lib.meta.arith;

import org.arend.ext.FreeBindingsModifier;
import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.context.ContextHelper;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ArithMeta extends BaseMetaDefinition {
  @Dependency(name = "linarith") ArendRef linarithRef;

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { true };
  }

  @Override
  public int numberOfOptionalExplicitArguments() {
    return 1;
  }

  @Override
  public boolean requireExpectedType() {
    return true;
  }

  protected abstract List<CoreBinding> collectSyntheticBindings(
      ExpressionTypechecker typechecker, ContextData contextData, List<CoreBinding> bindings);

  protected static void tryStrengthen(ExpressionTypechecker typechecker, ConcreteFactory factory,
      ArendRef lemmaRef, List<CoreBinding> bindings, List<CoreBinding> out) {
    for (CoreBinding b : bindings) {
      ConcreteExpression tightened = factory.app(factory.ref(lemmaRef), true, factory.ref(b));
      TypedExpression typed = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(tightened, null));
      if (typed != null) {
        out.add(typed.makeEvaluatingBinding(null));
      }
    }
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ConcreteFactory factory = typechecker.getFactory().withData(contextData.getMarker());
    CoreExpression expectedType = contextData.getExpectedType();
    ConcreteExpression userHint = contextData.getArguments().isEmpty() ? null : contextData.getArguments().getFirst().getExpression();
    List<CoreBinding> bindings = new ContextHelper(null).getContextBindings(typechecker);

    List<CoreBinding> synthBindings = collectSyntheticBindings(typechecker, contextData, bindings);

    if (synthBindings.isEmpty()) {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(linarithRef));
      if (userHint != null) builder.app(userHint, true);
      return typechecker.typecheck(builder.build(), expectedType);
    }

    FreeBindingsModifier modifier = new FreeBindingsModifier();
    modifier.add(synthBindings);
    return typechecker.withFreeBindings(modifier, tc -> {
      ConcreteAppBuilder builder = factory.appBuilder(factory.ref(linarithRef));
      if (userHint != null) builder.app(userHint, true);
      return tc.typecheck(builder.build(), expectedType);
    });
  }
}
