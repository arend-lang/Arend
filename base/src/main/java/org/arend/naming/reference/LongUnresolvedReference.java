package org.arend.naming.reference;

import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.typing.DynamicScopeProvider;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.scope.*;
import org.arend.term.Fixity;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LongUnresolvedReference implements UnresolvedReference {
  private final Object myData;
  private final List<AbstractReference> myReferences; // TODO[server2]: Remove this after parser redesign
  private final List<String> myPath;
  private Referable resolved;

  public LongUnresolvedReference(Object data, @Nullable List<AbstractReference> references, @NotNull List<String> path) {
    assert !path.isEmpty();
    myData = data;
    myPath = path;

    if (references == null) {
      references = new ArrayList<>(path.size());
      for (String ignored : path) {
        references.add(null);
      }
    }
    myReferences = references;
  }

  public static UnresolvedReference make(Object data, @NotNull List<String> path) {
    if (path.isEmpty()) return null;
    if (path.size() == 1) return new NamedUnresolvedReference(data, path.getFirst());

    List<AbstractReference> references = new ArrayList<>(path.size());
    for (String ignored : path) {
      references.add(null);
    }
    return new LongUnresolvedReference(data, references, path);
  }

  public static UnresolvedReference make(Object data, @NotNull List<AbstractReference> references, @NotNull List<String> path) {
    return path.isEmpty() ? null : path.size() == 1 ? new NamedUnresolvedReference(data, path.getFirst()) : new LongUnresolvedReference(data, references, path);
  }

  public @NotNull List<String> getPath() {
    return myPath;
  }

  @Override
  public LongUnresolvedReference copy() {
    return new LongUnresolvedReference(myData, myReferences, myPath);
  }

  @Nullable
  @Override
  public Object getData() {
    return myData;
  }

  @NotNull
  @Override
  public String textRepresentation() {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String name : myPath) {
      if (first) {
        first = false;
      } else {
        builder.append(".");
      }
      builder.append(name);
    }
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    LongUnresolvedReference that = (LongUnresolvedReference) o;

    return myPath.equals(that.myPath);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPath.hashCode();
    return result;
  }

  private Referable resolve(Scope scope, List<Referable> resolvedRefs, boolean onlyTry, Scope.ScopeContext context, @Nullable ResolverListener listener) {
    if (resolved != null) {
      return resolved;
    }

    for (int i = 0; i < myPath.size() - 1; i++) {
      if (resolvedRefs != null) {
        resolvedRefs.add(scope.resolveName(myPath.get(i)));
      }
      if (listener != null && i < myReferences.size() && myReferences.get(i) != null) {
        listener.resolving(myReferences.get(i), scope, context, !onlyTry);
      }
      scope = scope.resolveNamespace(myPath.get(i));
      if (scope == null) {
        if (!onlyTry) {
          Object data = getData();
          resolved = new ErrorReference(data, make(data, myPath.subList(0, i)), i, myPath.get(i));
          if (resolvedRefs != null) {
            resolvedRefs.set(i, resolved);
          }
        } else {
          resolved = null;
        }
        return resolved;
      }
    }

    String name = myPath.getLast();
    if (listener != null && myPath.size() - 1 < myReferences.size() && myReferences.get(myPath.size() - 1) != null) {
      listener.resolving(myReferences.get(myPath.size() - 1), scope, context, !onlyTry);
    }
    resolved = scope.resolveName(name, context);
    if (resolved == null && !onlyTry) {
      Object data = getData();
      resolved = new ErrorReference(data, make(data, myPath.subList(0, myPath.size() - 1)), myPath.size() - 1, name);
    }
    if (resolvedRefs != null && resolved != null) {
      resolvedRefs.add(resolved);
    }

    return resolved;
  }

  @NotNull
  @Override
  public Referable resolve(Scope scope, @Nullable List<Referable> resolvedRefs, @Nullable Scope.ScopeContext context, @Nullable ResolverListener listener) {
    return resolve(scope, resolvedRefs, false, context, listener);
  }

  @Nullable
  @Override
  public Referable tryResolve(Scope scope, List<Referable> resolvedRefs, @Nullable Scope.ScopeContext context, @Nullable ResolverListener listener) {
    return resolve(scope, resolvedRefs, true, context, listener);
  }

  private Concrete.Expression resolveArgument(Scope scope, boolean onlyTry, TypingInfo typingInfo, List<Referable> resolvedRefs, @Nullable ResolverListener listener) {
    if (resolved != null) {
      return null;
    }

    Scope initialScope = scope;
    Scope prevScope = scope;
    for (int i = 0; i < myPath.size() - 1; i++) {
      Scope nextScope = scope.resolveNamespace(myPath.get(i));
      if (listener != null && i < myReferences.size() && myReferences.get(i) != null) {
        listener.resolving(myReferences.get(i), scope, Scope.ScopeContext.STATIC, !onlyTry && nextScope != null);
      }
      if (nextScope == null) {
        return resolveField(prevScope, initialScope, i - 1, onlyTry, typingInfo, resolvedRefs, listener);
      }

      if (resolvedRefs != null) {
        resolvedRefs.add(scope.resolveName(myPath.get(i)));
      }

      prevScope = scope;
      scope = nextScope;
    }

    String name = myPath.getLast();
    resolved = scope.resolveName(name);
    if (listener != null && myPath.size() - 1 < myReferences.size() && myReferences.get(myPath.size() - 1) != null) {
      listener.resolving(myReferences.get(myPath.size() - 1), scope, Scope.ScopeContext.STATIC, !onlyTry && resolved != null);
    }
    if (resolved == null) {
      if (myPath.size() == 1) {
        if (onlyTry) return null;
        resolved = new ErrorReference(getData(), name);
      } else {
        return resolveField(prevScope, initialScope, myPath.size() - 2, onlyTry, typingInfo, resolvedRefs, listener);
      }
    }
    if (resolvedRefs != null) {
      resolvedRefs.add(resolved);
    }

    return null;
  }

  @Nullable
  @Override
  public Concrete.Expression resolveExpression(Scope scope, @NotNull TypingInfo typingInfo, @Nullable List<Referable> resolvedRefs, @Nullable ResolverListener listener) {
    return resolveArgument(scope, false, typingInfo, resolvedRefs, listener);
  }

  @Override
  public @Nullable Concrete.Expression tryResolveExpression(Scope scope, @NotNull TypingInfo typingInfo, @Nullable List<Referable> resolvedRefs, @Nullable ResolverListener listener) {
    return resolveArgument(scope, true, typingInfo, resolvedRefs, listener);
  }

  @Override
  public @NotNull List<AbstractReference> getReferenceList() {
    return myReferences;
  }

  @Override
  public void reset() {
    resolved = null;
  }

  @Override
  public boolean isResolved() {
    return resolved != null;
  }

  private Concrete.Expression resolveField(Scope scope, Scope initialScope, int i, boolean onlyTry, TypingInfo typingInfo, List<Referable> resolvedRefs, ResolverListener listener) {
    if (i == -1) {
      resolved = scope.resolveName(myPath.getFirst());
      if (listener != null && !myReferences.isEmpty() && myReferences.getFirst() != null) {
        listener.resolving(myReferences.getFirst(), scope, Scope.ScopeContext.STATIC, !onlyTry);
      }
      if (resolvedRefs != null) {
        resolvedRefs.add(resolved);
      }
      i = 0;
    } else {
      resolved = resolvedRefs != null ? resolvedRefs.get(i) : scope.resolveName(myPath.get(i));
    }

    if (resolved == null) {
      if (!onlyTry) {
        resolved = new ErrorReference(myData, make(myData, myPath.subList(0, i)), i, myPath.get(i));
        if (resolvedRefs != null) {
          resolvedRefs.set(i, resolved);
        }
      }
      return null;
    }

    DynamicScopeProvider provider = typingInfo.getTypeDynamicScopeProvider(resolved);
    boolean withArg = true;
    if (provider == null) {
      provider = typingInfo.getBodyDynamicScopeProvider(resolved);
      if (provider != null) withArg = false;
    }
    if (provider == null && i + 1 < myPath.size() && RedirectingReferable.getOriginalReferable(resolved) instanceof GlobalReferable globalRef && globalRef.getKind() == GlobalReferable.Kind.OTHER) {
      resolved = new ErrorReference(myData, resolved, i + 1, myPath.get(i + 1));
      return null;
    }

    Concrete.Expression result = withArg ? new Concrete.ReferenceExpression(myData, resolved) : null;
    for (i++; i < myPath.size(); i++) {
      Referable newResolved;
      if (provider == null) {
        newResolved = initialScope.resolveName(myPath.get(i), Scope.ScopeContext.DYNAMIC);
        if (listener != null && i < myReferences.size() && myReferences.get(i) != null) {
          listener.resolving(myReferences.get(i), initialScope, Scope.ScopeContext.DYNAMIC, !onlyTry);
        }
      } else {
        Scope dynamicScope = new DynamicScope(provider, typingInfo, DynamicScope.Extent.WITH_DYNAMIC);
        newResolved = dynamicScope.resolveName(myPath.get(i));
        if (listener != null && i < myReferences.size() && myReferences.get(i) != null) {
          listener.resolving(myReferences.get(i), dynamicScope, Scope.ScopeContext.STATIC, !onlyTry);
        }
      }
      if (newResolved == null) {
        if (provider != null) {
          if (onlyTry) return null;
          resolved = new ErrorReference(i < myReferences.size() ? myReferences.get(i) : myData, resolved, i, myPath.get(i));
          if (resolvedRefs != null) {
            resolvedRefs.add(resolved);
          }
          return null;
        } else {
          for (; i < myPath.size(); i++) {
            result = new Concrete.FieldCallExpression(myData, new NamedUnresolvedReference(i < myReferences.size() ? myReferences.get(i) : myData, myPath.get(i)), i == myPath.size() - 1 ? Fixity.UNKNOWN : Fixity.NONFIX, result);
          }
          return result;
        }
      }
      resolved = newResolved;
      if (resolvedRefs != null) {
        resolvedRefs.add(resolved);
      }
      Concrete.Expression refExpr = new Concrete.ReferenceExpression(myData, resolved);
      result = result == null ? refExpr : Concrete.AppExpression.make(myData, refExpr, result, false);
      provider = typingInfo.getTypeDynamicScopeProvider(resolved);
    }

    return result;
  }

  public Scope resolveNamespace(Scope scope) {
    if (resolved instanceof ErrorReference) {
      return null;
    }

    for (int i = 0; i < myPath.size(); i++) {
      scope = scope.resolveNamespace(myPath.get(i));
      if (scope == null) {
        Object data = getData();
        resolved = new ErrorReference(data, make(data, myPath.subList(0, i)), i, myPath.get(i));
        return null;
      }
    }

    return scope;
  }

  public ErrorReference getErrorReference() {
    return resolved instanceof ErrorReference ? (ErrorReference) resolved : null;
  }
}
