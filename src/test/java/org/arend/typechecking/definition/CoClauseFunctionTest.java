package org.arend.typechecking.definition;

import org.arend.ext.error.ArgumentExplicitnessError;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Ignore;
import org.junit.Test;

import static org.arend.Matchers.*;

public class CoClauseFunctionTest extends TypeCheckingTestCase {
  @Test
  public void functionTest() {
    typeCheckModule("""
      \\record C (a b : Nat) (f g : \\Pi (x : Nat) -> x = a -> x = b)
      \\func test : C 0 \\cowith
        | b => 0
        | f x (p : x = 0) : x = 0 \\with {
          | 0, _ => idp
          | suc n, p => p
        }
        | g (x : Nat) (p : x = 0) : x = 0 \\elim x {
          | 0 => idp
          | suc n => p
        }
      """);
  }

  @Test
  public void parameterDependencyError() {
    typeCheckModule("""
      \\record C (a : Nat) (f : \\Pi (x : Nat) -> x = a -> x = a)
      \\func test : C 0 \\cowith
        | f x p : x = 0 \\elim x {
          | 0 => p
          | suc n => p
        }
      """, 1);
  }

  @Test
  public void resultTypeDependencyError() {
    typeCheckModule("""
      \\record C (a : Nat) (f : \\Pi (x : Nat) -> x = a -> x = a)
      \\func test : C 0 \\cowith
        | f x (p : x = 0) \\elim x {
          | 0 => p
          | suc n => p
        }
      """, 1);
  }

  @Test
  public void functionTest2() {
    typeCheckModule("""
      \\record C (f g : Nat -> Nat)
      \\func test : C \\cowith
        | f (x : Nat) \\with {
          | 0 => 0
          | suc n => suc (g n Nat.+ test.g n)
        }
        | g x \\elim x {
          | 0 => 0
          | suc n => suc (f n Nat.+ test.f n)
        }
      """);
  }

  @Test
  public void longName() {
    typeCheckModule("""
      \\record C (f : Nat -> Nat)
      \\record D \\extends C
      \\func test : D \\cowith
        | C.f (x : Nat) \\with {
          | 0 => 0
          | suc n => suc (f n)
        }
      """);
  }

  @Test
  public void fieldResolveError() {
    resolveNamesModule("""
      \\record C (a : Nat) (f : Nat -> Nat)
      \\func test : C \\cowith
        | a => 0\
        | f x \\with {
          | 0 => a
          | suc n => f n
        }
      """, 1);
    assertThatErrorsAre(notInScope("a"));
  }

  @Test
  public void fieldTypecheckingError() {
    typeCheckModule("""
      \\record C (a : Nat) (f : Nat -> Nat)
      \\func test : C \\cowith
        | a => 0\
        | f x \\with {
          | 0 => C.a
          | suc n => f n
        }
      """, 1);
  }

