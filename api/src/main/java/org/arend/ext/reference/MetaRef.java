package org.arend.ext.reference;

import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.ext.typechecking.meta.MetaTypechecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reference to a meta definition.
 */
public interface MetaRef extends ArendRef {
  @NotNull MetaTypechecker getTypechecker();
  @Nullable MetaDefinition getDefinition();
  @Nullable MetaResolver getResolver();
}
