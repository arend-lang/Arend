package org.arend.ext.typechecking;

import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.error.ErrorReporter;
import org.jetbrains.annotations.NotNull;

/**
 * A meta definition that is used as an attribute ({@code \attr}) can implement this interface
 * to validate definitions marked with it.
 */
public interface AttributeChecker {
  /**
   * Invoked after a definition marked with this attribute is typechecked.
   * The checker can report errors if the definition does not have the expected shape.
   * It can also attach preprocessed data to the definition (as user data) to speed up later lookups.
   *
   * @param definition      the typechecked definition marked with this attribute
   * @param errorReporter   an error reporter for the definition
   */
  void checkAttributedDefinition(@NotNull CoreDefinition definition, @NotNull ErrorReporter errorReporter);
}
