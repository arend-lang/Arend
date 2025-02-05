package org.arend.naming.scope;

import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

public class InstanceScope extends DelegateScope {
  public InstanceScope(Scope parent) {
    super(parent);
  }

  @Override
  public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    return parent.getElements(context).stream().filter(ref -> ref instanceof GlobalReferable global && global.getKind() == GlobalReferable.Kind.INSTANCE).toList();
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    return parent.find(ref -> ref instanceof GlobalReferable global && global.getKind() == GlobalReferable.Kind.INSTANCE && pred.test(ref), context);
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    Referable ref = parent.resolveName(name, context);
    return ref instanceof GlobalReferable global && global.getKind() == GlobalReferable.Kind.INSTANCE ? ref : null;
  }

  @Override
  public @Nullable Scope resolveNamespace(@NotNull String name) {
    return null;
  }
}
