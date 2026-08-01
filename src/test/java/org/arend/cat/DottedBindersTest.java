package org.arend.cat;

import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.LamExpression;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertTrue;

public class DottedBindersTest extends TypeCheckingTestCase {
  @Test
  public void paramTest() {
    typeCheckDef("\\func f (x .: Nat) : Nat => x");
  }

  @Test
  public void applyDataTest() {
    typeCheckModule(
      "\\data D (n .: Nat)\n" +
      "\\func f (n .: Nat) : \\Type => D n");
  }

  @Test
  public void applyConTest() {
    typeCheckModule(
      "\\data D | con (n .: Nat)\n" +
      "\\func f (n .: Nat) : D => con n");
  }

  @Test
  public void fieldTest() {
    typeCheckModule("\\class C | f (n .: Nat) : Nat");
  }

  @Test
  public void applyPiError() {
    typeCheckDef("\\func test (a : Nat) (f : \\Pi (y .: Nat) -> Nat) => f a", 1);
  }

  @Test
  public void applyPiTest() {
    typeCheckDef("\\func test (a .: Nat) (f : \\Pi (y .: Nat) -> Nat) => f a");
  }

  @Test
  public void applyFunError() {
    typeCheckModule(
      "\\func f (y .: Nat) => y\n" +
      "\\func test (a : Nat) => f a", 1);
  }

  @Test
  public void paramTypeError() {
    typeCheckModule(
      "\\func T (n : Nat) : \\Type => Nat\n" +
      "\\func test (a : Nat) (f .: T a) => 0", 1);
  }

  @Test
  public void paramTypeTest() {
    typeCheckModule(
      "\\func T (n : Nat) : \\Type => Nat\n" +
      "\\func test (a .: Nat) (f .: T a) => 0");
  }

  @Test
  public void doubleClearTest() {
    typeCheckModule(
      "\\func g (y .: Nat) : \\Type => Nat\n" +
      "\\func test (a .: Nat) (f .: g a) => 0");
  }

  @Test
  public void doubleClearError() {
    typeCheckModule(
      "\\func g (y .: Nat) : \\Type => Nat\n" +
      "\\func test (a : Nat) (f .: g a) => 0", 1);
  }

  @Test
  public void dotParamElim() {
    typeCheckDef("""
      \\func f (x .: Nat) : Nat \\elim x
        | 0 => 0
        | suc n => n
      """);
  }

  @Test
  public void dotParamElim2() {
    typeCheckDef("""
      \\func f (x .: Nat) : Nat
        | 0 => 0
        | suc n => n
      """);
  }

  @Test
  public void subMatchTest() {
    typeCheckModule("""
      \\data D | con (n .: Nat)
      \\func f (d : D) : Nat
        | con 0 => 0
        | con (suc n) => n
      """);
  }

  @Test
  public void dottedParameterCanBeTriviallyBound() {
    typeCheckDef("\\func f (x .: Nat) : Nat | x => x");
  }

  @Test
  public void sigmaTest() {
    typeCheckDef("\\func test => \\Sigma (x .: Nat) Nat", 1);
  }

  @Test
  public void letTest() {
    typeCheckDef("\\func test => \\let | f (x .: Nat) => x \\in f 0");
  }

  @Test
  public void letError() {
    typeCheckDef("\\func test (y : Nat) => \\let | f (x .: Nat) => x \\in f y", 1);
  }

  @Test
  public void metaError() {
    typeCheckModule("\\meta warm (x .: Nat) => x", 1);
  }

  @Test
  public void obError() {
    typeCheckModule("""
      \\data Ob (X : \\Cat)
        | ob (_ .: X)
      """, 1);
  }

  @Test
  public void obTest() {
    typeCheckModule("""
      \\data Ob (X .: \\Cat)
        | ob (_ .: X)
      \\func test {X .: \\Cat} (x : Ob X) : X
        | ob x => x
      """);
  }

  @Test
  public void obTest2() {
    typeCheckModule("""
      \\data Ob (X .: \\Cat)
        | ob (_ .: X)
      \\func test {X .: \\Cat} (x : Ob X) (f : \\Pi (_ .: X) -> Nat) : Nat \\elim x
        | ob x => f x
      """);
  }

  @Test
  public void lamTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\lam (x .: Nat) => x");
    assertTrue(((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().isDotted());
  }

  @Test
  public void lamTest2() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x .: Nat) -> Nat => \\lam (x .: Nat) => x");
    assertTrue(((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().isDotted());
  }

  @Test
  public void lamTest3() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x .: Nat) -> Nat => \\lam (x : Nat) => x");
    assertTrue(((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().isDotted());
  }

  @Test
  public void lamTest4() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x .: Nat) -> Nat => \\lam x => x");
    assertTrue(((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().isDotted());
  }

  @Test
  public void lamError() {
    typeCheckModule("\\func test : \\Pi (x : Nat) -> Nat => \\lam (x .: Nat) => x", 1);
  }
}
