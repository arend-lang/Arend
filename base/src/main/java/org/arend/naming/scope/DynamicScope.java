package org.arend.naming.scope;

import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.DynamicScopeProvider;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class DynamicScope implements Scope {
  private final DynamicScopeProvider myProvider;
  private final TypingInfo myTypingInfo;
  private final Extent myExtent;

  public enum Extent { WITH_SUPER, WITH_DYNAMIC, WITH_SUPER_DYNAMIC, ONLY_FIELDS }

  public DynamicScope(DynamicScopeProvider provider, TypingInfo typingInfo, Extent extent) {
    myProvider = provider;
    myTypingInfo = typingInfo;
    myExtent = extent;
  }

  @Nullable
  @Override
  public Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    if (!(context == null || context == ScopeContext.STATIC)) {
      return null;
    }

    Set<GlobalReferable> visited = new HashSet<>();
    Deque<DynamicScopeProvider> toVisit = new ArrayDeque<>();
    toVisit.add(myProvider);
    Extent extent = myExtent;

    while (!toVisit.isEmpty()) {
      DynamicScopeProvider provider = toVisit.removeLast();
      if (!visited.add(provider.getReferable())) {
        continue;
      }

      for (GlobalReferable referable : provider.getDynamicContent()) {
        boolean isField = referable.getKind() == GlobalReferable.Kind.FIELD;
        boolean notPrivate = referable.getAccessModifier() != AccessModifier.PRIVATE;
        if (isField && (extent == Extent.WITH_SUPER || notPrivate) || !isField && extent == Extent.WITH_DYNAMIC && notPrivate && !isCoclause(referable)) {
          if (pred.test(referable)) {
            return referable;
          }
          if (referable.hasAlias()) {
            AliasReferable aliasRef = new AliasReferable(referable);
            if (pred.test(aliasRef)) {
              return aliasRef;
            }
          }
        }
      }

      if (extent == Extent.WITH_SUPER_DYNAMIC) {
        extent = Extent.WITH_DYNAMIC;
      }

      List<? extends GlobalReferable> superRefs = provider.getSuperReferables();
      if (myExtent == Extent.WITH_SUPER) {
        for (GlobalReferable superRef : superRefs) {
          DynamicScopeProvider superProvider = myTypingInfo.getDynamicScopeProvider(superRef);
          if (superProvider == null) continue;
          if (pred.test(superProvider.getReferable())) {
            return superProvider.getReferable();
          }
          if (superProvider.getReferable().hasAlias()) {
            AliasReferable aliasRef = new AliasReferable(superProvider.getReferable());
            if (pred.test(aliasRef)) {
              return aliasRef;
            }
          }
        }
      }

      for (int i = superRefs.size() - 1; i >= 0; i--) {
        DynamicScopeProvider superProvider = myTypingInfo.getDynamicScopeProvider(superRefs.get(i));
        if (superProvider != null) toVisit.add(superProvider);
      }
    }

    return null;
  }

  private static boolean isCoclause(GlobalReferable referable) {
    return referable instanceof LocatedReferable located && located.getKind() == GlobalReferable.Kind.COCLAUSE_FUNCTION;
  }

  @NotNull
  @Override
  public Scope getGlobalSubscope() {
    return EmptyScope.INSTANCE;
  }

  @NotNull
  @Override
  public Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
    return EmptyScope.INSTANCE;
  }
}
