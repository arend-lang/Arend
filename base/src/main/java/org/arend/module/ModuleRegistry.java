package org.arend.module;

import org.arend.ext.module.ModulePath;
import org.arend.term.group.ConcreteGroup;

public interface ModuleRegistry {
  void registerModule(ModulePath modulePath, ConcreteGroup group);
  void unregisterModule(ModulePath path);
  boolean isRegistered(ModulePath modulePath);
}
