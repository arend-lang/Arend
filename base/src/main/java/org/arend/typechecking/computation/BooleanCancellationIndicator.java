package org.arend.typechecking.computation;

public class BooleanCancellationIndicator implements CancellationIndicator {
    // Volatile: a daemon cancels a request from another thread than the one checking it.
    public volatile boolean isCancelled = false;

    @Override
    public boolean isCanceled() {
        return isCancelled;
    }

    @Override
    public void cancel() {
        isCancelled = true;
    }
}
