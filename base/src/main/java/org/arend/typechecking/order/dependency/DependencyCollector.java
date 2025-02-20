package org.arend.typechecking.order.dependency;

import org.arend.core.definition.*;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.reference.TCReferable;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyCollector implements DependencyListener {
  private final ArendServer myServer;
  private final Map<TCReferable, Set<TCReferable>> myDependencies = new ConcurrentHashMap<>();
  private final Map<TCReferable, Set<TCReferable>> myReverseDependencies = new ConcurrentHashMap<>();

  public DependencyCollector(ArendServer server) {
    myServer = server;
  }

  @Override
  public void dependsOn(TCReferable def1, TCReferable def2) {
    if (def1.isLocalFunction() || def2.isLocalFunction()) {
      return;
    }
    ModuleLocation location = def2.getLocation();
    ArendLibrary library = location == null || myServer == null ? null : myServer.getLibrary(location.getLibraryName());
    if (library == null || !library.isExternalLibrary()) {
      myReverseDependencies.computeIfAbsent(def2, k -> ConcurrentHashMap.newKeySet()).add(def1);
    }
  }

  @Override
  public Set<? extends TCReferable> update(TCReferable definition) {
    Set<TCReferable> updated = new HashSet<>();
    Stack<TCReferable> stack = new Stack<>();
    stack.push(definition);

    while (!stack.isEmpty()) {
      TCReferable toUpdate = stack.pop();
      if (!updated.add(toUpdate)) {
        continue;
      }

      Set<TCReferable> dependencies = myDependencies.remove(toUpdate);
      if (dependencies != null) {
        for (TCReferable dependency : dependencies) {
          Set<TCReferable> definitions = myReverseDependencies.get(dependency);
          if (definitions != null) {
            definitions.remove(definition);
          }
        }
      }

      Set<TCReferable> reverseDependencies = myReverseDependencies.remove(toUpdate);
      if (reverseDependencies != null) {
        stack.addAll(reverseDependencies);
      }
    }

    for (TCReferable updatedDef : updated) {
      if (!(updatedDef instanceof TCDefReferable)) {
        continue;
      }
      Definition def = ((TCDefReferable) updatedDef).getTypechecked();
      ((TCDefReferable) updatedDef).setTypechecked(null);
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
  public Set<? extends TCReferable> getDependencies(TCReferable definition) {
    Set<TCReferable> dependencies = myDependencies.get(definition);
    return dependencies == null ? Collections.emptySet() : dependencies;
  }

  public void clear() {
    myDependencies.clear();
    myReverseDependencies.clear();
  }

  public void copyTo(DependencyCollector collector) {
    for (Map.Entry<TCReferable, Set<TCReferable>> entry : myDependencies.entrySet()) {
      collector.myDependencies.computeIfAbsent(entry.getKey(), k -> ConcurrentHashMap.newKeySet()).addAll(entry.getValue());
    }
    for (Map.Entry<TCReferable, Set<TCReferable>> entry : myReverseDependencies.entrySet()) {
      collector.myReverseDependencies.computeIfAbsent(entry.getKey(), k -> ConcurrentHashMap.newKeySet()).addAll(entry.getValue());
    }
  }
}
