package org.arend.typechecking.implicitargs;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

/**
 * An inference variable compared with a \Pi-type whose codomain mentions this variable has no solution.
 * Such an equation must be reported as an error instead of being unfolded forever.
 */
public class InfiniteTypeTest extends TypeCheckingTestCase {
  private static final String DEFS = """
    \\func mk {B : \\Type} (b : B) : Nat -> B => \\lam _ => b
    \\func mk2 {B : \\Type} (b : B) : Nat -> Nat -> B => \\lam _ _ => b
    \\func same {A : \\Type} (x y : A) => 0
    """;

  @Test
  public void cycleThroughEquations() {
    typeCheckModule(DEFS + "\\func test => \\lam (y : _) => same y (mk y)", 1);
  }

  @Test
  public void cycleThroughEquationsReversed() {
    typeCheckModule(DEFS + "\\func test => \\lam (y : _) => same (mk y) y", 1);
  }

  @Test
  public void nestedCodomain() {
    typeCheckModule(DEFS + "\\func test => \\lam (y : _) => same y (mk2 y)", 1);
  }

  @Test
  public void mutualCycle() {
    typeCheckModule(DEFS + "\\func test => \\lam (y z : _) => (same y (mk z), same z (mk y))", 1);
  }

  @Test
  public void finiteType() {
    typeCheckModule(DEFS + "\\func test (n : Nat) => \\lam (y : _) => (same y (mk n), same (y 0) n)");
  }
}
