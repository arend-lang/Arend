package org.arend.ext;

import org.arend.ext.concrete.definition.ConcreteDefinition;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.typechecking.DeferredMetaDefinition;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * DefinitionContributor is used to declare meta definitions defined in the extension.
 */
public interface DefinitionContributor {
  /**
   * Declares new concrete definition.
   */
  void declare(@NotNull Doc description, @NotNull ConcreteDefinition definition);

  /**
   * Declares new meta definition.
   *
   * @param metaRef       the reference to be declared
   * @param meta          the definition itself
   * @param resolver      the associated resolver of the definition
   */
  void declare(@NotNull Doc description, @NotNull MetaRef metaRef, @Nullable MetaDefinition meta, @Nullable MetaResolver resolver);

  default void declare(@NotNull Doc description, @NotNull MetaRef metaRef, @Nullable MetaDefinition meta) {
    declare(description, metaRef, meta, meta instanceof MetaResolver ? (MetaResolver) meta : meta instanceof DeferredMetaDefinition defMeta && defMeta.deferredMeta instanceof MetaResolver ? (MetaResolver) defMeta.deferredMeta : null);
  }
}
