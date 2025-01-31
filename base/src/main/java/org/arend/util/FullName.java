package org.arend.util;

import org.arend.ext.module.LongName;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.LocatedReferable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FullName {
  public ModuleLocation module;
  public LongName longName;

  public FullName(ModuleLocation modulePath, LongName longName) {
    this.module = modulePath;
    this.longName = longName;
  }

  public FullName(LocatedReferable referable) {
    List<String> name = new ArrayList<>();
    module = LocatedReferable.Helper.getLocation(referable, name);
    longName = new LongName(name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FullName fullName = (FullName) o;
    return Objects.equals(module, fullName.module) &&
      Objects.equals(longName, fullName.longName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(module, longName);
  }

  @Override
  public String toString() {
    return module + "::" + longName;
  }
}
