package org.arend.server.impl;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.LocatedReferable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO[server2]: Currently, after a large PSI change, 'cause' reference in a typechecking error might become invalid.
//                There are a few ways to fix this:
//                1. Always re-typecheck definitions with errors after every update.
//                2. After an update, check that all 'cause' references are OK, and re-typecheck definitions with broken errors.
//                3. Try to restore broken reference from the concrete source node.
//                   It is easier to do this after introducing raw expressions so that we can easily map them to PSI elements.
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

  public void resetDefinition(LocatedReferable referable) {
    myTypecheckingErrors.remove(referable);
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
