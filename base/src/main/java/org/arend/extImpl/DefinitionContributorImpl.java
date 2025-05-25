package org.arend.extImpl;

import org.arend.ext.DefinitionContributor;
import org.arend.ext.concrete.definition.ConcreteDefinition;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.*;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.DataModuleReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.LongUnresolvedReference;
import org.arend.naming.reference.NamedUnresolvedReference;
import org.arend.naming.scope.Scope;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DefinitionContributorImpl extends Disableable implements DefinitionContributor {
  private static class Tree {
    Doc description;
    final LocatedReferable referable;
    Concrete.ResolvableDefinition definition;
    final Map<String, Tree> children = new LinkedHashMap<>();

    private Tree(Doc description, LocatedReferable referable) {
      this.description = description;
      this.referable = referable;
    }

    ConcreteGroup makeGroup(List<ConcreteStatement> statements) {
      for (Tree tree : children.values()) {
        statements.add(new ConcreteStatement(tree.makeGroup(new ArrayList<>()), null, null, null));
      }
      return new ConcreteGroup(description, referable, definition, statements, Collections.emptyList(), Collections.emptyList());
    }
  }

  private final String myLibraryName;
  private final Map<ModuleLocation, Tree> myModules = new LinkedHashMap<>();
  private final Map<ModuleLocation, List<ConcreteStatement>> myImports = new HashMap<>();

  public DefinitionContributorImpl(String libraryName) {
    myLibraryName = libraryName;
  }

  public Map<ModuleLocation, ConcreteGroup> getModules() {
    Map<ModuleLocation, ConcreteGroup> result = new LinkedHashMap<>();
    for (Map.Entry<ModuleLocation, Tree> entry : myModules.entrySet()) {
      List<ConcreteStatement> imports = myImports.get(entry.getKey());
      result.put(entry.getKey(), entry.getValue().makeGroup(imports == null ? new ArrayList<>() : imports));
    }
    return result;
  }

  private void declareDefinition(Doc description, Concrete.ResolvableDefinition definition) {
    description.accept(new BaseDocVisitor<Void>() {
      private void checkText(String text) {
        if (text.indexOf('\n') >= 0) {
          throw new IllegalArgumentException("LineDoc consisting of several lines");
        }
      }

      @Override
      public Void visitText(TextDoc doc, Void params) {
        checkText(doc.getText());
        return null;
      }

      @Override
      public Void visitTermLine(TermLineDoc doc, Void params) {
        checkText(doc.getText());
        return null;
      }

      @Override
      public Void visitPattern(PatternDoc doc, Void params) {
        checkText(doc.getText());
        return null;
      }
    }, null);

    List<LocatedReferable> ancestors = new ArrayList<>();
    ModuleLocation module = LocatedReferable.Helper.getAncestors(definition.getData(), ancestors);

    Tree tree = myModules.computeIfAbsent(module, k -> {
      if (!(myLibraryName.equals(module.getLibraryName()) && module.getLocationKind() == ModuleLocation.LocationKind.GENERATED && FileUtils.isCorrectModulePath(module.getModulePath()) && FileUtils.isCorrectDefinitionName(new LongName(ancestors.stream().map(LocatedReferable::getRefName).toList())))) {
        throw new IllegalArgumentException();
      }
      return new Tree(DocFactory.nullDoc(), new DataModuleReferable(null, module));
    });

    for (LocatedReferable ref : ancestors) {
      tree = tree.children.compute(ref.getRefName(), (refName, prevTree) -> {
        if (prevTree == null) return new Tree(DocFactory.nullDoc(), ref);
        if (!prevTree.referable.equals(ref)) {
          throw new IllegalArgumentException("Duplicate name: " + refName);
        }
        return prevTree;
      });
    }

    if (tree.definition != null) {
      throw new IllegalArgumentException("Duplicate definition: " + definition.getData());
    }

    tree.description = description;
    tree.definition = definition;
  }

  @Override
  public void declare(@NotNull Doc description, @NotNull ConcreteDefinition definition) {
    if (!(definition instanceof Concrete.ResolvableDefinition def)) {
      throw new IllegalArgumentException();
    }
    declareDefinition(description, def);
  }

  @Override
  public void declare(@NotNull ModulePath module, @NotNull ModulePath importedModule, @NotNull String... names) {
    myImports.computeIfAbsent(new ModuleLocation(myLibraryName, ModuleLocation.LocationKind.GENERATED, module), k -> new ArrayList<>())
        .add(new ConcreteStatement(null, new ConcreteNamespaceCommand(null, true, new LongUnresolvedReference(null, null, importedModule.toList()), names.length == 0, Arrays.stream(names).map(name -> new ConcreteNamespaceCommand.NameRenaming(null, Scope.ScopeContext.STATIC, new NamedUnresolvedReference(null, name), null, null)).toList(), Collections.emptyList()), null, null));
  }
}
