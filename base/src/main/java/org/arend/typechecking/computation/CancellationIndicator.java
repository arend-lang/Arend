package org.arend.typechecking.computation;

import org.arend.naming.reference.TCDefReferable;
import org.arend.util.ComputationInterruptedException;

import java.util.Set;

public interface CancellationIndicator {
  boolean isCanceled();
  void cancel();

  default void cancel(Set<? extends TCDefReferable> definitions) {
    cancel();
  }

  default void checkCanceled() throws ComputationInterruptedException {
    if (isCanceled()) {
      throw new ComputationInterruptedException();
    }
  }
}
