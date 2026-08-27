package org.arend.typechecking.definition;

import org.arend.core.definition.DConstructor;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.arend.Matchers.*;
import static org.junit.Assert.assertNotNull;

public class ConstructorTest extends TypeCheckingTestCase {
  @Test
  public void constructorTest() {
    typeCheckModule("""
      \\data D1 | con1 | con1' Nat
      \\data D2 | con2 D1 | con2'
      \\cons con (n : Nat) => con2 (con1' (suc n))
      """);
  }

  @Test
  public void functionError() {
    typeCheckModule("""
      \\data D1 | con1 | con1' Nat
      \\data D2 | con2 D1 | con2'
      \\cons con (n : Nat) => con2 (con1' (n Nat.+ 1))
      """, 1);
  }

  @Test
  public void lambdaTest() {
    typeCheckModule("""
      \\data D2 | con2 (Nat -> Nat)
      \\cons con => con2 (\\lam _ => 0)
      """, 1);
  }

  @Test
  public void lambdaError() {
    typeCheckModule("""
      \\data D2 | con2 (Nat -> Nat)
      \\cons con => con2 (\\lam n => n)
      """, 1);
  }

  @Test
  public void doubleVariableTest() {
    typeCheckModule("""
      \\data D1 | con1 | con1' Nat
      \\data D2 | con2 D1 Nat | con2'
      \\cons con (n m : Nat) => con2 (con1' n) m
      """);
  }

  @Test
  public void doubleVariableError() {
    typeCheckModule("""
      \\data D1 | con1 | con1' Nat
      \\data D2 | con2 D1 Nat | con2'
      \\cons con (n : Nat) => con2 (con1' n) n
      """, 1);
  }

  @Test
  public void variableTest() {
    typeCheckDef("\\cons con (n : Nat) => n");
  }

  @Test
  public void variableTest2() {
    typeCheckDef("\\cons con {A : \\Type} (a : A) => a");
  }

  @Test
  public void parametersTest() {
    typeCheckModule("""
      \\data List (A : \\Type) | cons A (List A) | nil
      \\cons single {A : \\Type} (a : A) => cons a nil
      """);
  }

  @Test
  public void parametersError() {
    typeCheckModule("""
      \\data List (A : \\Type) | cons A (List A) | nil
      \\cons single (A : \\Type) (a : A) => cons a nil
      """, 1);
  }

  @Test
  public void parametersError2() {
    typeCheckDef("\\cons con {x : Nat} (n : Nat) => suc n", 1);
  }

  @Test
  public void elimError() {
    typeCheckDef("""
      \\cons con (n : Nat) : Nat \\elim n
        | 0 => 0
        | suc n => suc n
      """, 1);
  }

  @Test
  public void patternsTest() {
    typeCheckModule("""
      \\data List (A : \\Type) | cons A (List A) | nil
      \\cons single {A : \\Type} (a : A) => cons a nil
      \\func f {A : \\Type} (xs : List A) : Nat
        | nil => 3
        | single x => 2
        | cons _ (cons _ _) => 1
      \\func test1 : f (single 5) = 2 => idp
      \\func test2 : f (cons 4 nil) = 2 => idp
      """);
  }

  @Test
  public void patternsElimTest() {
    typeCheckModule("""
      \\data List (A : \\Type) | cons A (List A) | nil
      \\cons single {A : \\Type} (a : A) => cons a nil
      \\func f {A : \\Type} (xs : List A) : Nat \\elim xs
        | nil => 3
        | single x => 2
        | cons _ (cons _ _) => 1
      \\func test1 : f (single 5) = 2 => idp
      \\func test2 : f (cons 4 nil) = 2 => idp
      """);
  }

  @Test
  public void patternsCaseTest() {
    typeCheckModule("""
      \\data List (A : \\Type) | cons A (List A) | nil
      \\cons single {A : \\Type} (a : A) => cons a nil
      \\func f {A : \\Type} (xs : List A) : Nat => \\case xs \\with {
        | nil => 3
        | single x => 2
        | cons _ (cons _ _) => 1
      }
      \\func test1 : f (single 5) = 2 => idp
      \\func test2 : f (cons 4 nil) = 2 => idp
      """);
  }

  @Test
  public void patternsCoverageError() {
    typeCheckModule("""
      \\data List (A : \\Type) | cons A (List A) | nil
      \\cons single {A : \\Type} (a : A) => cons a nil
      \\func f {A : \\Type} (xs : List A) : Nat
        | single x => 2
        | cons _ (cons _ _) => 1
      """, 1);
    assertThatErrorsAre(missingClauses(1));
  }

  @Test
  public void patternsCaseCoverageError() {
    typeCheckModule("""
      \\data List (A : \\Type) | cons A (List A) | nil
      \\cons single {A : \\Type} (a : A) => cons a nil
      \\func f {A : \\Type} (xs : List A) : Nat => \\case xs \\with {
        | single x => 2
        | cons _ (cons _ _) => 1
      }
      """, 1);
    assertThatErrorsAre(missingClauses(1));
  }

