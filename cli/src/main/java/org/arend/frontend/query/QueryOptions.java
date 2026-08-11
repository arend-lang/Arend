package org.arend.frontend.query;

/**
 * Options common to every query tool; each tool's {@code Options} extends this so
 * the cross-cutting fields live in one place.
 */
public class QueryOptions {
  /** Emit results as a single JSON object instead of the human-readable listing. */
  public boolean json = false;

  /** Restrict results to the top-level (requested) libraries, i.e., do not look into dependencies. */
  public boolean self = false;

  /**
   * Cap on the number of results emitted; 0 = unlimited. The tool still counts every
   * match for the reported total, so a truncated listing reports the full count.
   * {@code -fu} raises the default to 500 (usage sets run larger); {@code -sc} ignores
   * this entirely (a scope dump is intrinsically bounded).
   */
  public int limit = 200;

  /** Number of items to emit for a given {@code total}, honouring {@link #limit} (0 = unlimited). */
  public int capped(int total) {
    return limit > 0 ? Math.min(total, limit) : total;
  }

  /**
   * The footer suffix flagging truncation -- {@code " (showing N; pass `limit=0` for all)"}
   * when {@code shown < total}, empty otherwise. Shared by the flat listers
   * ({@code -ss}/{@code -ps}/{@code -fu}).
   */
  public String truncationNote(int shown, int total) {
    return shown < total ? " (showing " + shown + "; pass `limit=0` for all)" : "";
  }
}
