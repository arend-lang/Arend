package org.arend.naming.scope;

import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Resolves names in STATIC context to extension functions if there is no static definition with this name.
 * It is used for names in namespace commands.
 */
public class StaticOrExtensionScope extends DelegateScope {
  public StaticOrExtensionScope(Scope parent) {
    super(parent);
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    Referable result = parent.find(pred, context);
    return result == null && context == ScopeContext.STATIC ? parent.find(pred, ScopeContext.EXTENSION) : result;
  }

  @Override
  public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    return parent.getElements(context);
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    Referable result = parent.resolveName(name, context);
    return result == null && context == ScopeContext.STATIC ? parent.resolveName(name, ScopeContext.EXTENSION) : result;
  }
}
