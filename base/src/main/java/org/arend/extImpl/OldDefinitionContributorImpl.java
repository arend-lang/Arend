package org.arend.extImpl;

import org.arend.ext.DefinitionContributor;
import org.arend.ext.concrete.definition.ConcreteDefinition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.library.error.LibraryError;
import org.arend.module.ModuleLocation;
import org.arend.module.scopeprovider.SimpleModuleScopeProvider;
import org.arend.naming.reference.*;
import org.arend.naming.scope.SimpleScope;
import org.arend.term.concrete.Concrete;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO[server2]: Delete this
public class OldDefinitionContributorImpl extends Disableable implements DefinitionContributor {
  private final String myLibraryName;
  private final ErrorReporter myErrorReporter;
  private final SimpleModuleScopeProvider myModuleScopeProvider;

  public OldDefinitionContributorImpl(String libraryName, ErrorReporter errorReporter, SimpleModuleScopeProvider moduleScopeProvider) {
    myLibraryName = libraryName;
    myErrorReporter = errorReporter;
    myModuleScopeProvider = moduleScopeProvider;
  }

  private void declare(LocatedReferable referable) {
    checkEnabled();

    List<String> longNameList = new ArrayList<>();
    ModulePath module = LocatedReferable.Helper.getLocation(referable, longNameList).getModulePath();
    LongName longName = new LongName(longNameList);

    if (!FileUtils.isCorrectModulePath(module)) {
      myErrorReporter.report(FileUtils.illegalModuleName(module.toString()));
      return;
    }

    if (!FileUtils.isCorrectDefinitionName(longName)) {
      myErrorReporter.report(FileUtils.illegalDefinitionName(longName.toString()));
      return;
    }

    SimpleScope scope = (SimpleScope) myModuleScopeProvider.forModule(module);
    if (scope == null) {
      scope = new SimpleScope();
      myModuleScopeProvider.addModule(module, scope);
    }

    LocatedReferable locationRef = new FullModuleReferable(new ModuleLocation(myLibraryName, ModuleLocation.LocationKind.GENERATED, module));
    Referable prevRef = null;
    List<String> list = longName.toList();
    for (int i = 0; i < list.size(); i++) {
      String name = list.get(i);
      if (i == list.size() - 1) {
        Referable ref = scope.resolveName(name);
        if (ref != null) {
          myErrorReporter.report(LibraryError.duplicateExtensionDefinition(myLibraryName, module, longName));
          return;
        }
        scope.names.put(name, referable);
        String alias = referable.getAliasName();
        if (alias != null) {
          scope.names.putIfAbsent(alias, new AliasReferable(referable));
          SimpleScope namespace = scope.namespaces.get(name);
          if (namespace != null) {
            scope.namespaces.putIfAbsent(alias, namespace);
          }
          namespace = scope.namespaces.get(alias);
          if (namespace != null) {
            scope.namespaces.putIfAbsent(name, namespace);
          }
        }
      } else {
        prevRef = scope.names.putIfAbsent(name, new EmptyLocatedReferable(name, prevRef instanceof LocatedReferable ? (LocatedReferable) prevRef : locationRef));
        SimpleScope newScope = scope.namespaces.computeIfAbsent(name, k -> new SimpleScope());
        if (prevRef instanceof AliasReferable) {
          scope.namespaces.putIfAbsent(((AliasReferable) prevRef).getOriginalReferable().getRefName(), newScope);
        } else if (prevRef instanceof GlobalReferable) {
          String aliasName = ((GlobalReferable) prevRef).getAliasName();
          if (aliasName != null) {
            scope.namespaces.putIfAbsent(aliasName, newScope);
          }
        }
        scope = newScope;
      }
    }
  }

  @Override
  public void declare(@NotNull Doc description, @NotNull MetaRef metaRef, @Nullable MetaDefinition meta, @Nullable MetaResolver resolver) {
    if (!(metaRef instanceof MetaReferable ref)) {
      throw new IllegalArgumentException();
    }
    ref.setDefinition(meta, resolver);
    declare(ref);
  }

  @Override
  public void declare(@NotNull Doc description, @NotNull ConcreteDefinition definition) {
    if (!(definition instanceof Concrete.Definition def)) {
      throw new IllegalArgumentException();
    }
    declare(def.getData());
  }
}
