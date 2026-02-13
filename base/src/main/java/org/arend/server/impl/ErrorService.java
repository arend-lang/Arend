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

  public void addErrorReporterIfNotExists(ErrorReporter errorReporter) {
    if (!myErrorReporters.contains(errorReporter)) {
      myErrorReporters.add(errorReporter);
    }
  }

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

  @Override
  public void report(GeneralError error) {
    System.out.println("[DEBUG_LOG] ErrorService.report called with error: " + error);
    System.out.println("[DEBUG_LOG] Error level: " + error.level + ", Error class: " + error.getClass().getName());
    error.forAffectedDefinitions((ref, newError) -> {
      System.out.println("[DEBUG_LOG] Processing affected definition: " + ref);
      if (ref instanceof LocatedReferable located) {
        System.out.println("[DEBUG_LOG] Adding typechecking error for located referable: " + located);
        myTypecheckingErrors.computeIfAbsent(located, k -> new ArrayList<>()).add(newError);
      }
    });
    System.out.println("[DEBUG_LOG] Forwarding error to " + myErrorReporters.size() + " error reporters");
    for (ErrorReporter errorReporter : myErrorReporters) {
      System.out.println("[DEBUG_LOG] Forwarding to: " + errorReporter.getClass().getName());
      errorReporter.report(error);
    }
  }
}
