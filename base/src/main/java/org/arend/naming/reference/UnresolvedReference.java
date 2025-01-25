package org.arend.naming.reference;

import org.arend.ext.reference.DataContainer;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.scope.Scope;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface UnresolvedReference extends Referable, DataContainer {
  @NotNull Referable resolve(Scope scope, @Nullable List<Referable> resolvedRefs, @Nullable Scope.ScopeContext context, @Nullable ResolverListener listener);
  @Nullable Referable tryResolve(Scope scope, List<Referable> resolvedRefs, @Nullable ResolverListener listener);
  @Nullable Concrete.Expression resolveExpression(Scope scope, @NotNull TypingInfo typingInfo, @Nullable List<Referable> resolvedRefs, @Nullable ResolverListener listener);
  @Nullable Concrete.Expression tryResolveExpression(Scope scope, @NotNull TypingInfo typingInfo, @Nullable List<Referable> resolvedRefs, @Nullable ResolverListener listener);
  @NotNull List<AbstractReference> getReferenceList();
  @NotNull List<String> getPath();
  UnresolvedReference copy();
  void reset();
  boolean isResolved();

  @NotNull
  default Referable resolve(Scope scope, List<Referable> resolvedRefs, @Nullable ResolverListener listener) {
    return resolve(scope, resolvedRefs, Scope.ScopeContext.STATIC, listener);
  }

  @Override
  default boolean isLocalRef() {
    return false;
  }
}
