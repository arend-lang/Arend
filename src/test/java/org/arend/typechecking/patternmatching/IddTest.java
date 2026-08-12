package org.arend.typechecking.patternmatching;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.local.IdpPatternError;
import org.junit.Test;

public class IddTest extends TypeCheckingTestCase {
  @Test
  public void jTest() {
    typeCheckModule("""
      \\func J {C : \\Type} {a : C} (B : \\Pi {a' : C} -> a ~> a' -> \\Type) (b : B idd) {a' : C} (p : a ~> a') : B p \\elim p
        | idd => b
      \\func test : J (\\lam {n} _ => n ~> 0) idd idd = idd => idp
      """);
  }

  @Test
  public void kTest() {
    typeCheckModule("""
      \\func K {C : \\Type} {a : C} (B : a ~> a -> \\Type) (b : B idd) (p : a ~> a) : B p \\elim p
        | idd => b
      """, 1);
  }

  @Test
  public void natTest() {
    typeCheckModule("""
      \\func test {n : Nat} (p : n ~> n Nat.+ n) (B : Nat -> \\Type) (b : B n) : B (n Nat.+ n) \\elim p
        | idd => b
      """, 1);
  }

  @Test
  public void reorderTest() {
    typeCheckModule(
      "\\func f {C : \\Type} (B : C -> \\Type) {a a' : C} (b : B a) (b' : B a') (p : a ~> a') : \\Sigma (B a) (B a) (B a') (B a') \\elim p\n" +
      "  | idd => (b,b',b,b')");
  }

  @Test
  public void nestedIddTest() {
    typeCheckModule("""
      \\data \\infix 4 <= (n m : Nat) \\with
        | 0, _ => zero<=_
        | suc n, suc m => suc<=suc (n <= m)
      \\data D (n : Nat) | con (n ~> 0) | con' (1 ~> n)
      \\func f (x : Nat) (d : D x) : x <= 1 \\elim d
        | con idd => zero<=_
        | con' idd => suc<=suc zero<=_
      """);
  }

  @Test
  public void multipleIddTest() {
    typeCheckDef("""
      \\func f {C : \\Type} {a1 a2 a3 a4 : C} (p : a1 ~> a2) (q : a2 ~> a3) (r : a4 ~> a3) : a1 ~> a4
        | idd, idd, idd => idd
      """);
  }

  @Test
  public void multipleIddError() {
    typeCheckDef("""
      \\func f {C : \\Type} {a1 a2 a3 : C} (p : a1 ~> a2) (q : a2 ~> a3) (r : a1 ~> a3) : a1 ~> a3
        | idd, idd, idd => idd
      """, 1);
  }

  @Test
  public void multipleIddError2() {
    typeCheckDef("""
      \\func f {C : \\Type} {a1 a2 a3 : C} (p : a1 ~> a2) (q : a2 ~> a3) (r : a3 ~> a1) : a1 ~> a3
        | idd, idd, idd => idd
      """, 1);
  }

  @Test
  public void chooseVarTest() {
    typeCheckModule("""
      \\func f {C : \\Type} (B : C -> \\Type) {a : C} (b : B a) {a' : C} (p : a ~> a') : B a' \\elim p
        | idd => b
      \\func g {C : \\Type} (B : C -> \\Type) {a' : C} (b : B a') {a : C} (p : a ~> a') : B a' \\elim p
        | idd => b
      """);
  }

  @Test
  public void recordTest() {
    typeCheckModule("""
      \\record R (n m : Nat)
      \\func test {n : Nat} (r : R n) (r' : R n) (p : r ~> {R} r') : Nat \\elim p
        | idd => 0
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(IdpPatternError.class));
  }

  @Test
  public void notInTypeError() {
    typeCheckModule("""
      \\func f {C : \\Cat} {a b : C} (p : a ~> b) (B : C -> \\Type) (x : B a) : B b \\elim p
        | idd => x
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(IdpPatternError.class));
  }

  @Test
  public void inTypeOk() {
    typeCheckModule("""
      \\func f {C : \\Type} {a b : C} (p : a ~> b) (B : C -> \\Type) (x : B a) : B b \\elim p
        | idd => x
      """);
  }
}
