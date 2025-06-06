package org.arend.naming.scope;

import org.arend.ext.module.ModulePath;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.*;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

// TODO[server2]: This class shouldn't resolve namespace commands. It should take as an input already resolved data.
public class LexicalScope implements Scope {
  private final Scope myParent;
  private final ConcreteGroup myGroup;
  private final ModulePath myModule;
  private final boolean myDynamicContext;
  private final boolean myWithAdditionalContent; // with external parameters and content of \open

  public LexicalScope(Scope parent, ConcreteGroup group, ModulePath module, boolean isDynamicContext, boolean withAdditionalContent) {
    myParent = parent;
    myGroup = group;
    myModule = module;
    myDynamicContext = isDynamicContext;
    myWithAdditionalContent = withAdditionalContent;
  }

  public static LexicalScope insideOf(ConcreteGroup group, Scope parent, boolean isDynamicContext) {
    ModuleLocation moduleLocation = group.referable().getLocation();
    return new LexicalScope(parent, group, moduleLocation == null ? null : moduleLocation.getModulePath(), isDynamicContext, true);
  }

  public static LexicalScope opened(ConcreteGroup group) {
    return new LexicalScope(EmptyScope.INSTANCE, group, null, true, false);
  }

  private Referable checkReferable(Referable referable, Predicate<Referable> pred) {
    String name = referable.textRepresentation();
    if (!name.isEmpty() && !"_".equals(name)) {
      if (pred.test(referable)) return referable;
    }
    if (referable instanceof GlobalReferable) {
      String alias = ((GlobalReferable) referable).getAliasName();
      if (alias != null && !alias.isEmpty() && !"_".equals(alias)) {
        Referable aliasRef = new AliasReferable((GlobalReferable) referable);
        if (pred.test(aliasRef)) return aliasRef;
      }
    }
    return null;
  }

