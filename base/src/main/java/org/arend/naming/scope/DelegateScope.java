package org.arend.naming.scope;

import org.arend.naming.reference.GlobalReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DelegateScope implements Scope {
  protected final Scope parent;

  public DelegateScope(Scope parent) {
    this.parent = parent;
  }

  @Override
  public @Nullable Scope resolveNamespace(@NotNull String name) {
    return parent.resolveNamespace(name);
  }

  @Override
  public @NotNull List<GlobalReferable> resolveExtensions(@NotNull String name) {
    return parent.resolveExtensions(name);
  }

  @Override
  public @NotNull Scope getGlobalSubscope() {
    return parent.getGlobalSubscope();
  }

  @Override
  public @NotNull Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
    return parent.getGlobalSubscopeWithoutOpens(withImports);
  }

  @Override
  public @Nullable ImportedScope getImportedSubscope() {
    return parent.getImportedSubscope();
  }
}
