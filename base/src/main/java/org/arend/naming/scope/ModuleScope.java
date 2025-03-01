package org.arend.naming.scope;

import org.arend.ext.module.ModulePath;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.Referable;
import org.arend.server.ArendLibrary;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.ArendServerRequester;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ModuleScope implements Scope {
  private final ArendServerRequester myRequester;
  private final List<String> myLibraries;
  private final boolean myWithTests;
  private final MapModuleScope myAdditionalScope;
  private final List<String> myPrefix;

  private ModuleScope(ArendServerRequester requester, List<String> libraries, boolean withTests, MapModuleScope additionalScope, List<String> prefix) {
    myRequester = requester;
    myLibraries = libraries;
    myWithTests = withTests;
    myAdditionalScope = additionalScope;
    myPrefix = prefix;
  }

  public ModuleScope(ArendServerImpl server, String libraryName, boolean withTests) {
    myRequester = server.getRequester();
    myWithTests = withTests;
    myPrefix = Collections.emptyList();

    myLibraries = new ArrayList<>();
    if (libraryName == null) {
      myLibraries.addAll(server.getLibraries());
    } else {
      ArendLibrary library = server.getLibrary(libraryName);
      myLibraries.add(libraryName);
      if (library != null) {
        myLibraries.addAll(library.getLibraryDependencies());
      }
    }

    List<ModuleLocation> modules = new ArrayList<>();
    if (!myLibraries.isEmpty()) {
      for (ModuleLocation module : server.getModules()) {
        if (module.getLocationKind() == ModuleLocation.LocationKind.GENERATED && myLibraries.contains(module.getLibraryName())) {
          modules.add(module);
        }
      }
    }
    myAdditionalScope = new MapModuleScope(modules);
  }

  private void addNames(String library, ModuleLocation.LocationKind kind, List<String> names, List<Referable> result) {
    if (names != null) {
      for (String name : names) {
        List<String> path = new ArrayList<>(myPrefix.size() + 1);
        path.addAll(myPrefix);
        path.add(name);
        result.add(new FullModuleReferable(new ModuleLocation(library, kind, new ModulePath(path))));
      }
    }
  }

  @Override
  public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    if (context != ScopeContext.STATIC) return Collections.emptyList();
    List<Referable> result = new ArrayList<>();
    if (myAdditionalScope != null) {
      result.addAll(myAdditionalScope.getElements());
    }

    if (myRequester != null) {
      for (String library : myLibraries) {
        addNames(library, ModuleLocation.LocationKind.SOURCE, myRequester.getFiles(library, false, myPrefix), result);
        if (myWithTests) {
          addNames(library, ModuleLocation.LocationKind.TEST, myRequester.getFiles(library, true, myPrefix), result);
        }
      }
    }

    return result;
  }

  @Override
  public @Nullable Scope resolveNamespace(@NotNull String name) {
    List<String> prefix = new ArrayList<>(myPrefix.size() + 1);
    prefix.addAll(myPrefix);
    prefix.add(name);
    return new ModuleScope(myRequester, myLibraries, myWithTests, myAdditionalScope == null ? null : myAdditionalScope.resolveNamespace(name), prefix);
  }
}
