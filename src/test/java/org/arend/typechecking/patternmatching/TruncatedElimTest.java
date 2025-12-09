package org.arend.typechecking.patternmatching;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.arend.Matchers.*;

public class TruncatedElimTest extends TypeCheckingTestCase {
  @Test
  public void propElim() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\func f (x : D) : 0 = 0
        | con1 => idp
      """);
  }

  @Test
  public void propElimWarn() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\func f (x : D) : 0 = 0
        | con1 => idp
        | con2 _ => idp
      """, 1);
    assertThatErrorsAre(warning());
  }

  @Test
  public void propElimPartial() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 }
      \\func f (x : D) : 0 = 0
        | con1 => idp
      """, 1);
    assertThatErrorsAre(missingClauses(1));
  }

  @Test
  public void propElimNotProp() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\func f (x : D) : Nat
        | con1 => 0
      """, 1);
    assertThatErrorsAre(missingClauses(1));
  }

  @Test
  public void setElim() {
    typeCheckModule("""
      \\data D | con1 | con2 I I { | left, _ => con1 | right, _ => con1 | _, left => con1 | _, right => con1 }
      \\func f (x : D) : Nat
        | con1 => 0
      """);
  }

  @Test
  public void setElimPartial() {
    typeCheckModule("""
      \\data D | con1 | con2 I I { | left, _ => con1 | right, _ => con1 | _, left => con1 }
      \\func f (x : D) : Nat
        | con1 => 0
      """, 1);
    assertThatErrorsAre(missingClauses(1));
  }

  @Test
  public void setElimNotSet() {
    typeCheckModule("""
      \\data D | con1 | con2 I I { | left, _ => con1 | right, _ => con1 | _, left => con1 | _, right => con1 }
      \\func f (x : D) : x = x
        | con1 => idp
      """, 1);
    assertThatErrorsAre(missingClauses(1));
  }

  @Test
  public void multiplePatternsSet() {
    typeCheckModule("""
      \\data D
        | con1
        | con2 I { | left => con1 | right => con1 }
        | con3 I I { | left, _ => con1 | right, _ => con1 | _, left => con1 | _, right => con1 }
      \\func f (x y : D) : Nat
        | con1, con1 => 0
        | con2 _, con1 => 0
        | con1, con2 _ => 0
      """);
  }

  @Test
  public void multiplePatterns1Type() {
    typeCheckModule("""
      \\data D
        | con1
        | con2 I { | left => con1 | right => con1 }
        | con3 I I { | left, _ => con1 | right, _ => con1 | _, left => con1 | _, right => con1 }
      \\func f (x y : D) : \\Set
        | con1, con1 => Nat
        | con2 _, con1 => Nat
        | con1, con2 _ => Nat
        | con1, con3 _ _ => Nat
        | con3 _ _, con1 => Nat
        | con2 _, con2 _ => Nat
      """);
  }

  @Test
  public void multiplePatterns1TypeError() {
    typeCheckModule("""
      \\data D
        | con1
        | con2 I { | left => con1 | right => con1 }
        | con3 I I { | left, _ => con1 | right, _ => con1 | _, left => con1 | _, right => con1 }
      \\func f (x y : D) : \\Set
        | con1, con1 => Nat
        | con2 _, con1 => Nat
        | con1, con2 _ => Nat
        | con1, con3 _ _ => Nat
        | con3 _ _, con1 => Nat
      """, 1);
    assertThatErrorsAre(missingClauses(1));
  }

  @Test
  public void caseTest() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\func f (x : D) => \\case x \\return 0 = 0 \\with {
        | con1 => idp
      }
      """);
  }

  @Test
  public void caseTest2() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\data Maybe (A : \\Sort) | just A | nothing
      \\func f (x : D) => \\case x \\return just 0 = just 0 \\with {
        | con1 => idp
      }
      """);
  }

  @Test
  public void caseTestError() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\data Maybe (A : \\Type) | just A | nothing
      \\func f (x : D) => \\case x \\return (Nat : \\Prop) \\with {
        | con1 => 0
      }
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Test
  public void caseTest3() {
    typeCheckModule("""
      \\data Unit | unit
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\func f (x : D) : Unit => \\case x \\with {
        | con1 => unit
      }
      """);
  }

  @Test
  public void levelTest() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\data Empty
      \\data Bool | true | false
      \\func E (b : Bool) : \\Set0 | true => Empty | false => Empty
      \\func E-isProp (b : Bool) (x y : E b) : x = y \\elim b, x | true, () | false, ()
      \\func f (b : Bool) (x : E b) (d : D) : \\level (E b) (E-isProp b) \\elim d | con1 => x
      """);
  }

  @Test
  public void caseLevelTest() {
    typeCheckModule("""
      \\data D | con1 | con2 I { | left => con1 | right => con1 }
      \\data Empty
      \\data Bool | true | false
      \\func E (b : Bool) : \\Set0 | true => Empty | false => Empty
      \\func E-isProp (b : Bool) (x y : E b) : x = y \\elim b, x | true, () | false, ()
      \\func f (b : Bool) (x : E b) (d : D) => \\case d \\return \\level (E b) (E-isProp b) \\with { | con1 => x }
      """);
  }

  @Test
  public void truncatedLevelTest() {
    typeCheckModule("""
      \\truncated \\data \\infixr 2 || (A B : \\Type) : \\Prop
          | byLeft A
          | byRight B
      \\sfunc rec {A B C : \\Type} (p : \\Pi (x y : C) -> x = y) (f : A -> C) (g : B -> C) (t : A || B) : \\level C p \\elim t
        | byLeft a => f a
        | byRight b => g b
      """);
  }
}
