package org.arend.classes;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class ThisTest extends TypeCheckingTestCase {
  @Test
  public void mutualRecursionError() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\record S \\extends R { | x => R.y | y => R.x }
      """, 1);
  }

  @Test
  public void thisRecursive() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X) {
        \\func f (n : Nat) : X -> X \\elim n
          | 0 => x
          | suc n => f n
      }
      \\record S \\extends R | t : X | g : R.f 0 t = t
      """);
  }

  @Test
  public void thisRecursiveData() {
    typeCheckModule("""
      \\record R (X : \\Type) {
        \\data D
          | con1 D
          | con2
      }
      \\record S \\extends R | g : R.D
      """);
  }

  @Test
  public void constructorsWithPatterns() {
    typeCheckModule("""
      \\record R (X : \\Type) {
        \\data D (n : Nat) \\with
          | zero => con1
          | suc n => con2 (D n)
      }
      \\record S \\extends R | g : R.D 0
      """);
  }

  @Test
  public void thisArgument() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\func f (r : R) => r.x
      \\record S \\extends R | t : X | g : f \\this t = t
      """);
  }

  @Test
  public void thisRecursiveArgument() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\func f (n : Nat) (r : R) : r.X -> r.X \\elim n
        | 0 => r.x
        | suc n => f n r
      \\record S \\extends R | t : X | g : f 0 \\this t = t
      """);
  }

  @Test
  public void thisBadRecursiveArgument() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\func f (n : Nat) (r : R) : r.X -> r.X \\elim n
        | 0 => r.x
        | suc n => f n (\\let r' => r \\in r')
      \\record S \\extends R | t : X | g : f 0 \\this t = t
      """, 1);
    assertThat(getDefinition("f").getGoodThisParameters(), is(empty()));
  }

  @Test
  public void mutualRecursion() {
    typeCheckModule("""
      \\record R {
        \\func f (n : Nat) : Nat
          | 0 => 0
          | suc n => g n
        \\func g (n : Nat) : Nat
          | 0 => 0
          | suc n => f n
      }
      """);
  }

  @Test
  public void thisRecursiveDataArgument() {
    typeCheckModule("""
      \\record R (X : \\Type)
      \\data D (r : R)
        | con1 (D r)
        | con2
      \\record S \\extends R | g : D \\this
      """);
  }

  @Test
  public void thisClassExt() {
    typeCheckModule("""
      \\record R (X : \\Type)
      \\record D (r : R)
      \\record S \\extends R | g : D \\this
      """);
  }

  @Test
  public void thisEquality() {
    typeCheckModule("""
      \\record R
      \\record S \\extends R | g : \\this = {R} \\this
      """);
  }

  @Test
  public void thisError() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\func f (r : R) => r.x
      \\record S \\extends R | t : X | g : f (\\let y => \\this \\in y) t = t
      """, 1);
  }

  @Test
  public void thisErrorInferred() {
    typeCheckModule("""
      \\record R (X : \\Type0) (f : Nat -> Nat)
      \\record S \\extends R | g : (idp : \\this = {R} \\this) = idp
      """, 1);
  }

  @Test
  public void thisBadArgument() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\func f (r : R) => R.x {\\let r' => r \\in r'}
      \\record S \\extends R | t : X | g : f \\this t = t
      """, 1);
  }

  @Test
  public void thisBadField() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\record F (r : R) | f (t : r.X) : R.x {\\let r' => r \\in r'} t = t
      \\record S \\extends R | t : X | g : F \\this
      """, 1);
  }

  @Test
  public void thisBadFieldSubclass() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\record F (r : R)
      \\record G \\extends F | f (t : r.X) : R.x {\\let r' => r \\in r'} t = t
      \\record S \\extends R | t : X | g : G \\this
      """, 1);
  }

  @Test
  public void thisBadFieldSuperclass() {
    typeCheckModule("""
      \\record R (X : \\Type) (x : X -> X)
      \\record F (r : R) | f (t : r.X) : R.x {\\let r' => r \\in r'} t = t
      \\record G \\extends F
      \\record S \\extends R | t : X | g : G \\this
      """, 1);
  }

  @Test
  public void superClassExt() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\record S (y : Nat) \\extends R
      \\record T \\extends R
        | field : S { | R => \\this }
      """);
  }
}
