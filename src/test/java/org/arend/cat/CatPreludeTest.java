package org.arend.cat;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.arend.Matchers.typecheckingError;

public class CatPreludeTest extends TypeCheckingTestCase {
  @Test
  public void invariantOk() {
    typeCheckModule("""
      \\func f (i : DI) : Nat \\elim i
        | dleft => 0
        | dright => 1
      """);
  }

  @Test
  public void covariantError() {
    typeCheckModule("""
      \\func g (i :+ DI) : Nat \\elim i
        | dleft => 0
        | dright => 1
      """, 2);
    assertThatErrorsAre(typecheckingError(), typecheckingError());
  }

  @Test
  public void invariantInDataOk() {
    typeCheckModule("""
      \\data D (i : DI) \\elim i
        | dleft => con1
        | dright => con2
      """);
  }

  @Test
  public void invariantInDataOk2() {
    typeCheckModule("""
      \\data D (i : DI) \\elim i
        | dleft => con
      """);
  }

  @Test
  public void covariantInDataError() {
    typeCheckModule("""
      \\data D (i :+ DI) \\elim i
        | dleft => Nat
        | dright => Int
      """, 2);
    assertThatErrorsAre(typecheckingError(), typecheckingError());
  }

  @Test
  public void pathAppTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} {a b : C} (p : a ~> b) (i :+ DI)
        => p i
      """);
  }

  @Test
  public void pathBetaTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} (f : DI ->+ C) : (\\lam i =>+ dpath f i) = (\\lam i =>+ f i)
        => idp
      """);
  }

  @Test
  public void pathBetaLeftTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} {a b : C} (p : a ~> b) : p dleft = a
        => idp
      """);
  }

  @Test
  public void pathBetaRightTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} {a b : C} (p : a ~> b) : p dright = b
        => idp
      """);
  }

  @Test
  public void pathEtaTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} {a b : C} (p : a ~> b) : dpath (\\lam i => p i) = p
        => idp
      """);
  }

  @Test
  public void pathTypeTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} {a b : C} : \\Type
        => a = b
      """);
  }

  @Test
  public void dPathTypeTest() {
    typeCheckModule("""
      \\func test {C : DI ->+ \\Cat} {a : C dleft} {b : C dright} : \\Type
        => DPath C a b
      """);
  }

  @Test
  public void dPathInfixTypeTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} {a b : C} : \\Type
        => a ~> b
      """);
  }

  @Test
  public void dPathInfTypeError() {
    typeCheckModule("""
      \\func test {C : DI ->+ \\Cat0} {a : C dleft} {b : C dright} : \\Type0
        => DPath C a b
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void dPathInfixInfTypeError() {
    typeCheckModule("""
      \\func test {C : \\Cat0} {a b : C} : \\Type0
        => a ~> b
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void intervalElimDI() {
    typeCheckModule("""
      \\func f (n : Nat) (i : DI) : Nat
        | zero, _ => 0
        | suc _, _ => 0
        | _, dleft => 0
        | _, dright => 0
      \\func g (n : Nat) : f n dleft = 0 => idp
      """);
  }

  @Test
  public void intervalElimDIError() {
    typeCheckModule("""
      \\func f (n : Nat) (i :+ DI) : Nat
        | zero, _ => 0
        | suc _, _ => 0
        | _, dleft => 0
        | _, dright => 0
      """, 2);
  }

  @Test
  public void intervalElimDICoverageError() {
    typeCheckModule("""
      \\func f (n : Nat) (i : DI) : Nat
        | zero, _ => 0
        | _, dleft => 0
        | _, dright => 0
      """, 1);
  }

  @Test
  public void intervalElimDAt() {
    typeCheckModule("""
      \\func myDAt {C : DI ->+ \\Cat} {a : C dleft} {b : C dright} (p : DPath C a b) (i : DI) : C i \\elim p, i
        | dpath f, i => f i
        | _, dleft => a
        | _, dright => b
      \\func g (p : 0 ~> 1) : myDAt p dright = 1 => idp
      """);
  }

  @Test
  public void intervalElimDAtConditionsError() {
    typeCheckModule("""
      \\func myDAt {A : \\Type} (a a' : A) (p : a ~> a') (i : DI) : A \\elim p, i
        | dpath f, i => f i
        | _, dleft => a'
        | _, dright => a
      """, 2);
  }

  @Test
  public void intervalElimMixedIAndDI() {
    typeCheckModule("""
      \\func mixed (i : I) (j : DI) : Nat
        | _, _ => 0
        | left, _ => 0
        | right, _ => 0
        | _, dleft => 0
        | _, dright => 0
      \\func testMixed : mixed left dleft = 0 => idp
      """);
  }

  @Test
  public void intervalElimDIConstructor() {
    typeCheckModule("""
      \\data D | con1 | con2 | dseg (d : D) (i : DI) \\elim i { | dleft => d | dright => con2 }
      \\func testLeft (d : D) : dseg d dleft = d => idp
      \\func testRight (d : D) : dseg d dright = con2 => idp
      """);
  }
}
