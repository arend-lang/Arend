package org.arend.naming.scope;

import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.prelude.Prelude;
import org.arend.term.abs.Abstract;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// TODO[server2]: Delete obsolete functions and move others somewhere else
public class ScopeFactory {
  public static @NotNull Scope forGroup(@Nullable ConcreteGroup group, @NotNull ModuleScopeProvider moduleScopeProvider) {
    return forGroup(group, moduleScopeProvider, true, false);
  }

  public static @NotNull Scope parentScopeForGroup(@Nullable ConcreteGroup group, @NotNull ModuleScopeProvider moduleScopeProvider, boolean prelude) {
    if (prelude && group != null) {
      for (ConcreteStatement statement : group.statements()) {
        ConcreteNamespaceCommand cmd = statement.command();
        if (cmd != null && cmd.isImport() && cmd.module().getPath().equals(Prelude.MODULE_PATH.toList())) {
          prelude = false;
        }
      }
    }

    Scope preludeScope = prelude ? moduleScopeProvider.forModule(Prelude.MODULE_PATH) : null;
    if (group == null) {
      return preludeScope == null ? EmptyScope.INSTANCE : preludeScope;
    }

    ImportedScope importedScope = new ImportedScope(group, moduleScopeProvider);
    return preludeScope == null ? importedScope : new MergeScope(preludeScope, importedScope);
  }

  public static @NotNull Scope forGroup(@Nullable ConcreteGroup group, @NotNull ModuleScopeProvider moduleScopeProvider, boolean prelude, boolean isDynamicContext) {
    Scope parent = parentScopeForGroup(group, moduleScopeProvider, prelude);
    return group == null ? parent : LexicalScope.insideOf(group, parent, isDynamicContext);
  }

}
