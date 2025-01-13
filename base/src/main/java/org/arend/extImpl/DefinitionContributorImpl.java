package org.arend.extImpl;

import org.arend.ext.DefinitionContributor;
import org.arend.ext.concrete.definition.ConcreteDefinition;
import org.arend.ext.module.LongName;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.MetaReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DefinitionContributorImpl extends Disableable implements DefinitionContributor {
  private static class Tree {
    final LocatedReferable referable;
    Concrete.ResolvableDefinition definition;
    final Map<String, Tree> children = new LinkedHashMap<>();

    private Tree(LocatedReferable referable) {
      this.referable = referable;
    }

    ConcreteGroup makeGroup() {
      List<ConcreteStatement> statements = new ArrayList<>();
      for (Tree tree : children.values()) {
        statements.add(new ConcreteStatement(tree.makeGroup(), null, null, null));
      }
      return new ConcreteGroup(referable, definition, statements, Collections.emptyList(), Collections.emptyList());
    }
  }

  private final String myLibraryName;
  private final Map<ModuleLocation, Tree> myModules = new LinkedHashMap<>();

  public DefinitionContributorImpl(String libraryName) {
    myLibraryName = libraryName;
  }

  public Map<ModuleLocation, ConcreteGroup> getModules() {
    Map<ModuleLocation, ConcreteGroup> result = new LinkedHashMap<>();
    for (Map.Entry<ModuleLocation, Tree> entry : myModules.entrySet()) {
      result.put(entry.getKey(), entry.getValue().makeGroup());
    }
    return result;
  }

  private void declareDefinition(Concrete.ResolvableDefinition definition) {
    List<LocatedReferable> ancestors = new ArrayList<>();
    ModuleLocation module = LocatedReferable.Helper.getAncestors(definition.getData(), ancestors);

    Tree tree = myModules.computeIfAbsent(module, k -> {
      if (!(myLibraryName.equals(module.getLibraryName()) && module.getLocationKind() == ModuleLocation.LocationKind.GENERATED && FileUtils.isCorrectModulePath(module.getModulePath()) && FileUtils.isCorrectDefinitionName(new LongName(ancestors.stream().map(LocatedReferable::getRefName).toList())))) {
        throw new IllegalArgumentException();
      }
      return new Tree(new FullModuleReferable(module));
    });

    for (LocatedReferable ref : ancestors) {
      tree = tree.children.compute(ref.getRefName(), (refName, prevTree) -> {
        if (prevTree == null) return new Tree(ref);
        if (!prevTree.referable.equals(ref)) {
          throw new IllegalArgumentException("Duplicate name: " + refName);
        }
        return prevTree;
      });
    }

    if (tree.definition != null) {
      throw new IllegalArgumentException("Duplicate definition: " + definition.getData());
    }

    tree.definition = definition;
  }

  @Override
  public void declare(@NotNull ConcreteDefinition definition) {
    if (!(definition instanceof Concrete.Definition def)) {
      throw new IllegalArgumentException();
    }
    declareDefinition(def);
  }

  @Override
  public void declare(@NotNull MetaRef metaRef, @Nullable MetaDefinition meta, @Nullable MetaResolver resolver) {
    if (!(metaRef instanceof MetaReferable ref)) {
      throw new IllegalArgumentException();
    }
    ref.setDefinition(meta, resolver);
    declareDefinition(new DefinableMetaDefinition(ref, null, null, Collections.emptyList(), null));
  }
}
