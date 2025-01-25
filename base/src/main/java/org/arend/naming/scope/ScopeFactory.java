package org.arend.naming.scope;

import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.local.ListScope;
import org.arend.naming.scope.local.*;
import org.arend.prelude.Prelude;
import org.arend.term.NamespaceCommand;
import org.arend.term.abs.Abstract;
import org.arend.term.abs.AbstractParameterPattern;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.group.ChildGroup;
import org.arend.term.group.Group;
import org.arend.term.group.Statement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class ScopeFactory {
  public static @NotNull Scope forGroup(@Nullable Group group, @NotNull ModuleScopeProvider moduleScopeProvider) {
    return forGroup(group, moduleScopeProvider, true, false);
  }

  public static @NotNull Scope forGroup(@Nullable Group group, @NotNull ModuleScopeProvider moduleScopeProvider, boolean prelude) {
    return forGroup(group, moduleScopeProvider, prelude, false);
  }

  public static @NotNull Scope parentScopeForGroup(@Nullable Group group, @NotNull ModuleScopeProvider moduleScopeProvider, boolean prelude) {
    ChildGroup parentGroup = group instanceof ChildGroup ? ((ChildGroup) group).getParentGroup() : null;
    Scope parentScope;
    if (parentGroup == null) {
      if (prelude && group != null) {
        for (Statement statement : group.getStatements()) {
          NamespaceCommand cmd = statement.getNamespaceCommand();
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
      parentScope = preludeScope == null ? importedScope : new MergeScope(preludeScope, importedScope);
    } else {
      parentScope = forGroup(parentGroup, moduleScopeProvider, prelude, ((ChildGroup) group).isDynamicContext());
    }
    return parentScope;
  }

  public static @NotNull Scope forGroup(@Nullable Group group, @NotNull ModuleScopeProvider moduleScopeProvider, boolean prelude, boolean isDynamicContext) {
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

  public static Scope forSourceNode(Scope parentScope, Abstract.SourceNode sourceNode, Scope importElements, Function<ClassReferable, Scope> classFieldImplScope) {
    if (sourceNode == null) {
      return parentScope;
    }

    // We cannot use any references in level expressions
    if (sourceNode instanceof Abstract.LevelExpression) {
      return EmptyScope.INSTANCE;
    }

    Abstract.SourceNode parentSourceNode = sourceNode.getParentSourceNode();
    if (parentSourceNode instanceof Abstract.Expression && sourceNode instanceof Abstract.Reference) {
      sourceNode = parentSourceNode;
      parentSourceNode = sourceNode.getParentSourceNode();
    }

    if (sourceNode instanceof Abstract.Pattern) {
      return parentScope.getGlobalSubscope();
    }

    // After namespace command
    if (parentSourceNode instanceof Abstract.NamespaceCommandHolder && sourceNode instanceof Abstract.Reference) {
      Scope scope;
      if (((Abstract.NamespaceCommandHolder) parentSourceNode).getKind() == NamespaceCommand.Kind.IMPORT) {
        ImportedScope importedScope = parentScope.getImportedSubscope();
        if (importedScope != null && importElements != null) {
          importedScope = new ImportedScope(importedScope, importElements);
        }
        scope = importedScope == null ? (importElements == null ? EmptyScope.INSTANCE : importElements) : importedScope;
      } else {
        scope = parentScope.getGlobalSubscopeWithoutOpens(true);
      }
      if (sourceNode.equals(((Abstract.NamespaceCommandHolder) parentSourceNode).getOpenedReference())) {
        return scope;
      } else {
        scope = scope.resolveNamespace(((Abstract.NamespaceCommandHolder) parentSourceNode).getPath());
        return scope == null ? EmptyScope.INSTANCE : scope;
      }
    }

    // After a dot
    if (parentSourceNode instanceof Abstract.LongReference && sourceNode instanceof Abstract.Reference) {
      Abstract.Reference headRef = ((Abstract.LongReference) parentSourceNode).getHeadReference();
      if (headRef == null || sourceNode.equals(headRef)) {
        return parentScope;
      }

      List<AbstractReference> references = new ArrayList<>();
      List<String> path = new ArrayList<>();
      Object headData = headRef.getData();
      references.add(headData instanceof AbstractReference ? (AbstractReference) headData : null);
      path.add(headRef.getReferent().textRepresentation());
      for (Abstract.Reference reference : ((Abstract.LongReference) parentSourceNode).getTailReferences()) {
        if (reference == null) {
          return EmptyScope.INSTANCE;
        }
        if (sourceNode.equals(reference)) {
          return new LongUnresolvedReference(sourceNode, references, path).resolveNamespaceWithArgument(parentScope);
        }
        Object refData = reference.getData();
        references.add(refData instanceof AbstractReferable ? (AbstractReference) refData : null);
        path.add(reference.getReferent().textRepresentation());
      }

      return EmptyScope.INSTANCE;
    }

    // We cannot use any references in the universe of a data type
    if (parentSourceNode instanceof Abstract.DataDefinition && sourceNode instanceof Abstract.Expression) {
      return EmptyScope.INSTANCE;
    }

    if (parentSourceNode instanceof Abstract.EliminatedExpressionsHolder) {
      // We can use only parameters in eliminated expressions
      if (sourceNode instanceof Abstract.Reference) {
        Collection<? extends Abstract.Reference> elimExprs = ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getEliminatedExpressions();
        if (elimExprs != null) {
          for (Abstract.Reference elimExpr : elimExprs) {
            if (sourceNode.equals(elimExpr)) {
              return TelescopeScope.make(EmptyScope.INSTANCE, ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getParameters());
            }
          }
        }
      }

      // Remove eliminated expressions from the scope in clauses
      if (sourceNode instanceof Abstract.Clause) {
        Collection<? extends Abstract.Reference> elimExprs = ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getEliminatedExpressions();
        if (elimExprs != null) {
          if (elimExprs.isEmpty()) {
            return parentScope;
          } else {
            List<Referable> excluded = new ArrayList<>(elimExprs.size());
            Scope parametersScope = TelescopeScope.make(EmptyScope.INSTANCE, ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getParameters());
            for (Abstract.Reference elimExpr : elimExprs) {
              Referable ref = ExpressionResolveNameVisitor.resolve(elimExpr.getReferent(), parametersScope);
              if (!(ref == null || ref instanceof ErrorReference)) {
                excluded.add(ref);
              }
            }
            return TelescopeScope.make(parentScope, ((Abstract.EliminatedExpressionsHolder) parentSourceNode).getParameters(), excluded);
          }
        }
      }
    }

    // Replace the scope with class fields in class extensions
    if (parentSourceNode instanceof Abstract.ClassFieldImpl && sourceNode instanceof Abstract.Reference) {
      Abstract.SourceNode parentParent = parentSourceNode.getParentSourceNode();
      if (parentParent instanceof Abstract.ClassReferenceHolder) {
        ClassReferable classRef = ((Abstract.ClassReferenceHolder) parentParent).getClassReference();
        if (classRef == null) {
          return EmptyScope.INSTANCE;
        }
        Scope result = classFieldImplScope.apply(classRef);
        return new MergeScope(true, result != null ? result : new ClassFieldImplScope(classRef, true), parentScope);
      }
    }

    // Parameters are not visible in \extends
    if (parentSourceNode instanceof Abstract.ClassDefinition && sourceNode instanceof Abstract.Reference) {
      return Prelude.DEP_ARRAY == null ? parentScope : new ElimScope(parentScope, Collections.singleton(Prelude.DEP_ARRAY.getRef()));
    }

    // Extend the scope with parameters
    if (parentSourceNode instanceof Abstract.ParametersHolder) {
      if (parentSourceNode instanceof Abstract.LamParametersHolder) {
        List<? extends Abstract.LamParameter> parameters = ((Abstract.LamParametersHolder) parentSourceNode).getLamParameters();
        boolean hasPatterns = false;
        for (Abstract.LamParameter parameter : parameters) {
          if (parameter instanceof Abstract.Pattern) {
            hasPatterns = true;
            break;
          }
        }
        if (hasPatterns) {
          List<Abstract.Pattern> patterns = new ArrayList<>();
          for (Abstract.LamParameter parameter : parameters) {
            if (parameter instanceof Abstract.Pattern) {
              patterns.add((Abstract.Pattern) parameter);
            } else if (parameter instanceof Abstract.Parameter) {
              for (Referable referable : ((Abstract.Parameter) parameter).getReferableList()) {
                patterns.add(new AbstractParameterPattern((Abstract.Parameter) parameter, referable));
              }
            }
          }
          return new PatternScope(parentScope, patterns);
        }
      }

      List<? extends Abstract.Parameter> parameters = ((Abstract.ParametersHolder) parentSourceNode).getParameters();
      if (sourceNode instanceof Abstract.Parameter && !(sourceNode instanceof Abstract.Expression)) {
        List<Abstract.Parameter> parameters1 = new ArrayList<>(parameters.size());
        for (Abstract.Parameter parameter : parameters) {
          if (sourceNode.equals(parameter)) {
            break;
          }
          parameters1.add(parameter);
        }
        return TelescopeScope.make(parentScope, parameters1);
      } else {
        return TelescopeScope.make(parentScope, parameters);
      }
    }

    // Extend the scope with case arguments and remove eliminated variables from case clauses
    if ((sourceNode instanceof Abstract.Expression || sourceNode instanceof Abstract.Clause) && (parentSourceNode instanceof Abstract.CaseArgumentsHolder || parentSourceNode instanceof Abstract.CaseArgument && sourceNode.equals(((Abstract.CaseArgument) parentSourceNode).getType()))) {
      Abstract.CaseArgumentsHolder caseArgumentsHolder;
      if (parentSourceNode instanceof Abstract.CaseArgumentsHolder) {
        caseArgumentsHolder = (Abstract.CaseArgumentsHolder) parentSourceNode;
      } else {
        Abstract.SourceNode parentParent = parentSourceNode.getParentSourceNode();
        if (!(parentParent instanceof Abstract.CaseArgumentsHolder)) {
          return parentScope;
        }
        caseArgumentsHolder = (Abstract.CaseArgumentsHolder) parentParent;
      }

      Set<Referable> eliminatedRefs = null;
      List<Referable> referables = null;
      List<? extends Abstract.CaseArgument> caseArguments = caseArgumentsHolder.getCaseArguments();
      for (Abstract.CaseArgument caseArgument : caseArguments) {
        if (parentSourceNode instanceof Abstract.CaseArgument && parentSourceNode.equals(caseArgument)) {
          break;
        }

        if (sourceNode instanceof Abstract.Expression) {
          Referable ref = caseArgument.getReferable();
          if (ref != null) {
            if (referables == null) {
              referables = new ArrayList<>();
            }
            referables.add(ref);
          }
        }

        if (sourceNode instanceof Abstract.Clause) {
          Abstract.Reference elimRef = caseArgument.getEliminatedReference();
          Referable resolveRef = elimRef == null ? null : ExpressionResolveNameVisitor.resolve(elimRef.getReferent(), parentScope);
          if (!(resolveRef == null || resolveRef instanceof ErrorReference)) {
            if (eliminatedRefs == null) {
              eliminatedRefs = new HashSet<>();
            }
            eliminatedRefs.add(resolveRef);
          }
        }
      }

      return referables != null ? new ListScope(parentScope, referables) : eliminatedRefs != null ? new ElimScope(parentScope, eliminatedRefs) : parentScope;
    }

    // Extend the scope with let clauses
    if (parentSourceNode instanceof Abstract.LetClausesHolder) {
      Collection<? extends Abstract.LetClause> clauses = ((Abstract.LetClausesHolder) parentSourceNode).getLetClauses();
      List<Abstract.LetClause> clauses1;
      if (sourceNode instanceof Abstract.LetClause) {
        clauses1 = new ArrayList<>(clauses.size());
        for (Abstract.LetClause clause : clauses) {
          if (sourceNode.equals(clause)) {
            break;
          }
          clauses1.add(clause);
        }
      } else {
        clauses1 = new ArrayList<>(clauses);
      }
      return new LetScope(parentScope, clauses1);
    }

    // Extend the scope with patterns
    if (parentSourceNode instanceof Abstract.Clause) {
      return new PatternScope(parentScope, ((Abstract.Clause) parentSourceNode).getPatterns());
    }

    // Extend the scope with previous patterns for the type in a pattern
   if (sourceNode instanceof Abstract.Expression && parentSourceNode instanceof Abstract.Pattern) {
     Abstract.SourceNode parentParentSourceNode = parentSourceNode.getParentSourceNode();
     List<? extends Abstract.Pattern> patterns = parentParentSourceNode instanceof Abstract.Clause clause ? clause.getPatterns() : parentParentSourceNode instanceof Abstract.Pattern pattern ? pattern.getSequence() : Collections.emptyList();
     if (!patterns.isEmpty()) {
       List<Abstract.Pattern> newPatterns = new ArrayList<>(patterns.size());
       for (Abstract.Pattern pattern : patterns) {
         if (pattern == parentSourceNode) {
           break;
         }
         newPatterns.add(pattern);
       }
       if (!newPatterns.isEmpty()) {
         return new PatternScope(parentScope, newPatterns);
       }
     }
   }

    return parentScope;
  }
}
