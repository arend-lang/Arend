package org.arend.naming.scope;

import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Minimal definition: (find or getElements) and resolveNamespace
public interface Scope {
  enum ScopeContext { STATIC, DYNAMIC, PLEVEL, HLEVEL }

  default @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    for (Referable referable : getElements(context)) {
      if (pred.test(referable)) {
        return referable;
      }
    }
    return null;
  }

  default @Nullable Referable find(Predicate<Referable> pred) {
    return find(pred, ScopeContext.STATIC);
  }

  /**
   * Returns elements in this scope.
   *
   * @param context  if null, returns all elements.
   */
  @NotNull
  default Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    List<Referable> result = new ArrayList<>();
    find(ref -> { result.add(ref); return false; }, context);
    return result;
  }

  @NotNull
  default Collection<? extends Referable> getElements() {
    return getElements(ScopeContext.STATIC);
  }

  /**
   * Resolves a name in this scope.
   *
   * @param name      the name to resolve
   * @param context   if null, allows any kind of referable
   */
  @Nullable
  default Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    return find(ref -> Objects.equals(name, ref.textRepresentation()), context);
  }

  @Nullable
  default Referable resolveName(@NotNull String name) {
    return resolveName(name, ScopeContext.STATIC);
  }

  default @Nullable Scope resolveNamespace(@NotNull String name) {
    return null;
  }

  default @NotNull Scope getGlobalSubscope() {
    return this;
  }

  default @NotNull Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
    return this;
  }

  default @Nullable ImportedScope getImportedSubscope() {
    return null;
  }

  default @Nullable Scope resolveNamespace(List<? extends String> path) {
    Scope scope = this;
    for (String name : path) {
      scope = scope.resolveNamespace(name);
      if (scope == null) {
        return null;
      }
    }
    return scope;
  }

  static Referable resolveName(Scope scope, List<? extends String> path) {
    for (int i = 0; i < path.size(); i++) {
      if (scope == null) {
        return null;
      }
      if (i == path.size() - 1) {
        return scope.resolveName(path.get(i));
      } else {
        scope = scope.resolveNamespace(path.get(i));
      }
    }
    return null;
  }

  static void traverse(Scope scope, Consumer<Referable> consumer) {
    if (scope == null) return;
    for (Referable ref : scope.getElements()) {
      consumer.accept(ref);
      traverse(scope.resolveNamespace(ref.getRefName()), consumer);
    }
  }
}
