package org.arend.module.serialization;

import org.arend.core.definition.Definition;
import org.arend.core.expr.DefCallExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * Repairs {@code \box} wrapping on defcalls that were deserialized before their callee's
 * parameters existed.
 *
 * <p>{@link DefCallExpression#fixBoxes()} runs eagerly from the {@code make} of
 * {@code FunCallExpression} / {@code DataCallExpression} / {@code ConCallExpression}, and it
 * decides what to wrap by walking {@code definition.getParameters()}. A callee that has only
 * been through {@code readDefinitions} is a shell whose parameter list is still empty, so
 * {@code fixBoxes} concludes there is nothing to box and returns. Filling the callee in
 * afterwards does not revisit the already-constructed argument list, so the boxing is lost for
 * good.
 *
 * <p>That matters because property arguments are proof-irrelevant: a term that should have been
 * boxed and was not is <em>printed identically</em> to the correct one but does not compare
 * equal to it. The resulting diagnostics ({@code Expressions are not equal}, {@code Type
 * mismatch} between types that look the same) point at whichever definition later consumes the
 * term, with nothing tying them back to the cache.
 *
 * <p>{@code BinaryLoader} loads a whole library at once and cannot guarantee that every callee
 * is filled before its callers — import cycles make some orderings impossible in principle. So
 * rather than depend on order, the deserializer records each defcall it builds against an
 * unfilled callee and re-runs {@code fixBoxes} on exactly those once every module is filled.
 * Since the recorded calls are precisely the ones for which {@code fixBoxes} did nothing, the
 * replay is their first effective run and cannot double-wrap.
 *
 * <p>Not thread-safe: a single instance belongs to one library-loading pass.
 */
public final class DeferredBoxFixes {
  private final List<DefCallExpression> myPending = new ArrayList<>();

  /**
   * Records {@code expr} if its callee had no parameters at the time {@code fixBoxes} ran, which
   * is exactly the condition under which that run cannot have wrapped anything.
   *
   * <p>The empty-parameter-list test is what makes this safe, and it cannot be replaced by a
   * status test: a definition is filled parameters-first and only gets its final status at the
   * end, so a recursive call inside a definition's own body sees a callee that still "needs
   * typechecking" yet already has complete parameters. {@code fixBoxes} handled that one
   * correctly, and replaying it would wrap the arguments a second time. The status test is then
   * added purely to skip callees that are genuinely parameterless, for which the replay would be
   * a no-op -- on a full arend-lib load that is the difference between ~120k and ~430k replays.
   */
  void deferIfUnfilled(DefCallExpression expr, Definition callee) {
    if (callee != null && !callee.getParameters().hasNext() && callee.status().needsTypeChecking()) {
      myPending.add(expr);
    }
  }

  /**
   * Re-runs {@code fixBoxes} on every recorded defcall and clears the record. Call once all
   * modules of the pass have been filled in; the pending list is empty in the common case.
   *
   * @return how many defcalls were revisited.
   */
  public int apply() {
    int count = myPending.size();
    for (DefCallExpression expr : myPending) {
      expr.fixBoxes();
    }
    myPending.clear();
    return count;
  }
}
