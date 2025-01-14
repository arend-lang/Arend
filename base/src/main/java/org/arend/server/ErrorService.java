package org.arend.server;

import org.arend.ext.error.GeneralError;
import org.arend.module.ModuleLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ErrorService {
  private final Map<ModuleLocation, List<GeneralError>> myErrorMap = new ConcurrentHashMap<>();

  public void setErrors(ModuleLocation module, List<GeneralError> errors) {
    if (errors.isEmpty()) {
      myErrorMap.remove(module);
    } else {
      myErrorMap.put(module, errors);
    }
  }

  public List<GeneralError> getErrorList(ModuleLocation module) {
    List<GeneralError> errors = myErrorMap.get(module);
    return errors == null ? Collections.emptyList() : errors;
  }

  public Collection<ModuleLocation> getModulesWithErrors() {
    return myErrorMap.keySet();
  }

  public void clear() {
    myErrorMap.clear();
  }
}
