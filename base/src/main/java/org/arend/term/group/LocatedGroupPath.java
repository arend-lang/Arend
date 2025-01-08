package org.arend.term.group;

import org.arend.module.ModuleLocation;

import java.util.List;

public class LocatedGroupPath extends GroupPath {
  private final ModuleLocation myModule;

  public LocatedGroupPath(ModuleLocation module, List<Element> path) {
    super(path);
    myModule = module;
  }

  public ModuleLocation getModule() {
    return myModule;
  }
}
