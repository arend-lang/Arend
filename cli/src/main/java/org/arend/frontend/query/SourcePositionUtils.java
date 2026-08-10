package org.arend.frontend.query;

import org.arend.error.SourcePosition;
import org.arend.ext.reference.DataContainer;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts the {@link SourcePosition} (line/column) a referable was declared at. The
 * console tools all read it the same way — unwrap the referable's {@link DataContainer}
 * data and, if it is a {@code SourcePosition}, take its line and column — so the logic
 * lives here once.
 */
public final class SourcePositionUtils {
  /**
   * The {@link SourcePosition} backing {@code referableOrData}, or {@code null} when
   * absent. Accepts either a referable (its {@link DataContainer#getData() data} is
   * unwrapped) or an already-unwrapped data object.
   */
  public static @Nullable SourcePosition of(@Nullable Object referableOrData) {
    Object data = referableOrData instanceof DataContainer dc ? dc.getData() : referableOrData;
    return data instanceof SourcePosition sp ? sp : null;
  }

  /** {@code {line, column}} of {@code referableOrData}'s position, or {@code {0, 0}} when unknown. */
  public static int[] lineColumn(@Nullable Object referableOrData) {
    SourcePosition sp = of(referableOrData);
    return sp == null ? new int[] {0, 0} : new int[] {sp.line, sp.column};
  }
}
