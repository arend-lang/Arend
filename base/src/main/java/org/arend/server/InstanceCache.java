package org.arend.server;

import org.arend.core.expr.Expression;
import org.arend.naming.reference.TCDefReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface InstanceCache {
  /**
   * Checks if the cache has any available instances.
   * This method is quicker than getting the list of all available instances.
   */
  boolean hasInstances(@NotNull TCDefReferable classRef);

  /**
   * Finds available instances for the given class.
   *
   * @param classRef                a class reference.
   * @param classifyingExpression   the classifying expression of the required instance; if {@code null}, all available instances are returned.
   * @return the list of possible solutions. Each solution is a list of instances that are required for the solution to work.
   */
  @NotNull List<List<TCDefReferable>> getAvailableInstances(@NotNull TCDefReferable classRef, @Nullable Expression classifyingExpression);
}
