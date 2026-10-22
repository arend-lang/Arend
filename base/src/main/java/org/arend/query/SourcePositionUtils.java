package org.arend.query;

import org.arend.error.SourcePosition;
import org.arend.ext.reference.DataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Extracts the {@link SourcePosition} (line/column) a referable was declared at. The
 * console tools all read it the same way — unwrap the referable's {@link DataContainer}
 * data and, if it is a {@code SourcePosition}, take its line and column — so the logic
 * lives here once.
 *
 * <p>Data that is not a {@code SourcePosition} goes to the resolver the caller gave this thread,
 * if any: the IntelliJ plugin's concrete trees carry PSI elements, whose position only the IDE can
 * work out.
 */
public final class SourcePositionUtils {
  private static final ThreadLocal<Function<Object, SourcePosition>> RESOLVER = new ThreadLocal<>();

  /** Runs {@code body} with {@code resolver} working out the positions of this thread's other data. */
  public static <T> T withResolver(@NotNull Function<Object, @Nullable SourcePosition> resolver, @NotNull Supplier<T> body) {
    Function<Object, SourcePosition> previous = RESOLVER.get();
    RESOLVER.set(resolver);
    try {
      return body.get();
    } finally {
      if (previous != null) RESOLVER.set(previous); else RESOLVER.remove();
    }
  }

  /**
   * The {@link SourcePosition} backing {@code referableOrData}, or {@code null} when
   * absent. Accepts either a referable (its {@link DataContainer#getData() data} is
   * unwrapped) or an already-unwrapped data object.
   */
  public static @Nullable SourcePosition of(@Nullable Object referableOrData) {
    Object data = referableOrData instanceof DataContainer dc ? dc.getData() : referableOrData;
    if (data instanceof SourcePosition sp) return sp;
    Function<Object, SourcePosition> resolver = RESOLVER.get();
    return resolver == null || data == null ? null : resolver.apply(data);
  }

  /** {@code {line, column}} of {@code referableOrData}'s position, or {@code {0, 0}} when unknown. */
  public static int[] lineColumn(@Nullable Object referableOrData) {
    SourcePosition sp = of(referableOrData);
    return sp == null ? new int[] {0, 0} : new int[] {sp.line, sp.column};
  }
}
