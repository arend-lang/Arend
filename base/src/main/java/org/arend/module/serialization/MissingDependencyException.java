package org.arend.module.serialization;

import org.arend.ext.serialization.DeserializationException;

/**
 * A cache refers to a module or a definition that is not loaded. This says what has been loaded so far,
 * not that the cache is broken: it can be read once its dependencies are.
 */
public class MissingDependencyException extends DeserializationException {
  public MissingDependencyException(String message) {
    super(message);
  }
}
