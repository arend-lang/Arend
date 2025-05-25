package org.arend.ext;

import org.arend.ext.concrete.definition.ConcreteDefinition;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * DefinitionContributor is used to declare meta definitions defined in the extension.
 */
public interface DefinitionContributor {
  /**
   * Declares new concrete definition.
   */
  void declare(@NotNull Doc description, @NotNull ConcreteDefinition definition);

  /**
   * Declares a import command in a module.
   */
  void declare(@NotNull ModulePath module, @NotNull ModulePath importedModule, @NotNull String... names);
}
