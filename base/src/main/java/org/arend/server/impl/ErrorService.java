package org.arend.server.impl;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.LocatedReferable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ErrorService implements ErrorReporter {
  private final Map<ModuleLocation, List<GeneralError>> myResolverErrors = new ConcurrentHashMap<>();
  private final Map<LocatedReferable, List<GeneralError>> myTypecheckingErrors = new ConcurrentHashMap<>();

  public void setResolverErrors(ModuleLocation module, List<GeneralError> errors) {
    if (errors.isEmpty()) {
      myResolverErrors.remove(module);
    } else {
      myResolverErrors.put(module, errors);
    }
  }

  public void clear() {
    myResolverErrors.clear();
    myTypecheckingErrors.clear();
  }

  public boolean hasErrors() {
    return !(myResolverErrors.isEmpty() && myTypecheckingErrors.isEmpty());
  }

  public Map<ModuleLocation, List<GeneralError>> getAllErrors() {
    Map<ModuleLocation, List<GeneralError>> result = new HashMap<>(myResolverErrors);
    for (Map.Entry<LocatedReferable, List<GeneralError>> entry : myTypecheckingErrors.entrySet()) {
      ModuleLocation module = entry.getKey().getLocation();
      if (module != null) {
        result.computeIfAbsent(module, k -> new ArrayList<>()).addAll(entry.getValue());
      }
    }
    return result;
  }

  public List<GeneralError> getTypecheckingErrors(ModuleLocation module) {
    List<GeneralError> result = new ArrayList<>();
    for (Map.Entry<LocatedReferable, List<GeneralError>> entry : myTypecheckingErrors.entrySet()) {
      ModuleLocation errorModule = entry.getKey().getLocation();
      if (module.equals(errorModule)) {
        result.addAll(entry.getValue());
      }
    }
    return result;
  }

  @Override
  public void report(GeneralError error) {
    error.forAffectedDefinitions((ref, newError) -> {
      if (ref instanceof LocatedReferable located) {
        myTypecheckingErrors.computeIfAbsent(located, k -> new ArrayList<>()).add(newError);
      }
    });
  }
}
