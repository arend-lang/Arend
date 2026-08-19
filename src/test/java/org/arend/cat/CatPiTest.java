package org.arend.cat;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.arend.Matchers.typecheckingError;

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
  public void piCovariantTest2() {
    typeCheckDef("\\func test (X :+ \\Prop) => X ->+ Nat", 1);
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

  @Test
  public void covariantParamInTypeTest() {
    typeCheckDef("\\func test {C : \\Cat} {a : C} (x :+ C) (p :+ a ~> x) => 0");
  }

  @Test
  public void covariantParamInTypeError() {
    typeCheckDef("\\func test {C : \\Cat} {a : C} (x :+ C) (p : a ~> x) => 0", 1);
    assertThatErrorsAre(typecheckingError());
  }

  @Test
  public void partiallyAppliedLetTest() {
    typeCheckModule("""
      \\func foo (n :+ Nat) => n
      \\func bar (n :+ Nat) =>
       \\let t => foo
       \\in t n
      """);
  }

  @Test
  public void partiallyAppliedVarianceChangeTest() {
    typeCheckModule("""
      \\func def (X : \\Cat) (x :+ X) => 0
      \\func test (X : \\Cat) : X -> Nat => def X
      """);
  }

  @Test
  public void piParamTest() {
    typeCheckModule("""
      \\func foo {C D :+ \\Cat} (f :+ C ->+ D) => f
      \\func test (C :+ \\Cat) => foo {C} \\lam x => x
      """);
  }

  @Test
  public void piParamTest2() {
    typeCheckModule("""
      \\func foo {C D :+ \\Cat} (f :+ C ->+ D ->+ C) => f
      \\func test (C D :+ \\Cat) => foo {C} {D} \\lam x y => x
      """);
  }

  @Test
  public void piParamError() {
    typeCheckDef("\\func foo {C D :+ \\Cat} (f :+ C -> D ->+ C) => f", 1);
  }

  @Test
  public void piParamError2() {
    typeCheckDef("\\func foo {C D :+ \\Cat} (f :+ C ->+ D -> C) => f", 1);
  }

  @Test
  public void piParamPartiallyAppliedError() {
    typeCheckModule("""
      \\func foo {C D :+ \\Cat} (f :+ C ->+ D) => f
      \\func test (C :+ \\Cat) => foo {C} {C}
      """, 1);
  }

  @Test
  public void piParamContravariantError() {
    typeCheckDef("\\func foo {C D :+ \\Cat} (f :+ (C ->+ Nat) ->+ D) => f", 1);
  }

  @Test
  public void piParamDataTest() {
    typeCheckModule("""
      \\data Foo {C D :+ \\Cat} (f :+ C ->+ D)
      \\func test (C :+ \\Cat) => Foo {C} \\lam x => x
      """);
  }

  @Test
  public void piParamClassTest() {
    typeCheckDef("\\record R {C D :+ \\Cat} (f :+ C ->+ D)", 1);
  }

  @Test
  public void piParamConstructorTest() {
    typeCheckModule("""
      \\data Foo (C D :+ \\Cat)
        | con (f :+ C ->+ D)
      """, 1);
  }
}
