package org.arend.server.impl;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.LocatedReferable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ErrorService implements ErrorReporter {
  private final Map<ModuleLocation, List<GeneralError>> myResolverErrors = new ConcurrentHashMap<>();
  private final Map<LocatedReferable, List<GeneralError>> myTypecheckingErrors = new ConcurrentHashMap<>();
  private final List<ErrorReporter> myErrorReporters = new ArrayList<>();

  public void addErrorReporter(ErrorReporter errorReporter) {
    myErrorReporters.add(errorReporter);
  }

  public void setResolverErrors(ModuleLocation module, List<GeneralError> errors) {
    if (errors.isEmpty()) {
      myResolverErrors.remove(module);
    } else {
      myResolverErrors.put(module, errors);
      for (ErrorReporter errorReporter : myErrorReporters) {
        for (GeneralError error : errors) {
          errorReporter.report(error);
        }
      }
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
    Map<ModuleLocation, List<GeneralError>> result = new HashMap<>();
    for (Map.Entry<ModuleLocation, List<GeneralError>> entry : myResolverErrors.entrySet()) {
      result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    for (Map.Entry<LocatedReferable, List<GeneralError>> entry : myTypecheckingErrors.entrySet()) {
      ModuleLocation module = entry.getKey().getLocation();
      if (module != null) {
        result.computeIfAbsent(module, k -> new ArrayList<>()).addAll(entry.getValue());
      }
    }
    return result;
  }

  public List<GeneralError> getTypecheckingErrors(LocatedReferable referable) {
    List<GeneralError> errors = myTypecheckingErrors.get(referable);
    return errors == null ? Collections.emptyList() : errors;
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

  /**
   * The name-resolution errors currently recorded for {@code module}, empty if there are none.
   *
   * <p>{@link #setResolverErrors} also pushes them to the registered reporters, but only when
   * the module is actually (re-)resolved. A process that outlives one pass over a library --
   * an IDE server, a daemon -- has to read them back from here to report them again.
   */
  public List<GeneralError> getResolverErrors(ModuleLocation module) {
    List<GeneralError> errors = myResolverErrors.get(module);
    return errors == null ? Collections.emptyList() : errors;
  }

  /** Forgets everything recorded for a module that is no longer part of the server. */
  public void removeModule(ModuleLocation module) {
    myResolverErrors.remove(module);
    clearTypecheckingErrors(module);
  }

  public void clearTypecheckingErrors(ModuleLocation module) {
    myTypecheckingErrors.keySet().removeIf(ref -> module.equals(ref.getLocation()));
  }

  @Override
  public void report(GeneralError error) {
    error.forAffectedDefinitions((ref, newError) -> {
      if (ref instanceof LocatedReferable located) {
        myTypecheckingErrors.computeIfAbsent(located, k -> new ArrayList<>()).add(newError);
      }
    });
    for (ErrorReporter errorReporter : myErrorReporters) {
      errorReporter.report(error);
    }
  }
}