package org.arend.module;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class ModuleLocationTest {
  /**
   * Modules are kept in hash-ordered collections that get iterated — {@code
   * ArendServer.getModules()} is a {@code ConcurrentHashMap} keyed by {@link ModuleLocation}, and
   * the binary-cache loader walks it. So the hash has to be a function of the module's identity
   * alone, not of anything the JVM makes up at runtime.
   *
   * <p>The trap is {@link ModuleLocation.LocationKind}: {@code Enum.hashCode()} is the identity
   * hash, which differs between runs and, within one run, depends on how many objects happened to
   * be hashed on the thread beforehand. Folding the constant itself into the hash therefore made
   * the same command over the same files iterate modules in a different order depending on
   * whether an unrelated startup probe had run first. Comparing against {@code ordinal()} pins
   * that down without pinning the particular hash algorithm or the order of the constants.
   */
  @Test
  public void hashCodeDoesNotDependOnIdentityHashes() {
    ModulePath path = new ModulePath("Algebra", "Group");
    for (ModuleLocation.LocationKind kind : ModuleLocation.LocationKind.values()) {
      assertEquals("ModuleLocation.hashCode() must not mix in " + kind + "'s identity hash",
          Objects.hash("arend-lib", kind.ordinal(), path),
          new ModuleLocation("arend-lib", kind, path).hashCode());
    }
  }
}
