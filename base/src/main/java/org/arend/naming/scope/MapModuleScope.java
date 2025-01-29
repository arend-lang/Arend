package org.arend.naming.scope;

import org.arend.ext.module.ModulePath;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.ModuleReferable;
import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MapModuleScope implements Scope {
  private final List<String> myPrefix;
  private final ModuleLocation myModuleLocation;
  private final Map<String, MapModuleScope> myMap;

  private MapModuleScope(List<String> prefix, ModuleLocation moduleLocation) {
    myPrefix = prefix;
    myModuleLocation = moduleLocation;
    myMap = new HashMap<>();
  }

  public MapModuleScope(Collection<? extends ModuleLocation> modules) {
    myPrefix = Collections.emptyList();
    myModuleLocation = null;
    myMap = new HashMap<>();
    for (ModuleLocation module : modules) {
      Map<String, MapModuleScope> map = myMap;
      List<String> prefix = Collections.emptyList();
      List<String> path = module.getModulePath().toList();
      for (String name : path) {
        if (prefix.size() == path.size() - 1) {
          map.computeIfAbsent(name, k -> new MapModuleScope(module.getModulePath().toList(), module));
        } else {
          List<String> newPrefix = new ArrayList<>(prefix.size() + 1);
          newPrefix.addAll(prefix);
          newPrefix.add(name);
          map = map.computeIfAbsent(name, k -> new MapModuleScope(newPrefix, null)).myMap;
          prefix = newPrefix;
        }
      }
    }
  }

  @Override
  public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    if (context != ScopeContext.STATIC) return Collections.emptyList();
    List<ModuleReferable> result = new ArrayList<>(myMap.size());
    for (MapModuleScope scope : myMap.values()) {
      if (scope.myModuleLocation != null) {
        result.add(new FullModuleReferable(scope.myModuleLocation));
      } else {
        result.add(new ModuleReferable(new ModulePath(scope.myPrefix)));
      }
    }
    return result;
  }

  @Override
  public @Nullable MapModuleScope resolveNamespace(@NotNull String name) {
    return myMap.get(name);
  }
}
