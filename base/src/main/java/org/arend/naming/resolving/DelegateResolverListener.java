package org.arend.naming.resolving;

import org.arend.module.ModuleLocation;
import org.arend.naming.reference.Referable;
import org.arend.term.NameRenaming;
import org.arend.term.NamespaceCommand;
import org.arend.term.concrete.Concrete;

import java.util.Collection;
import java.util.List;

public class DelegateResolverListener implements ResolverListener {
  private final ResolverListener myResolverListener;

  public DelegateResolverListener(ResolverListener resolverListener) {
    myResolverListener = resolverListener;
  }

  @Override
  public void bindingResolved(Referable binding) {
    myResolverListener.bindingResolved(binding);
  }

  @Override
  public void referenceResolved(Concrete.Expression expr, Referable originalRef, Concrete.ReferenceExpression refExpr, List<Referable> resolvedRefs) {
    myResolverListener.referenceResolved(expr, originalRef, refExpr, resolvedRefs);
  }

  @Override
  public void fieldCallResolved(Concrete.FieldCallExpression expr, Referable originalRef, Referable resolvedRef) {
    myResolverListener.fieldCallResolved(expr, originalRef, resolvedRef);
  }

  @Override
  public void patternParsed(Concrete.ConstructorPattern pattern) {
    myResolverListener.patternParsed(pattern);
  }

  @Override
  public void patternResolved(Referable originalRef, Referable newRef, Concrete.Pattern pattern, List<Referable> resolvedRefs) {
    myResolverListener.patternResolved(originalRef, newRef, pattern, resolvedRefs);
  }

  @Override
  public void coPatternResolved(Concrete.CoClauseElement classFieldImpl, Referable originalRef, Referable referable, List<Referable> resolvedRefs) {
    myResolverListener.coPatternResolved(classFieldImpl, originalRef, referable, resolvedRefs);
  }

  @Override
  public void overriddenFieldResolved(Concrete.OverriddenField overriddenField, Referable originalRef, Referable referable, List<Referable> resolvedRefs) {
    myResolverListener.overriddenFieldResolved(overriddenField, originalRef, referable, resolvedRefs);
  }

  @Override
  public void namespaceResolved(NamespaceCommand namespaceCommand, List<Referable> resolvedRefs) {
    myResolverListener.namespaceResolved(namespaceCommand, resolvedRefs);
  }

  @Override
  public void renamingResolved(NameRenaming renaming, Referable originalRef, Referable resolvedRef) {
    myResolverListener.renamingResolved(renaming, originalRef, resolvedRef);
  }

  @Override
  public void metaResolved(Concrete.ReferenceExpression expression, List<Concrete.Argument> arguments, Concrete.Expression result, Concrete.Coclauses coclauses, Concrete.FunctionClauses clauses) {
    myResolverListener.metaResolved(expression, arguments, result, coclauses, clauses);
  }

  @Override
  public void levelResolved(Referable originalRef, Concrete.VarLevelExpression refExpr, Referable resolvedRef, Collection<Referable> availableRefs) {
    myResolverListener.levelResolved(originalRef, refExpr, resolvedRef, availableRefs);
  }

  @Override
  public void definitionResolved(Concrete.ResolvableDefinition definition) {
    myResolverListener.definitionResolved(definition);
  }

  @Override
  public void moduleResolved(ModuleLocation module) {
    myResolverListener.moduleResolved(module);
  }
}