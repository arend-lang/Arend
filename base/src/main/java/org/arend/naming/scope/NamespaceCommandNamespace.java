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
    return ref.resolve(new PrivateFilteredScope(myModuleNamespace, true), null, context, null);
  }

  @NotNull
  @Override
  public Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    Set<String> hidden = new HashSet<>();
    for (ConcreteNamespaceCommand.NameHiding hiddenElement : myNamespaceCommand.hidings()) {
      if (context == null || hiddenElement.scopeContext() == context) {
        hidden.add(hiddenElement.reference().getRefName());
      }
    }

    List<Referable> elements = new ArrayList<>();
    for (ConcreteNamespaceCommand.NameRenaming renaming : myNamespaceCommand.renamings()) {
      ScopeContext renamingContext = renaming.scopeContext();
      if (!(context == null || context == renamingContext)) {
        continue;
      }
      Referable oldRef = resolve(renaming.reference(), renamingContext);
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
      if ((context == null || context == hiddenRef.scopeContext()) && hiddenRef.reference().getRefName().equals(name)) {
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
      if (!(context == null || context == renamingContext)) {
        continue;
      }

      String newName = renaming.newName();
      NamedUnresolvedReference oldRef = renaming.reference();
      if (name.equals(newName) || newName == null && oldRef.getRefName().equals(name)) {
        Referable ref = resolve(oldRef, renamingContext);
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
      if (renaming.scopeContext() != ScopeContext.STATIC) {
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
