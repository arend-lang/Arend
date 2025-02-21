package org.arend.naming.scope;

import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.prelude.Prelude;
import org.arend.term.NamespaceCommand;
import org.arend.term.abs.Abstract;
import org.arend.term.group.ConcreteGroup;
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
        NamespaceCommand cmd = statement.command();
        if (cmd != null && cmd.getKind() == NamespaceCommand.Kind.IMPORT && cmd.getPath().equals(Prelude.MODULE_PATH.toList())) {
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

  public static boolean isGlobalScopeVisible(Abstract.SourceNode sourceNode) {
    while (sourceNode != null && !(sourceNode instanceof Abstract.Definition || sourceNode instanceof Abstract.NamespaceCommandHolder)) {
      // We cannot use any references in level expressions
      if (sourceNode instanceof Abstract.LevelExpression) {
        return false;
      }

      if (sourceNode instanceof Abstract.Pattern) {
        return true;
      }

      Abstract.SourceNode parentSourceNode = sourceNode.getParentSourceNode();
      if (parentSourceNode instanceof Abstract.Expression && sourceNode instanceof Abstract.Reference) {
        sourceNode = parentSourceNode;
        parentSourceNode = sourceNode.getParentSourceNode();
      }

      // After namespace command
      if (parentSourceNode instanceof Abstract.NamespaceCommandHolder && sourceNode instanceof Abstract.Reference) {
        if (((Abstract.NamespaceCommandHolder) parentSourceNode).getKind() == NamespaceCommand.Kind.IMPORT) {
          return false;
        }
        return sourceNode.equals(((Abstract.NamespaceCommandHolder) parentSourceNode).getOpenedReference());
      }

      // After a dot
      if (parentSourceNode instanceof Abstract.LongReference && sourceNode instanceof Abstract.Reference) {
        Abstract.Reference headRef = ((Abstract.LongReference) parentSourceNode).getHeadReference();
        if (headRef != null && !sourceNode.equals(headRef)) {
          return false;
        }
      } else

      // We cannot use any references in the universe of a data type
      if (parentSourceNode instanceof Abstract.DataDefinition && sourceNode instanceof Abstract.Expression) {
        return false;
      } else

      if (parentSourceNode instanceof Abstract.EliminatedExpressionsHolder) {
        // We can use only parameters in eliminated expressions
        if (sourceNode instanceof Abstract.Reference) {
          Collection<? extends Abstract.Reference> elimExprs = ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getEliminatedExpressions();
          if (elimExprs != null) {
            for (Abstract.Reference elimExpr : elimExprs) {
              if (sourceNode.equals(elimExpr)) {
                return false;
              }
            }
          }
        }
      } else

      // Class extensions
      if (parentSourceNode instanceof Abstract.ClassFieldImpl && sourceNode instanceof Abstract.Reference && parentSourceNode.getParentSourceNode() instanceof Abstract.ClassReferenceHolder) {
        return false;
      }

      sourceNode = parentSourceNode;
    }

    return true;
  }
}
