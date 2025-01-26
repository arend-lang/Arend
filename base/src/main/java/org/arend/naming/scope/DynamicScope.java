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
  private final boolean myWithSuper;
  private final boolean myOnlyFields;

  public DynamicScope(DynamicScopeProvider provider, TypingInfo typingInfo, boolean withSuper, boolean onlyFields) {
    myProvider = provider;
    myTypingInfo = typingInfo;
    myWithSuper = withSuper;
    myOnlyFields = onlyFields;
  }

  public DynamicScope(DynamicScopeProvider provider, TypingInfo typingInfo, boolean withSuper) {
    this(provider, typingInfo, withSuper, withSuper);
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

    while (!toVisit.isEmpty()) {
      DynamicScopeProvider provider = toVisit.removeLast();
      if (!visited.add(provider.getReferable())) {
        continue;
      }

      for (GlobalReferable referable : provider.getDynamicContent()) {
        if (referable.getAccessModifier() == AccessModifier.PRIVATE || myOnlyFields && referable.getKind() != GlobalReferable.Kind.FIELD) {
          continue;
        }
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

      List<? extends GlobalReferable> superRefs = provider.getSuperReferables();
      if (myWithSuper) {
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
