package org.arend.ext;

import org.arend.ext.concrete.definition.ConcreteDefinition;
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
}
