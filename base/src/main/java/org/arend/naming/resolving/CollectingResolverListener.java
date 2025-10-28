package org.arend.naming.resolving;

import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.DataContainer;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.*;
import org.arend.server.ArendServerRequester;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteNamespaceCommand;

import java.util.*;

public class CollectingResolverListener implements ResolverListener {
  public record ResolvedReference(AbstractReference reference, Referable referable) {}

  public record ModuleCacheStructure(List<ResolvedReference> cache, List<ModulePath> importedModules) {
    void addReference(AbstractReference reference, Referable referable) {
      if (reference != null) {
        cache.add(new ResolvedReference(reference, referable));
      }
    }

    void addImportedModule(ModulePath module) {
      importedModules.add(module);
    }
  }

  public ModuleLocation moduleLocation;
  private final ArendServerRequester myRequester;
  private final Map<ModuleLocation, ModuleCacheStructure> myModuleCache = new HashMap<>();
  private final boolean myCacheReferences;

  public CollectingResolverListener(ArendServerRequester requester, boolean cacheReferences) {
    myRequester = requester;
    myCacheReferences = cacheReferences;
  }

  public ModuleCacheStructure getCacheStructure(ModuleLocation module) {
    return myModuleCache.get(module);
  }

  private void cacheReference(Object data, Referable referable) {
    if (data instanceof AbstractReference) {
      myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>()))
        .addReference((AbstractReference) data, referable instanceof ErrorReference ? TCDefReferable.NULL_REFERABLE : referable);
    }
  }

  private void cacheReference(UnresolvedReference reference, Referable referable, List<Referable> resolvedRefs) {
    if (!myCacheReferences) return;
    if (reference instanceof LongUnresolvedReference && resolvedRefs != null) {
      List<AbstractReference> referenceList = reference.getReferenceList();
      for (int i = 0; i < referenceList.size() && i < resolvedRefs.size(); i++) {
        if (referenceList.get(i) != null) {
          Referable abstractRef = resolvedRefs.get(i);
          if (abstractRef != null) {
            myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>()))
              .addReference(referenceList.get(i), abstractRef instanceof ErrorReference ? TCDefReferable.NULL_REFERABLE : abstractRef);
          }
        }
      }
      for (int i = resolvedRefs.size(); i < referenceList.size(); i++) {
        myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>()))
          .addReference(referenceList.get(i), TCDefReferable.NULL_REFERABLE);
      }
    } else {
      cacheReference(reference.getData(), referable);
    }
  }

  @Override
  public void bindingResolved(Referable binding) {
    if (binding instanceof DataContainer container) {
      cacheReference(container.getData(), binding);
    }
  }

  @Override
  public void referenceResolved(Concrete.Expression expr, Referable originalRef, Concrete.ReferenceExpression refExpr, List<Referable> resolvedRefs) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, refExpr.getReferent(), resolvedRefs);
    }
  }

  @Override
  public void fieldCallResolved(Concrete.FieldCallExpression expr, Referable originalRef, Referable resolvedRef) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, resolvedRef, Collections.singletonList(resolvedRef));
    }
  }

  @Override
  public void patternResolved(Referable originalRef, Referable newRef, Concrete.Pattern pattern, List<Referable> resolvedRefs) {
    cacheReference(originalRef instanceof UnresolvedReference ? (UnresolvedReference) originalRef :
      new NamedUnresolvedReference(originalRef.getAbstractReferable(), originalRef.getRefName()), newRef, resolvedRefs);
  }

  @Override
  public void coPatternResolved(Concrete.CoClauseElement element, Referable originalRef, Referable referable, List<Referable> resolvedRefs) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, referable, resolvedRefs);
    }
  }

  @Override
  public void overriddenFieldResolved(Concrete.OverriddenField overriddenField, Referable originalRef, Referable referable, List<Referable> resolvedRefs) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, referable, resolvedRefs);
    }
  }

  @Override
  public void levelResolved(Referable originalRef, Concrete.VarLevelExpression refExpr, Referable resolvedRef, Collection<Referable> availableRefs) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, refExpr.getReferent(), null);
    }
  }

  @Override
  public void namespaceResolved(ConcreteNamespaceCommand namespaceCommand, List<Referable> resolvedRefs) {
    if (resolvedRefs != null) {
      resolvedRefs = myRequester.fixModuleReferences(resolvedRefs);
    }
    cacheReference(namespaceCommand.module(), null, resolvedRefs);
    if (namespaceCommand.isImport() && resolvedRefs != null && !resolvedRefs.isEmpty() && resolvedRefs.getLast() instanceof ModuleReferable moduleRef) {
      myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>()))
        .addImportedModule(moduleRef.path);
    }
  }

  @Override
  public void renamingResolved(ConcreteNamespaceCommand.NameRenaming renaming, Referable originalRef, Referable resolvedRef) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, resolvedRef, null);
    }
  }

  @Override
  public void hidingResolved(ConcreteNamespaceCommand.NameHiding hiding, Referable originalRef, Referable resolvedRef) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, resolvedRef, null);
    }
  }
}
