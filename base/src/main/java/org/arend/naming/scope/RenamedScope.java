package org.arend.naming.scope;

import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Predicate;

public class RenamedScope extends DelegateScope {
  private final Map<TCDefReferable, TCDefReferable> myRenamer;

  public RenamedScope(Scope parent, Map<TCDefReferable, TCDefReferable> renamer) {
    super(parent);
    myRenamer = renamer;
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    return parent.find(ref -> {
      Referable newRef = ref;
      if (ref instanceof TCDefReferable) {
        newRef = myRenamer.get(ref);
        if (newRef == null) newRef = ref;
      }
      return pred.test(newRef);
    }, context);
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    Referable result = parent.resolveName(name, context);
    if (result instanceof TCDefReferable) {
      TCDefReferable newRef = myRenamer.get(result);
      if (newRef != null) return newRef;
    }
    return result;
  }
}