  private Referable checkSubgroup(ConcreteGroup subgroup, Predicate<Referable> pred) {
    Referable ref = checkReferable(subgroup.referable(), pred);
    if (ref != null) return ref;
    for (InternalReferable internalRef : subgroup.getInternalReferables()) {
      if (internalRef.isVisible()) {
        ref = checkReferable(internalRef, pred);
        if (ref != null) return ref;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    for (ConcreteStatement statement : myGroup.statements()) {
      ConcreteGroup subgroup = statement.group();
      if (subgroup != null) {
        if (context == null || context == ScopeContext.STATIC) {
          Referable ref = checkSubgroup(subgroup, pred);
          if (ref != null) return ref;
        }
        if (context == null || context == ScopeContext.DYNAMIC) {
          for (ConcreteGroup dynamicGroup : subgroup.dynamicGroups()) {
            if (dynamicGroup.referable().getAccessModifier() == AccessModifier.PUBLIC) {
              Referable ref = checkSubgroup(dynamicGroup, pred);
              if (ref != null) return ref;
            }
          }
          for (InternalReferable field : subgroup.getFields()) {
            if (field.isVisible() && field.getAccessModifier() == AccessModifier.PUBLIC) {
              checkReferable(field, pred);
            }
          }
        }
      }
      Concrete.LevelsDefinition pDef = statement.pLevelsDefinition();
      if (pDef != null && (context == null || context == ScopeContext.PLEVEL)) {
        for (Referable referable : pDef.getReferables()) {
          if (pred.test(referable)) return referable;
        }
      }
      Concrete.LevelsDefinition hDef = statement.hLevelsDefinition();
      if (hDef != null && (context == null || context == ScopeContext.HLEVEL)) {
        for (Referable referable : hDef.getReferables()) {
          if (pred.test(referable)) return referable;
        }
      }
    }

    if (context == ScopeContext.DYNAMIC || myDynamicContext && (context == null || context == ScopeContext.STATIC)) {
      for (ConcreteGroup subgroup : myGroup.dynamicGroups()) {
        Referable ref = checkSubgroup(subgroup, pred);
        if (ref != null) return ref;
      }
    }

    if (context == null || context == ScopeContext.STATIC) {
      for (InternalReferable constructor : myGroup.getConstructors()) {
        checkReferable(constructor, pred);
      }
      for (InternalReferable field : myGroup.getFields()) {
        checkReferable(field, pred);
      }
    }

    Scope cachingScope = null;
    for (ConcreteStatement statement : myGroup.statements()) {
      ConcreteNamespaceCommand cmd = statement.command();
      if (cmd == null || !(myWithAdditionalContent || cmd.isImport())) {
        continue;
      }

      Scope scope;
      if (cmd.isImport()) {
        if (myModule != null && cmd.module().getPath().equals(myModule.toList())) {
          continue;
        }
        scope = getImportedSubscope();
      } else {
        if (cachingScope == null) {
          cachingScope = myDynamicContext && !myWithAdditionalContent ? this : CachingScope.make(new LexicalScope(myParent, myGroup, null, true, false));
        }
        scope = cachingScope;
      }
      scope = NamespaceCommandNamespace.resolveNamespace(scope, cmd);
      Referable ref = scope.find(pred, context);
      if (ref != null) return ref;
    }

    if (myWithAdditionalContent && (context == null || context == ScopeContext.STATIC)) {
      for (ParameterReferable ref : myGroup.externalParameters()) {
        if (pred.test(ref)) return ref;
      }
    }

    return myParent.find(pred, context);
  }

  private static GlobalReferable resolveInternal(ConcreteGroup group, String name, boolean onlyInternal) {
    for (InternalReferable internalReferable : group.getInternalReferables()) {
      if (!onlyInternal || internalReferable.isVisible()) {
        if (internalReferable.getRefName().equals(name)) {
          return internalReferable;
        }
        String alias = internalReferable.getAliasName();
        if (alias != null && alias.equals(name)) {
          return new AliasReferable(internalReferable);
        }
      }
    }

    return null;
  }

  private static Object resolveSubgroup(ConcreteGroup group, String name, ResolveType resolveType) {
    GlobalReferable ref = group.referable();
    boolean match = ref.textRepresentation().equals(name);
    if (!match) {
      String alias = ref.getAliasName();
      if (alias != null && alias.equals(name)) {
        if (resolveType == ResolveType.REF) {
          return new AliasReferable(ref);
        }
        match = true;
      }
    }
    if (match) {
      return resolveType == ResolveType.REF ? ref : LexicalScope.opened(group);
    }

    if (resolveType == ResolveType.REF) {
      return resolveInternal(group, name, true);
    }

    return null;
  }

  private enum ResolveType { REF, SCOPE }

  private Object resolve(String name, ResolveType resolveType, ScopeContext context) {
    if (name.isEmpty() || "_".equals(name)) {
      return null;
    }

    for (ConcreteStatement statement : myGroup.statements()) {
      ConcreteGroup subgroup = statement.group();
      if (subgroup != null) {
        if (resolveType != ResolveType.REF || context == null || context == ScopeContext.STATIC) {
          Object result = resolveSubgroup(subgroup, name, resolveType);
          if (result != null) {
            return result;
          }
        }
        if (resolveType == ResolveType.REF && (context == null || context == ScopeContext.DYNAMIC)) {
          for (ConcreteGroup dynamicGroup : subgroup.dynamicGroups()) {
            if (dynamicGroup.referable().getAccessModifier() == AccessModifier.PUBLIC) {
              Object result = resolveSubgroup(dynamicGroup, name, resolveType);
              if (result != null) {
                return result;
              }
            }
          }
          GlobalReferable result = resolveInternal(subgroup, name, true);
          if (result != null && result.getAccessModifier() == AccessModifier.PUBLIC) {
            return result;
          }
        }
      }
      if (context == null || context == ScopeContext.PLEVEL) {
        Concrete.LevelsDefinition levelParams = statement.pLevelsDefinition();
        if (levelParams != null) {
          for (Referable ref : levelParams.getReferables()) {
            if (name.equals(ref.getRefName())) {
              return ref;
            }
          }
        }
      }
      if (context == null || context == ScopeContext.HLEVEL) {
        Concrete.LevelsDefinition levelParams = statement.hLevelsDefinition();
        if (levelParams != null) {
          for (Referable ref : levelParams.getReferables()) {
            if (name.equals(ref.getRefName())) {
              return ref;
            }
          }
        }
      }
    }

    if (context == ScopeContext.DYNAMIC || myDynamicContext && (context == null || context == ScopeContext.STATIC)) {
      for (ConcreteGroup subgroup : myGroup.dynamicGroups()) {
        Object result = resolveSubgroup(subgroup, name, resolveType);
        if (result != null) {
          return result;
        }
      }
    }

    if (resolveType == ResolveType.REF && (context == null || context == ScopeContext.STATIC)) {
      GlobalReferable result = resolveInternal(myGroup, name, false);
      if (result != null) {
        return result;
      }
    }

    Scope cachingScope = null;
    for (ConcreteStatement statement : myGroup.statements()) {
      ConcreteNamespaceCommand cmd = statement.command();
      if (cmd == null || !(myWithAdditionalContent || cmd.isImport())) {
        continue;
      }

      Scope scope;
      if (cmd.isImport()) {
        if (myModule != null && cmd.module().getPath().equals(myModule.toList())) {
          continue;
        }
        scope = getImportedSubscope();
      } else {
        if (cachingScope == null) {
          cachingScope = myDynamicContext && !myWithAdditionalContent ? this : CachingScope.make(new LexicalScope(myParent, myGroup, null, true, false));
        }
        scope = cachingScope;
      }

      scope = NamespaceCommandNamespace.resolveNamespace(scope, cmd);
      Object result = resolveType == ResolveType.REF ? scope.resolveName(name, context) : scope.resolveNamespace(name);
      if (result != null) {
        return result;
      }
    }

    if (myWithAdditionalContent && resolveType == ResolveType.REF) {
      List<? extends Referable> refs = myGroup.externalParameters();
      for (int i = refs.size() - 1; i >= 0; i--) {
        Referable ref = refs.get(i);
        if (ref != null && ref.getRefName().equals(name)) {
          return ref;
        }
      }
    }

    return resolveType == ResolveType.REF ? myParent.resolveName(name, context) : myParent.resolveNamespace(name);
  }

  @Nullable
  @Override
  public Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    Object result = resolve(name, ResolveType.REF, context);
    return result instanceof Referable ? (Referable) result : null;
  }

  @Nullable
  @Override
  public Scope resolveNamespace(@NotNull String name) {
    Object result = resolve(name, ResolveType.SCOPE, null);
    return result instanceof Scope ? (Scope) result : null;
  }

  @NotNull
  @Override
  public Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
    return myWithAdditionalContent ? new LexicalScope(myParent, myGroup, null, myDynamicContext, false) : this;
  }

  @Override
  public @Nullable ImportedScope getImportedSubscope() {
    return myParent.getImportedSubscope();
  }
}
