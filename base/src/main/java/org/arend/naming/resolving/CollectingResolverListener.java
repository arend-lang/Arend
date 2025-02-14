package org.arend.naming.resolving;

import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.DataContainer;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.*;
import org.arend.term.NameRenaming;
import org.arend.term.NamespaceCommand;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.concrete.Concrete;

import java.util.*;

public class CollectingResolverListener implements ResolverListener {
  public record ResolvedReference(AbstractReference reference, Referable referable) {}

  public record ReferablePair(AbstractReferable referable, TCDefReferable tcReferable) {}

  public record ModuleCacheStructure(List<ResolvedReference> cache, List<ReferablePair> referables, List<ModulePath> importedModules) {
    void addReference(AbstractReference reference, Referable referable) {
      cache.add(new ResolvedReference(reference, referable));
    }

    void addReferable(AbstractReferable referable, TCDefReferable tcReferable) {
      referables.add(new ReferablePair(referable, tcReferable));
    }

    void addImportedModule(ModulePath module) {
      importedModules.add(module);
    }
  }

  public ModuleLocation moduleLocation;
  private final Map<ModuleLocation, ModuleCacheStructure> myModuleCache = new HashMap<>();
  private final boolean myCacheReferences;

  public CollectingResolverListener(boolean cacheReferences) {
    myCacheReferences = cacheReferences;
  }

  public ModuleCacheStructure getCacheStructure(ModuleLocation module) {
    return myModuleCache.get(module);
  }

  private void cacheReference(Object data, Referable referable) {
    if (data instanceof AbstractReference) {
      myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()))
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
            myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()))
              .addReference(referenceList.get(i), abstractRef instanceof ErrorReference ? TCDefReferable.NULL_REFERABLE : abstractRef);
          }
        }
      }
      for (int i = resolvedRefs.size(); i < referenceList.size(); i++) {
        myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()))
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
    cacheReference(originalRef instanceof UnresolvedReference ? (UnresolvedReference) originalRef : new NamedUnresolvedReference(originalRef, originalRef.getRefName()), newRef, resolvedRefs);
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
  public void namespaceResolved(NamespaceCommand namespaceCommand, List<Referable> resolvedRefs) {
    cacheReference(new LongUnresolvedReference(namespaceCommand, namespaceCommand.getReferenceList(), namespaceCommand.getPath()), null, resolvedRefs);
    if (namespaceCommand.getKind() == NamespaceCommand.Kind.IMPORT && resolvedRefs != null && !resolvedRefs.isEmpty() && resolvedRefs.get(resolvedRefs.size() - 1) instanceof ModuleReferable moduleRef) {
      myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()))
        .addImportedModule(moduleRef.path);
    }
  }

  @Override
  public void renamingResolved(NameRenaming renaming, Referable originalRef, Referable resolvedRef) {
    if (originalRef instanceof UnresolvedReference) {
      cacheReference((UnresolvedReference) originalRef, resolvedRef, null);
    }
  }

  @Override
  public void definitionResolved(Concrete.ResolvableDefinition definition) {
    if (definition.getData().getData() instanceof AbstractReferable referable) {
      myModuleCache.computeIfAbsent(moduleLocation, k -> new ModuleCacheStructure(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()))
        .addReferable(referable, definition.getData());
    }
  }
}
