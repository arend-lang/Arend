package org.arend.cat;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class CatPiTest extends TypeCheckingTestCase {
  @Test
  public void piTypeTest() {
    typeCheckDef("\\func test (X : \\Type) => \\Pi (x : X) -> Nat");
  }

  @Test
  public void piCatTest() {
    typeCheckDef("\\func test (X : \\Cat) => \\Pi (x : X) -> Nat", 1);
  }

  @Test
  public void piCatTest2() {
    typeCheckDef("\\func test (X : \\Cat) => \\Pi (x :. X) -> Nat", 1);
  }

  @Test
  public void piCatDotTest() {
    typeCheckDef("\\func test (X :. \\Cat) => \\Pi (x : X) -> Nat");
  }

  @Test
  public void lamTypeTest() {
    typeCheckDef("\\func test (X : \\Type) => \\lam (x : X) => 0");
  }

  @Test
  public void lamCatTest() {
    typeCheckDef("\\func test (X : \\Cat) => \\lam (x : X) => 0", 1);
  }

  @Test
  public void lamCatTest2() {
    typeCheckDef("\\func test (X : \\Cat) => \\lam (x :. X) => 0", 1);
  }

  @Test
  public void lamCatDotTest() {
    typeCheckDef("\\func test (X :. \\Cat) => \\lam (x : X) => 0");
  }
}
