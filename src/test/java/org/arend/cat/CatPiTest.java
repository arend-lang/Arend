package org.arend.cat;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class CatPiTest extends TypeCheckingTestCase {
  @Test
  public void piCatTest() {
    typeCheckDef("\\func test (X : \\Cat) => X -> Nat");
  }

  @Test
  public void piCatTest2() {
    typeCheckDef("\\func test (X : \\Cat) => \\Pi (x :+ X) -> Nat");
  }

  @Test
  public void piCovariantTest() {
    typeCheckDef("\\func test (X :+ \\Prop) => X -> Nat", 1);
  }

  @Test
  public void lamCatTest() {
    typeCheckDef("\\func test (X : \\Cat) => \\lam (x : X) => 0");
  }

  @Test
  public void lamCatTest2() {
    typeCheckDef("\\func test (X : \\Cat) => \\lam (x :+ X) => 0");
  }

  @Test
  public void lamCatTest3() {
    typeCheckDef("\\func test (X :+ \\Cat) => \\lam (x : X) => 0", 1);
  }

  @Test
  public void lamCatTest4() {
    typeCheckDef("\\func test (A B : \\Cat) (b : B) : \\Pi (x : A) -> B => \\lam x =>+ b", 1);
  }

  @Test
  public void lamCatTest5() {
    typeCheckDef("\\func test (A B : \\Cat) (b : B) : \\Pi (x :+ A) -> B => \\lam x =>+ b");
  }

  @Test
  public void lamCatTest6() {
    typeCheckDef("\\func test (A B : \\Cat) (b : B) : \\Pi (x :+ A) -> B => \\lam x => b");
  }

  @Test
  public void paramTest() {
    typeCheckModule("""
      \\func def (X : \\Cat) (x :+ X) => 0
      \\func test (X : \\Cat) => def X
      """);
  }

  @Test
  public void paramError() {
    typeCheckModule("""
      \\func def (X :+ \\Cat) (x : X) => 0
      \\func test (X :+ \\Cat) => def X
      """, 1);
  }
}
