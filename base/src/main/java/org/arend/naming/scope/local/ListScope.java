package org.arend.naming.scope.local;

import org.arend.ext.reference.ArendRef;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.DelegateScope;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.NamespaceCommandSink;
import org.arend.naming.scope.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class ListScope extends DelegateScope {
  private final List<? extends ArendRef> myReferables;

  public ListScope(Scope parent, List<? extends ArendRef> referables) {
    super(parent);
    myReferables = referables;
  }

  public ListScope(List<? extends ArendRef> referables) {
    this(EmptyScope.INSTANCE, referables);
  }

  public ListScope(ArendRef... referables) {
    this(Arrays.asList(referables));
  }

  private Referable findHere(Predicate<Referable> pred) {
    for (int i = myReferables.size() - 1; i >= 0; i--) {
      ArendRef referable = myReferables.get(i);
      if (referable instanceof Referable && pred.test((Referable) referable)) {
        return (Referable) referable;
      }
    }
    return null;
  }

  @Override
  public Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    Referable ref = context == null || context == ScopeContext.STATIC ? findHere(pred) : null;
    return ref != null ? ref : parent.find(pred, context);
  }

  @Override
  public Referable resolveName(@NotNull String name, @Nullable ScopeContext context, @Nullable NamespaceCommandSink sink) {
    return find(ref -> Objects.equals(name, ref.textRepresentation()), context, sink);
  }

  @Override
  public Referable find(Predicate<Referable> pred, @Nullable ScopeContext context, @Nullable NamespaceCommandSink sink) {
    if (context == null || context == ScopeContext.STATIC) {
      if (sink != null) sink.setNoCommand();
      Referable ref = findHere(pred);
      if (ref != null) return ref;
    }
    return parent.find(pred, context, sink);
  }
}