  @Test
  public void parameterSubtypeError() {
    typeCheckModule("""
      \\record C (f : \\Pi (A : \\Prop) (x : Nat) -> A -> A)
      \\lemma test : C \\cowith
        | f (A : \\Set0) x a \\elim x {
          | 0 => a
          | suc n => a
        }
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Test
  public void parameterSubtypeError2() {
    typeCheckModule("""
      \\record C (f : \\Pi (A : \\Set0) (x : Nat) -> A -> A)
      \\func test : C \\cowith
        | f (A : \\Prop) x a \\elim x {
          | 0 => a
          | suc n => a
        }
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Test
  public void resultSubtypeTest() {
    typeCheckModule("""
      \\record C (f : \\Prop -> Nat -> \\Set0)
      \\func test : C \\cowith
        | f A x : \\Prop \\elim x {
          | 0 => A
          | suc n => A
        }
      """);
  }

  @Test
  public void resultSubtypeError() {
    typeCheckModule("""
      \\record C (f : \\Prop -> Nat -> \\Prop)
      \\func test : C \\cowith
        | f A x : \\Set0 \\elim x {
          | 0 => A
          | suc n => A
        }
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Ignore
  @Test
  public void recordTest() {
    typeCheckModule("""
      \\record R (f g : Nat -> Nat)
      \\record S \\extends R
        | f x \\with {
          | 0 => 0
          | suc n => f n
        }
      """);
  }

  @Ignore
  @Test
  public void recordError() {
    typeCheckModule("""
      \\record R (f g : Nat -> Nat)
      \\record S \\extends R
        | f x \\with {
          | 0 => 0
          | suc n => g n
        }
      """, 1);
  }

  @Ignore
  @Test
  public void recordTest2() {
    typeCheckModule("""
      \\record R (a : Nat) (f : \\Pi (x : Nat) -> x = a -> Nat)
      \\record S \\extends R
        | a => 0
        | f x _ \\elim x {
          | 0 => 0
          | suc n => n
        }
      """);
  }

  @Ignore
  @Test
  public void recordError2() {
    typeCheckModule("""
      \\record R (a : Nat) (f : \\Pi (x : Nat) -> x = a -> Nat)
      \\record S \\extends R
        | f x _ \\elim x {
          | 0 => 0
          | suc n => n
        }
      """, 1);
  }

  @Test
  public void termTest() {
    typeCheckModule("""
      \\class D | \\infix 3 func (x y : Nat) : Nat
      \\instance D-inst : D
        | func \\as \\infix 3 func (x y : Nat) : Nat => x
      """);
  }

  @Test
  public void termTest2() {
    typeCheckModule("""
      \\class D | \\infix 3 func (x y : Nat) : Nat
      \\instance D-inst : D
        | func \\as \\infix 3 func (x y : Nat) => x
      """);
  }

  @Test
  public void levelTest() {
    typeCheckModule("""
      \\data Wrap (A : \\Type) | in A
      \\record R | field {A : \\Type0} (p : \\Pi (a a' : A) -> a = a') (t s : Wrap A) : A \\level p
      \\func test : R \\cowith | field {A : \\Type0} (p : \\Pi (a a' : A) -> a = a') (t s : Wrap A) : A \\elim t { | in a => a }
      """);
  }

  @Test
  public void levelTest2() {
    typeCheckModule("""
      \\data Wrap (A : \\Type) | in A
      \\record R | field {A : \\Type0} (p : \\Pi (a a' : A) -> a = a') (t s : Wrap A) : A \\level p
      \\func test : R \\cowith | field {A} p t s \\elim t { | in a => a }
      """);
  }

  @Test
  public void implicitParameterError() {
    typeCheckModule(
      "\\record R | field {A : \\Type0} : A -> A\n" +
      "\\func test : R \\cowith | field \\as \\fix 5 field t => {?}", 1);
    assertThatErrorsAre(typecheckingError(ArgumentExplicitnessError.class));
  }

  @Test
  public void infixTest() {
    typeCheckModule("""
      \\record R | \\infixl 5 % : Nat -> Nat -> Nat
      \\func test : R \\cowith | % n m \\elim n {
        | 0 => m
        | suc n => suc (n % m)
      }
      """);
  }

  @Test
  public void parametersTest() {
    typeCheckModule("""
      \\record C (A : \\Type) (f : Nat -> A -> A)
      \\func g (B : \\Type) (b' : B) : C B \\cowith
        | f n (b : B) : B \\elim n {
          | 0 => b
          | suc n => b'
        }
      \\func test (X : \\Type) (x : X) : C.f {g X x} = g.f {X} {x} => idp
      """);
  }

  @Test
  public void explicitParametersTest() {
    typeCheckModule("""
      \\record C (A : \\Type) (f : Nat -> A -> A)
      \\func g (B : \\Type) : C B \\cowith
        | f n (b : B) : B \\elim n {
          | 0 => b
          | suc n => f {B} n b
        }
      """);
  }

  @Test
  public void withParametersTest() {
    typeCheckModule("""
      \\record C (A : \\Type) (f : Nat -> A -> A)
      \\func g (B : \\Type) : C B \\cowith
        | f n (b : B) : B \\with {
          | 0, b => b
          | suc n, b => f {B} n b
        }
      """);
  }

  @Test
  public void missingClausesTest() {
    typeCheckModule("""
      \\record C (f : Nat -> Nat)
      \\func g (m : Nat) : C \\cowith
        | f n \\with
      """, 1);
    assertThatErrorsAre(missingClauses(2));
  }

  @Test
  public void withImplicitParametersTest() {
    typeCheckModule("""
      \\record C (A : \\Type) (f : \\Pi {n : Nat} -> A -> A)
      \\func g (B : \\Type) : C B \\cowith
        | f {n} (b : B) : B \\with {
          | {0}, b => b
          | {suc n}, b => f {B} {n} b
        }
      """);
  }

  @Test
  public void withImplicitParametersTest2() {
    typeCheckModule("""
      \\record C (A : \\Type) (f : \\Pi {m : Nat} -> Nat -> A -> A)
      \\func g (B : \\Type) : C B \\cowith
        | f {m} n (b : B) : B \\with {
          | 0, b => b
          | suc n, b => f {B} {0} n b
        }
      """);
  }

  @Test
  public void dynamicTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\record S (r : R)
      \\record T {
        \\func foo : S \\cowith
          | r : R \\cowith {
            | x => 0
          }
      }
      """);
  }

  @Test
  public void parametersSubstTest() {
    typeCheckModule("""
      \\record C (f : Nat -> Nat) (g : \\Pi (x : Nat) -> f x = 0)
      \\func test (n : Nat) : C \\cowith
        | f (m : Nat) : Nat \\with {
          | 0 => 0
          | suc m => 0
        }
        | g (m : Nat) : f m = 0 \\with {
          | 0 => idp
          | suc m => idp
        }
      """);
  }
}
