package org.arend.naming.reference;

import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.module.FullName;
import org.arend.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.naming.scope.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface LocatedReferable extends GlobalReferable {
  @Nullable ModuleLocation getLocation();
  @Nullable LocatedReferable getLocatedReferableParent();

  @NotNull
  @Override
  default LongName getRefLongName() {
    List<String> longName = new ArrayList<>();
    LocatedReferable.Helper.getLocation(this, longName);
    return new LongName(longName);
  }

  @Override
  default boolean checkName(@NotNull String libraryName, @NotNull ModulePath modulePath, @NotNull LongName name) {
    List<String> longName = new ArrayList<>();
    ModuleLocation module = LocatedReferable.Helper.getLocation(this, longName);
    return module != null && name.toList().equals(longName) && modulePath.equals(module.getModulePath()) && libraryName.equals(module.getLibraryName());
  }

  default FullName getRefFullName() {
    List<String> name = new ArrayList<>();
    return new FullName(LocatedReferable.Helper.getLocation(this, name), new LongName(name));
  }

  @Override
  default @Nullable ModulePath getModulePath() {
    ModuleLocation module = getLocation();
    return module == null ? null : module.getModulePath();
  }

  class Helper {
    public static ModuleLocation getLocation(LocatedReferable referable, List<String> fullName) {
      LocatedReferable parent = referable.getLocatedReferableParent();
      if (parent == null) {
        return referable.getLocation();
      }

      ModuleLocation location = getLocation(parent, fullName);
      fullName.add(referable.textRepresentation());
      return location;
    }

    public static ModuleLocation getAncestors(LocatedReferable referable, List<LocatedReferable> ancestors) {
      LocatedReferable parent = referable.getLocatedReferableParent();
      if (parent == null) {
        return referable.getLocation();
      }

      ModuleLocation location = getAncestors(parent, ancestors);
      ancestors.add(referable);
      return location;
    }

    public static Scope resolveNamespace(LocatedReferable locatedReferable, ModuleScopeProvider moduleScopeProvider) {
      LocatedReferable parent = locatedReferable.getLocatedReferableParent();
      if (parent == null) {
        ModuleLocation moduleLocation = locatedReferable.getLocation();
        if (moduleLocation == null) {
          return null;
        }
        return moduleScopeProvider.forModule(moduleLocation.getModulePath());
      } else {
        Scope scope = resolveNamespace(parent, moduleScopeProvider);
        return scope == null ? null : scope.resolveNamespace(locatedReferable.textRepresentation());
      }
    }
  }
}
