package org.arend.typechecking.constructions;


import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Sort;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UniverseLevelsTest extends TypeCheckingTestCase {
  @Test
  public void dataExpansion() {
    typeCheckModule("""
      \\data D (A : \\Type) (a : A) | d (B : A -> \\Type2)
      \\func f : \\Pi {A : \\Type1} {a : A} -> (A -> \\Type1) -> D A a => \\lam B => d B
      \\func test => f {\\Set0} {\\Prop} (\\lam _ => \\Type0)
      """);
  }

  @Test
  public void allowedInArgs() {
    typeCheckModule("\\func f.{u} (A : \\Type u -> \\Type u) => 0");
  }

  @Test
  public void notAllowedInArgs() {
    typeCheckModule("\\func f (A : \\Type -> \\Type) => 0", 1);
  }

  @Test
  public void allowedInResultType() {
    typeCheckModule("\\func g.{u} : \\Type u -> \\Type u => \\lam X => X");
  }

  @Test
  public void notAllowedInResultType() {
    typeCheckModule("\\func g : \\Type -> \\Type => \\lam X => X", -1);
  }

  @Test
  public void allowedAsExpression() {
    typeCheckModule("\\func f.{u} => \\Type u");
  }

  @Test
  public void notAllowedAsExpression() {
    typeCheckModule("\\func f => \\Type", 1);
  }

  @Test
  public void equalityOfTypes() {
    typeCheckModule("\\func f.{u} (A B : \\Type u) => A = B");
  }

  @Test
  public void equalityOfInfiniteTypes() {
    typeCheckModule("\\func f (A B : \\Type) => A = B", -1);
  }

  @Test
  public void callPolyFromOmega() {
     typeCheckModule(
         "\\func f (A : \\Type) => A\n" +
         "\\func g (A : \\Type) => f A");
  }

  @Test
  public void typeOmegaResult() {
    typeCheckModule("\\func f (A : \\Type) : \\Type => A");
  }

  @Test
  public void callNonPolyFromOmega() {
    typeCheckModule(
        "\\func f (A : \\Type0) => 0\n" +
        "\\func g (A : \\Type) => f A", 1);
  }

  @Test
  public void levelH() {
    parseExpr("\\Type1 3", 1);
  }

  @Test
  public void truncatedLevel() {
    assertEquals(new UniverseExpression(new Sort(7, 2)), typeCheckExpr("\\2-Type 7", null).expression);
  }

  @Test
  public void func() {
    typeCheckModule(
      "\\data Foo (A : \\Type) | foo A\n" +
      "\\func bar.{u} (A : \\Type u) => Foo A");
  }

  @Test
  public void dataMaxTest() {
    typeCheckModule(
      "\\data Foo (A : \\Type) | foo A\n" +
      "\\data Bar.{u} (A : \\Type u) | bar (Foo A)");
  }
}
