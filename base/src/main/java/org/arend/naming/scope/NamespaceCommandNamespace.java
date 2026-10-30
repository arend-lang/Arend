package org.arend.naming.scope;

import org.arend.naming.reference.*;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NamespaceCommandNamespace implements Scope {
  private final Scope myModuleNamespace;
  private final ConcreteNamespaceCommand myNamespaceCommand;

  private NamespaceCommandNamespace(Scope moduleNamespace, ConcreteNamespaceCommand namespaceCommand) {
    myNamespaceCommand = namespaceCommand;
    myModuleNamespace = moduleNamespace;
  }

  public static @NotNull Scope resolveNamespace(Scope parentScope, ConcreteNamespaceCommand cmd) {
    if (cmd.renamings().isEmpty() && !cmd.isUsing()) {
      return EmptyScope.INSTANCE;
    }
    List<String> path = cmd.module().getPath();
    if (path.isEmpty()) {
      return EmptyScope.INSTANCE;
    }
    parentScope = parentScope == null ? null : parentScope.resolveNamespace(path);
    return parentScope == null ? EmptyScope.INSTANCE : new NamespaceCommandNamespace(parentScope, cmd);
  }

  private Referable resolve(NamedUnresolvedReference ref, ScopeContext context) {
    return ref.resolve(new StaticOrExtensionScope(new PrivateFilteredScope(myModuleNamespace, true)), null, context, null);
  }

  /**
   * Names in namespace commands without a context refer to extension functions as well as to static definitions.
   */
  private static boolean matches(ScopeContext entryContext, @Nullable ScopeContext context) {
    return context == null || entryContext == context || entryContext == ScopeContext.STATIC && context == ScopeContext.EXTENSION;
  }

  private static ScopeContext getContext(Referable ref, ScopeContext entryContext) {
    return entryContext == ScopeContext.STATIC && ref instanceof GlobalReferable global && global.isDynamicExtension() ? ScopeContext.EXTENSION : entryContext;
  }

  @NotNull
  @Override
  public Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    Set<String> hidden = new HashSet<>();
    for (ConcreteNamespaceCommand.NameHiding hiddenElement : myNamespaceCommand.hidings()) {
      if (matches(hiddenElement.scopeContext(), context)) {
        hidden.add(hiddenElement.reference().getRefName());
      }
    }

    List<Referable> elements = new ArrayList<>();
    for (ConcreteNamespaceCommand.NameRenaming renaming : myNamespaceCommand.renamings()) {
      ScopeContext renamingContext = renaming.scopeContext();
      if (!matches(renamingContext, context)) {
        continue;
      }
      Referable oldRef = resolve(renaming.reference(), renamingContext);
      if (!(context == null || getContext(oldRef, renamingContext) == context)) {
        continue;
      }
      if (!(oldRef instanceof ErrorReference || oldRef instanceof GlobalReferable && ((GlobalReferable) oldRef).getAccessModifier() == AccessModifier.PRIVATE)) {
        String newName = renaming.newName();
        String name = newName != null ? newName : oldRef.textRepresentation();
        if (!hidden.contains(name)) {
          elements.add(newName != null ? new RedirectingReferableImpl(oldRef, renaming.newPrecedence(), newName) : oldRef);
        }
        hidden.add(oldRef.textRepresentation());
      }
    }

    if (myNamespaceCommand.isUsing()) {
      elemLoop:
      for (Referable ref : myModuleNamespace.getElements(context)) {
        if (hidden.contains(ref.textRepresentation()) || ref instanceof GlobalReferable && ((GlobalReferable) ref).getAccessModifier() != AccessModifier.PUBLIC) {
          continue;
        }

        for (ConcreteNamespaceCommand.NameRenaming renaming : myNamespaceCommand.renamings()) {
          if (renaming.reference().getRefName().equals(ref.getRefName())) {
            continue elemLoop;
          }
        }

        elements.add(ref);
      }
    }

    return elements;
  }

  private boolean isHidden(String name, ScopeContext context) {
    for (ConcreteNamespaceCommand.NameHiding hiddenRef : myNamespaceCommand.hidings()) {
      if (matches(hiddenRef.scopeContext(), context) && hiddenRef.reference().getRefName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isHiddenByUsing(String name) {
    if (!myNamespaceCommand.isUsing()) {
      return true;
    }

    for (ConcreteNamespaceCommand.NameRenaming renaming : myNamespaceCommand.renamings()) {
      if (renaming.reference().getRefName().equals(name)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  @Override
  public Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    if (isHidden(name, context)) {
      return null;
    }

    for (ConcreteNamespaceCommand.NameRenaming renaming : myNamespaceCommand.renamings()) {
      ScopeContext renamingContext = renaming.scopeContext();
      if (!matches(renamingContext, context)) {
        continue;
      }

      String newName = renaming.newName();
      NamedUnresolvedReference oldRef = renaming.reference();
      if (name.equals(newName) || newName == null && oldRef.getRefName().equals(name)) {
        Referable ref = resolve(oldRef, renamingContext);
        if (!(context == null || getContext(ref, renamingContext) == context)) {
          continue;
        }
        return ref instanceof ErrorReference ? null : newName != null ? new RedirectingReferableImpl(ref, renaming.newPrecedence(), newName) : ref;
      }
    }

    if (isHiddenByUsing(name)) return null;
    Referable ref = myModuleNamespace.resolveName(name, context);
    return ref instanceof GlobalReferable && ((GlobalReferable) ref).getAccessModifier() != AccessModifier.PUBLIC ? null : ref;
  }

  @Nullable
  @Override
  public Scope resolveNamespace(@NotNull String name) {
    if (isHidden(name, ScopeContext.STATIC)) {
      return null;
    }

    for (ConcreteNamespaceCommand.NameRenaming renaming : myNamespaceCommand.renamings()) {
      if (!renaming.isStatic()) {
        continue;
      }

      String newName = renaming.newName();
      NamedUnresolvedReference oldRef = renaming.reference();
      if (newName == null) {
        newName = resolve(oldRef, ScopeContext.STATIC).getRefName();
      }
      if (newName.equals(name)) {
        return myModuleNamespace.resolveNamespace(oldRef.getRefName());
      }
    }

    return isHiddenByUsing(name) ? null : myModuleNamespace.resolveNamespace(name);
  }
}
