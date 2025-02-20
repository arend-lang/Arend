package org.arend.typechecking.computation;

import org.arend.naming.reference.TCReferable;
import org.arend.util.ComputationInterruptedException;

import java.util.Set;

public interface CancellationIndicator {
  boolean isCanceled();
  void cancel();

  default void cancel(Set<? extends TCReferable> definitions) {
    cancel();
  }

  default void checkCanceled() throws ComputationInterruptedException {
    if (isCanceled()) {
      throw new ComputationInterruptedException();
    }
  }
}
