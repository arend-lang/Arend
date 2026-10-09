package org.arend.typechecking.computation;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cancelled once its flag is set, by whoever holds the flag: a daemon flips it on a client's {@code cancel}. */
public final class FlagCancellationIndicator implements CancellationIndicator {
  private final AtomicBoolean flag;

  public FlagCancellationIndicator(AtomicBoolean flag) {
    this.flag = flag;
  }

  @Override
  public boolean isCanceled() {
    return flag.get();
  }

  @Override
  public void cancel() {
    flag.set(true);
  }
}