  @Test
  public void patternsParametersTest() {
    typeCheckModule("""
      \\data D2 (n : Nat) | con2 (n = 0)
      \\cons single (p : 0 = 0) => con2 p
      \\func f (d : D2 0) : Nat
        | single _ => 3
      \\func test1 : f (single idp) = 3 => idp
      \\func test2 : f (con2 idp) = 3 => idp
      """);
  }

  @Test
  public void patternsParametersCaseTest() {
    typeCheckModule("""
      \\data D2 (n : Nat) | con2 (n = 0)
      \\cons single (p : 0 = 0) => con2 p
      \\func f (d : D2 0) : Nat => \\case d \\with {
        | single _ => 3
      }
      \\func test1 : f (single idp) = 3 => idp
      \\func test2 : f (con2 idp) = 3 => idp
      """);
  }

  @Test
  public void patternsParametersMismatchError() {
    typeCheckModule("""
      \\data D2 (n : Nat) | con2 (n = 0)
      \\cons single (p : 0 = 0) => con2 p
      \\func f (d : D2 1) : Nat
        | single _ => 0
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Test
  public void patternsParametersMismatchCaseError() {
    typeCheckModule("""
      \\data D2 (n : Nat) | con2 (n = 0)
      \\cons single (p : 0 = 0) => con2 p
      \\func f (d : D2 1) : Nat => \\case d \\with {
        | single _ => 0
      }
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Test
  public void tupleTest() {
    typeCheckModule("""
      \\data D1 | con1 Nat
      \\data D2 (d1 : D1) | con2 (d1' : D1) (p : d1 = d1')
      \\cons con (n : Nat) (d : D1) (p : con1 (suc n) = d) : \\Sigma (x : D1) (D2 x) => (con1 (suc n), con2 d p)
      \\func f (q : \\Sigma (x : D1) (D2 x)) : Nat
        | con n _ _ => n
        | _ => 0
      """);
  }

  @Test
  public void cowithTest() {
    typeCheckModule("""
      \\record Pair (A B : \\Type)
        | proj1 : A
        | proj2 : B
      \\cons pair {A B : \\Type} (a : A) (b : B) : Pair A B
        | proj1 => a
        | proj2 => b
      \\data D1 | con1 (Pair Nat Nat)
      \\data D2 | con2 D1 Nat
      \\cons con (n m k : Nat) => con2 (con1 (pair (suc n) m)) (suc k)
      \\func f (d : D2) : Nat
        | con n m k => n Nat.+ m Nat.+ k
        | _ => 0
      \\func test : f (con2 (con1 (\\new Pair Nat Nat 7 12)) 3) = 20 => idp
      """);
  }

  @Test
  public void newTest() {
    typeCheckModule("""
      \\record Pair (A B : \\Type)
        | proj1 : A
        | proj2 : B
      \\cons pair {A B : \\Type} (a : A) (b : B) : Pair A B
        => \\new Pair {
          | proj1 => a
          | proj2 => b
        }
      \\data D1 | con1 (Pair Nat Nat)
      \\data D2 | con2 D1 Nat
      \\cons con (n m k : Nat) => con2 (con1 (pair (suc n) m)) (suc k)
      \\func f (d : D2) : Nat
        | con n m k => n Nat.+ m Nat.+ k
        | _ => 0
      \\func test : f (con2 (con1 (\\new Pair Nat Nat 7 12)) 3) = 20 => idp
      """);
  }

  @Test
  public void numberTest() {
    typeCheckModule("""
      \\cons one => 1
      \\func f (x : Nat) : Nat
        | 0 => 10
        | one => 20
        | suc (suc x) => x
      """);
  }

  @Test
  public void numberError() {
    typeCheckDef("\\cons one => 200", 1);
  }

  @Test
  public void goalTest() {
    typeCheckDef("\\cons test => suc {?}", 1);
    assertThatErrorsAre(goalError());
  }

  @Test
  public void dependentTypeTest() {
    typeCheckModule("""
      \\data D (n : Nat) | con {m : Nat} (m = n)
      \\cons con2 (x : Nat) : D x => con idp
      \\func f (d : D 0) : Nat | con2 y => y
      \\func test : f (con idp) = 0 => idp
      """);
  }

  @Test
  public void recordTest() {
    typeCheckModule("""
      \\record R (n m : Nat)
      \\cons con (n : Nat) : R => \\new R n 0
      """);
    assertNotNull(((DConstructor) getDefinition("con")).getPattern());
  }

  @Test
  public void typeTest() {
    typeCheckModule("""
      \\type D => \\Sigma Nat Nat
      \\cons con (n : Nat) : D => (n,0)
      \\func foo (d : D) : Nat
        | con k => suc k
        | (_, suc _) => 0
      \\func test : foo (7,0) = 8 => idp
      """);
  }

  @Test
  public void typeConstructorTest() {
    typeCheckModule("""
      \\type D => Nat
      \\cons con (n : Nat) : D => suc n
      \\func foo (d : D) : Nat
        | con k => k
        | 0 => 100
      \\func test : foo 1 = 0 => idp
      """);
    assertNotNull(((DConstructor) getDefinition("con")).getPattern());
  }

  @Test
  public void typeError() {
    typeCheckModule("""
      \\type D => \\Sigma Nat Nat
      \\func f (n : Nat) : Nat => n
      \\cons con (n : Nat) : D => (f n, 0)
      """, 1);
  }
}
