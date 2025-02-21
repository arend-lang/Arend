package org.arend.typechecking.order.dependency;

import org.arend.core.definition.*;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyCollector implements DependencyListener {
  private final ArendServer myServer;
  private final Map<TCDefReferable, Set<TCDefReferable>> myDependencies = new ConcurrentHashMap<>();
  private final Map<TCDefReferable, Set<TCDefReferable>> myReverseDependencies = new ConcurrentHashMap<>();

  public DependencyCollector(ArendServer server) {
    myServer = server;
  }

  @Override
  public void dependsOn(TCDefReferable def1, TCDefReferable def2) {
    ModuleLocation location = def2.getLocation();
    ArendLibrary library = location == null || myServer == null ? null : myServer.getLibrary(location.getLibraryName());
    if (library == null || !library.isExternalLibrary()) {
      myReverseDependencies.computeIfAbsent(def2, k -> ConcurrentHashMap.newKeySet()).add(def1);
    }
  }

  @Override
  public Set<? extends TCDefReferable> update(TCDefReferable definition) {
    Set<TCDefReferable> updated = new HashSet<>();
    Stack<TCDefReferable> stack = new Stack<>();
    stack.push(definition);

    while (!stack.isEmpty()) {
      TCDefReferable toUpdate = stack.pop();
      if (!updated.add(toUpdate)) {
        continue;
      }

      Set<TCDefReferable> dependencies = myDependencies.remove(toUpdate);
      if (dependencies != null) {
        for (TCDefReferable dependency : dependencies) {
          Set<TCDefReferable> definitions = myReverseDependencies.get(dependency);
          if (definitions != null) {
            definitions.remove(definition);
          }
        }
      }

      Set<TCDefReferable> reverseDependencies = myReverseDependencies.remove(toUpdate);
      if (reverseDependencies != null) {
        stack.addAll(reverseDependencies);
      }
    }

    for (TCDefReferable updatedDef : updated) {
      Definition def = updatedDef.getTypechecked();
      updatedDef.setTypechecked(null);
      if (def instanceof ClassDefinition) {
        for (ClassField field : ((ClassDefinition) def).getPersonalFields()) {
          field.getReferable().setTypechecked(null);
        }
      } else if (def instanceof DataDefinition) {
        for (Constructor constructor : ((DataDefinition) def).getConstructors()) {
          constructor.getReferable().setTypechecked(null);
        }
      }
    }

    return updated;
  }

  @Override
  public Set<? extends TCDefReferable> getDependencies(TCDefReferable definition) {
    Set<TCDefReferable> dependencies = myDependencies.get(definition);
    return dependencies == null ? Collections.emptySet() : dependencies;
  }

  public void clear() {
    myDependencies.clear();
    myReverseDependencies.clear();
  }

  public void copyTo(DependencyCollector collector) {
    for (Map.Entry<TCDefReferable, Set<TCDefReferable>> entry : myDependencies.entrySet()) {
      collector.myDependencies.computeIfAbsent(entry.getKey(), k -> ConcurrentHashMap.newKeySet()).addAll(entry.getValue());
    }
    for (Map.Entry<TCDefReferable, Set<TCDefReferable>> entry : myReverseDependencies.entrySet()) {
      collector.myReverseDependencies.computeIfAbsent(entry.getKey(), k -> ConcurrentHashMap.newKeySet()).addAll(entry.getValue());
    }
  }
}
