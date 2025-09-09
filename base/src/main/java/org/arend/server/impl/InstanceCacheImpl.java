package org.arend.server.impl;

import org.arend.core.definition.ClassDefinition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.Expression;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.ext.instance.SubclassSearchParameters;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.typing.DynamicScopeProvider;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.scope.DynamicScope;
import org.arend.server.InstanceCache;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.instance.pool.GlobalInstancePool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InstanceCacheImpl implements InstanceCache {
  private final Map<TCDefReferable, Set<TCDefReferable>> myCache = new HashMap<>();
  private final Map<TCDefReferable, TCDefReferable> myInstances = new HashMap<>();

  private void addInstance(TCDefReferable classRef, TCDefReferable instanceRef) {
    myInstances.put(instanceRef, classRef);
    myCache.computeIfAbsent(classRef, k -> new HashSet<>()).add(instanceRef);
  }

  public synchronized void addInstances(GroupData groupData, TypingInfo typingInfo) {
    groupData.getRawGroup().traverseGroup(subgroup -> {
      if (subgroup.definition() instanceof Concrete.FunctionDefinition function && function.getKind() == FunctionKind.INSTANCE) {
        DefinitionData definitionData = groupData.getDefinitionData(subgroup.referable().getRefLongName());
        if (definitionData != null && definitionData.definition() instanceof Concrete.FunctionDefinition funDef) {
          DynamicScopeProvider provider = funDef.getResultType() == null ? null : typingInfo.getBodyDynamicScopeProvider(funDef.getResultType());
          if (provider != null) {
            if (provider.getReferable() instanceof TCDefReferable classRef) {
              addInstance(classRef, funDef.getRef());
            }
            for (Referable element : new DynamicScope(provider, typingInfo, DynamicScope.Extent.WITH_SUPER).getElements()) {
              if (element instanceof TCDefReferable classRef && classRef.getKind().isRecord()) {
                addInstance(classRef, funDef.getRef());
              }
            }
          }
        }
      }
    });
  }

  private void removeInstance(TCDefReferable instanceRef) {
    TCDefReferable classRef = myInstances.remove(instanceRef);
    if (classRef != null) {
      Set<TCDefReferable> instances = myCache.get(classRef);
      if (instances != null) {
        instances.remove(instanceRef);
        if (instances.isEmpty()) {
          myCache.remove(classRef);
        }
      }
    }
  }

  public synchronized void removeInstances(ConcreteGroup group) {
    group.traverseGroup(subgroup -> {
      if (subgroup.definition() instanceof Concrete.FunctionDefinition function && function.getKind() == FunctionKind.INSTANCE) {
        removeInstance(function.getRef());
      }
    });
  }

  @Override
  public boolean hasInstances(@NotNull TCDefReferable classRef) {
    return myCache.containsKey(classRef);
  }

  /**
   * Finds available instances for the given class.
   *
   * @param classRef                a class reference.
   * @param classifyingExpression   the classifying expression of the required instance; if {@code null}, all available instances are returned.
   * @return the list of possible solutions. Each solution is a list of instances that are required for the solution to work.
   */
  @Override
  @NotNull
  public List<List<TCDefReferable>> getAvailableInstances(@NotNull TCDefReferable classRef, @Nullable Expression classifyingExpression) {
    Set<TCDefReferable> instances = myCache.get(classRef);
    if (instances == null) return Collections.emptyList();

    ClassDefinition classDef = classRef.getTypechecked() instanceof ClassDefinition typechecked ? typechecked : null;
    List<FunctionDefinition> typecheckedInstances = new ArrayList<>();
    List<List<TCDefReferable>> result = new ArrayList<>();
    for (TCDefReferable instance : instances) {
      if (classDef != null && classifyingExpression != null && instance.getTypechecked() instanceof FunctionDefinition typechecked) {
        typecheckedInstances.add(typechecked);
      } else {
        result.add(Collections.singletonList(instance));
      }
    }

    List<List<TCDefReferable>> typecheckedResult = new ArrayList<>();
    if (!typecheckedInstances.isEmpty()) {
      Concrete.Expression expr = new GlobalInstancePool(typecheckedInstances, null).findInstance(classifyingExpression, new SubclassSearchParameters(classDef), null, null, null);
      List<TCDefReferable> instanceRefs = new ArrayList<>();
      while (expr instanceof Concrete.AppExpression appExpr) {
        if (appExpr.getArguments().getFirst().expression instanceof Concrete.ReferenceExpression refExpr && refExpr.getReferent() instanceof TCDefReferable instanceRef) {
          instanceRefs.add(instanceRef);
        }
        expr = appExpr.getFunction();
      }
      if (expr instanceof Concrete.ReferenceExpression refExpr && refExpr.getReferent() instanceof TCDefReferable instanceRef) {
        instanceRefs.add(instanceRef);
      }
      if (!instanceRefs.isEmpty()) {
        typecheckedResult.add(instanceRefs);
      }
    }

    return typecheckedResult.isEmpty() ? result : typecheckedResult;
  }
}
