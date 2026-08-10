package org.arend.naming.scope;

import org.arend.naming.reference.Referable;
import org.arend.naming.resolving.typing.TypedReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class ContextScope extends DelegateScope {
  private final List<? extends TypedReferable> myContext;
  private final List<? extends Referable> myPLevels;

  public ContextScope(Scope parent, List<? extends TypedReferable> context, List<? extends Referable> pLevels) {
    super(parent);
    myContext = context;
    myPLevels = pLevels;
  }

  public ContextScope(List<? extends TypedReferable> context) {
    super(EmptyScope.INSTANCE);
    myContext = context;
    myPLevels = Collections.emptyList();
  }

  @NotNull
  @Override
  public Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    if (parent == EmptyScope.INSTANCE && context != null) {
      return context == ScopeContext.STATIC ? myContext.stream().map(TypedReferable::getReferable).toList() : myPLevels;
    }
    List<Referable> result = new ArrayList<>();
    Set<String> names = new HashSet<>();
    if (context == null || context == ScopeContext.STATIC) {
      for (TypedReferable referable : myContext) {
        result.add(referable.getReferable());
      }
      for (TypedReferable referable : myContext) {
        names.add(referable.getReferable().getRefName());
      }
    }
    if (context == null || context == ScopeContext.LEVEL) {
      result.addAll(myPLevels);
      for (Referable referable : myPLevels) {
        names.add(referable.getRefName());
      }
    }

    parent.find(ref -> {
      if (!names.contains(ref.getRefName())) result.add(ref);
      return false;
    }, context);
    return result;
  }

  @Override
  public Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    if (context == null || context == ScopeContext.STATIC) {
      for (int i = myContext.size() - 1; i >= 0; i--) {
        if (pred.test(myContext.get(i).getReferable())) {
          return myContext.get(i).getReferable();
        }
      }
    }
    if (context == null || context == ScopeContext.LEVEL) {
      for (int i = myPLevels.size() - 1; i >= 0; i--) {
        if (pred.test(myPLevels.get(i))) {
          return myPLevels.get(i);
        }
      }
    }
    return parent.find(pred, context);
  }

  private Referable resolveNameLocal(@NotNull String name, @Nullable ScopeContext context) {
    if (context == null) {
      for (ScopeContext ctx : ScopeContext.values()) {
        Referable ref = resolveName(name, ctx);
        if (ref != null) return ref;
      }
    } else if (context == ScopeContext.STATIC) {
      for (int i = myContext.size() - 1; i >= 0; i--) {
        if (myContext.get(i).getReferable().getRefName().equals(name)) {
          return myContext.get(i).getReferable();
        }
      }
    } else {
      for (int i = myPLevels.size() - 1; i >= 0; i--) {
        if (myPLevels.get(i).getRefName().equals(name)) {
          return myPLevels.get(i);
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    Referable ref = resolveNameLocal(name, context);
    return ref != null ? ref : parent.resolveName(name, context);
  }

  @Override
  public @Nullable Scope resolveNamespace(@NotNull String name) {
    return resolveNameLocal(name, ScopeContext.STATIC) != null ? EmptyScope.INSTANCE : parent.resolveNamespace(name);
  }
}
