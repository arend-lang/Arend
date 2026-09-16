package org.arend.server.impl;

import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ScopeUsage;
import org.arend.server.ScopeUsages;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

public class ScopeUsagesService implements ScopeUsages {
  private final Map<ModuleLocation, Map<LongName, ScopeUsage>> myUsages = new HashMap<>();
  private final Predicate<ModuleLocation> myResolved;

  public ScopeUsagesService(@NotNull Predicate<ModuleLocation> resolved) {
    myResolved = resolved;
  }

  @Override
  public synchronized @NotNull ScopeUsage getUsage(@NotNull ModuleLocation module, @NotNull LongName longName) {
    Map<LongName, ScopeUsage> usages = myUsages.get(module);
    ScopeUsage usage = usages == null ? null : usages.get(longName);
    return usage == null ? ScopeUsage.EMPTY : usage;
  }

  @Override
  public synchronized @NotNull Set<LongName> getGroupNames(@NotNull ModuleLocation module) {
    Map<LongName, ScopeUsage> usages = myUsages.get(module);
    return usages == null ? Collections.emptySet() : Collections.unmodifiableSet(usages.keySet());
  }

  @Override
  public synchronized boolean isCollected(@NotNull ModuleLocation module) {
    return myUsages.containsKey(module) && myResolved.test(module);
  }

  public synchronized void updateNames(@NotNull ModuleLocation module, @NotNull Map<LongName, ScopeUsage> collected) {
    Map<LongName, ScopeUsage> previous = myUsages.get(module);
    Map<LongName, ScopeUsage> result = new HashMap<>(collected.size());
    for (Map.Entry<LongName, ScopeUsage> entry : collected.entrySet()) {
      ScopeUsage previousUsage = previous == null ? null : previous.get(entry.getKey());
      result.put(entry.getKey(), new ScopeUsage(Set.copyOf(entry.getValue().names()), Set.copyOf(entry.getValue().unknownNames()),
        Set.copyOf(entry.getValue().guessedNames()),
        previousUsage == null ? null : previousUsage.instances(), previousUsage == null ? null : previousUsage.inferenceFields()));
    }
    myUsages.put(module, result);
  }

  public synchronized void updateInstances(@NotNull Map<TCDefReferable, ? extends Set<TCDefReferable>> instancesByDefinition, @NotNull Map<TCDefReferable, ? extends Set<TCDefReferable>> inferenceFieldsByDefinition) {
    for (Map.Entry<TCDefReferable, ? extends Set<TCDefReferable>> entry : instancesByDefinition.entrySet()) {
      FullName fullName = entry.getKey().getRefFullName();
      if (fullName.module == null) continue;
      Map<LongName, ScopeUsage> usages = myUsages.get(fullName.module);
      if (usages == null) continue;
      Set<TCDefReferable> inferenceFields = inferenceFieldsByDefinition.get(entry.getKey());
      usages.computeIfPresent(fullName.longName, (longName, usage) ->
        new ScopeUsage(usage.names(), usage.unknownNames(), usage.guessedNames(), Set.copyOf(entry.getValue()),
          inferenceFields == null ? Collections.emptySet() : Set.copyOf(inferenceFields)));
    }
  }

  public synchronized void removeModule(@NotNull ModuleLocation module) {
    myUsages.remove(module);
  }

  public synchronized void removeModules(@NotNull Predicate<ModuleLocation> predicate) {
    myUsages.keySet().removeIf(predicate);
  }

  public synchronized void clear() {
    myUsages.clear();
  }
}
