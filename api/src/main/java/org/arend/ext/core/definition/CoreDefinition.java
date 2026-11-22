package org.arend.ext.core.definition;

import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.userData.UserDataHolder;
import org.arend.ext.variable.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a core definition.
 */
public interface CoreDefinition extends Variable, UserDataHolder {
  /**
   * Returns the underlying reference of this definition.
   */
  @NotNull ArendRef getRef();

  /**
   * Returns the linked list of parameters of this definition.
   * This list is always empty for class definitions and consists of a single parameter for fields.
   */
  @NotNull CoreParameter getParameters();

  /**
   * Returns the set of definitions mutually recursive with the given one including itself.
   * If the definition is not recursive, the result will be empty.
   * If the definition is recursive, then the result will be a singleton set consisting of the given definition.
   */
  @NotNull Set<? extends CoreDefinition> getRecursiveDefinitions();

  @Nullable CoreCoerceData getCoerceData();

  /**
   * Returns the attributes of this definition, that is, the references listed after {@code \attr}.
   */
  default @NotNull List<? extends ArendRef> getAttributes() {
    return Collections.emptyList();
  }
}
