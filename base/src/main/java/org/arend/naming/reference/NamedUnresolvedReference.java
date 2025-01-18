package org.arend.naming.reference;

import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.scope.Scope;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class NamedUnresolvedReference implements UnresolvedReference {
  private final Object myData;
  private final String myName;
  private Referable resolved;

  public NamedUnresolvedReference(Object data, @NotNull String name) {
    myData = data;
    myName = name;
  }

  @Nullable
  @Override
  public Object getData() {
    return myData;
  }

  @NotNull
  @Override
  public String textRepresentation() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NamedUnresolvedReference that = (NamedUnresolvedReference) o;
    return myName.equals(that.myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @NotNull
  @Override
  public Referable resolve(Scope scope, @Nullable List<Referable> resolvedRefs, @Nullable Scope.ScopeContext context, @Nullable ResolverListener listener) {
    if (resolved != null) {
      return resolved;
    }

    if (listener != null && myData instanceof AbstractReference reference) {
      listener.resolving(reference, scope, context, true);
    }
    resolved = scope.resolveName(myName, context);
    if (resolved == null) {
      resolved = new ErrorReference(myData, myName);
    }
    if (resolvedRefs != null) {
      resolvedRefs.add(resolved);
    }
    return resolved;
  }

  @Nullable
  @Override
  public Referable tryResolve(Scope scope, List<Referable> resolvedRefs, @Nullable ResolverListener listener) {
    if (resolved != null) {
      return resolved;
    }

    if (listener != null && myData instanceof AbstractReference reference) {
      listener.resolving(reference, scope, Scope.ScopeContext.STATIC, true);
    }
    resolved = scope.resolveName(myName);
    if (resolved != null && resolvedRefs != null) {
      resolvedRefs.add(resolved);
    }

    return resolved;
  }

  @Nullable
  @Override
  public Concrete.Expression resolveExpression(Scope scope, List<Referable> resolvedRefs, @Nullable ResolverListener listener) {
    resolve(scope, resolvedRefs, listener);
    return null;
  }

  @Override
  public @Nullable Concrete.Expression tryResolveExpression(Scope scope, List<Referable> resolvedRefs, @Nullable ResolverListener listener) {
    tryResolve(scope, resolvedRefs, listener);
    return null;
  }

  @Override
  public @NotNull List<AbstractReference> getReferenceList() {
    return Collections.singletonList(myData instanceof AbstractReference ? (AbstractReference) myData : null);
  }

  @Override
  public @NotNull List<String> getPath() {
    return Collections.singletonList(myName);
  }

  @Override
  public NamedUnresolvedReference copy() {
    return new NamedUnresolvedReference(myData, myName);
  }

  @Override
  public void reset() {
    resolved = null;
  }

  @Override
  public boolean isResolved() {
    return resolved != null;
  }
}
