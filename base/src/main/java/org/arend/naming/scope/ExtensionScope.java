package org.arend.naming.scope;

import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Contains extension functions of the parent scope in every context.
 * It is used to complete {@code x.foo}.
 */
public class ExtensionScope implements Scope {
  private final Scope myParent;

  public ExtensionScope(Scope parent) {
    myParent = parent;
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    return myParent.find(pred, ScopeContext.EXTENSION);
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    return myParent.resolveName(name, ScopeContext.EXTENSION);
  }

  @Override
  public @NotNull List<GlobalReferable> resolveExtensions(@NotNull String name) {
    return myParent.resolveExtensions(name);
  }

  @Override
  public @NotNull Scope getGlobalSubscope() {
    return EmptyScope.INSTANCE;
  }

  @Override
  public @NotNull Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
    return EmptyScope.INSTANCE;
  }
}
