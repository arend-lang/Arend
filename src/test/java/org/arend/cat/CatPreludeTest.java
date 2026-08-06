package org.arend.cat;

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
  public void contravariantSourceTest() {
    typeCheckDef("\\func test {C : \\Cat} {b : C} (x :- C) => x ~> b");
  }

  @Test
  public void pathEtaTest() {
    typeCheckModule("""
      \\func test {C : \\Cat} {a b : C} (p : a ~> b) : dpath (\\lam i => p i) = p
        => idp
      """);
  }
}
