package org.arend.frontend;

import org.arend.error.SourcePosition;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.reference.DataContainer;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ProgressReporter;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.List;

/**
 * Wraps another {@link ProgressReporter} and emits a stderr warning when a single
 * per-element typecheck exceeds {@code thresholdMs}. An element is normally one
 * definition; mutually recursive clusters arrive as a list, in which case all
 * names are listed in the warning.
 *
 * <p>Composable: pass {@link ProgressReporter#empty()} as the delegate when no
 * other reporter is needed; otherwise wrap (e.g.) {@link TimedProgressReporter}
 * to combine per-def warnings with the full {@code --show-times} summary.
 */
public final class SlowDefinitionWarningReporter
    implements ProgressReporter<List<? extends Concrete.ResolvableDefinition>> {

  private final ProgressReporter<List<? extends Concrete.ResolvableDefinition>> delegate;
  private final long thresholdMs;
  private final PrintStream out;
  private long itemStart;

  public SlowDefinitionWarningReporter(
      @Nullable ProgressReporter<List<? extends Concrete.ResolvableDefinition>> delegate,
      long thresholdMs,
      @NotNull PrintStream out) {
    this.delegate = delegate;
    this.thresholdMs = thresholdMs;
    this.out = out;
  }

  @Override
  public void beginProcessing(int numberOfItems) {
    if (delegate != null) delegate.beginProcessing(numberOfItems);
  }

  @Override
  public void beginItem(@NotNull List<? extends Concrete.ResolvableDefinition> item) {
    itemStart = System.currentTimeMillis();
    if (delegate != null) delegate.beginItem(item);
  }

  @Override
  public void endItem(@NotNull List<? extends Concrete.ResolvableDefinition> item) {
    long elapsed = System.currentTimeMillis() - itemStart;
    if (delegate != null) delegate.endItem(item);
    if (elapsed > thresholdMs && !item.isEmpty()) {
      String header = "[WARN] Slow typecheck (" + TimedProgressReporter.timeToString(elapsed)
          + ", threshold " + TimedProgressReporter.timeToString(thresholdMs) + ")"
          + (item.size() > 1 ? " — mutual group of " + item.size() + ":" : ":");
      out.println(header);
      for (Concrete.ResolvableDefinition def : item) {
        out.println("  " + format(def.getData()));
      }
      out.flush();
    }
  }

  /** {@code <library>::<module>:<longName>  at <abs-path-or-module>:<line>:<col>}. */
  private static String format(TCDefReferable ref) {
    ModuleLocation loc = ref.getLocation();
    String library = loc == null ? "?" : loc.getLibraryName();
    String module  = loc == null || loc.getModulePath() == null ? "?" : loc.getModulePath().toString();
    String longName = String.valueOf(ref.getRefLongName());
    int[] pos = positionOf(ref);
    StringBuilder sb = new StringBuilder();
    sb.append(library).append("::").append(module).append(":").append(longName);
    if (pos[0] > 0) sb.append("  at ").append(module).append(":").append(pos[0]).append(":").append(pos[1]);
    return sb.toString();
  }

  private static int[] positionOf(LocatedReferable ref) {
    Object data = ref instanceof DataContainer dc ? dc.getData() : null;
    if (data instanceof SourcePosition sp) return new int[] { sp.line, sp.column };
    return new int[] { 0, 0 };
  }
}
