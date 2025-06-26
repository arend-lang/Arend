package org.arend.ext.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FullName {
  public final @Nullable ModuleLocation module;
  public final @NotNull LongName longName;

  public FullName(@Nullable ModuleLocation modulePath, @NotNull LongName longName) {
    this.module = modulePath;
    this.longName = longName;
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
    return module == null ? longName.toString() : module + ":" + longName;
  }
}
