package org.arend.cat;

import org.arend.Matchers;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.LamExpression;
import org.arend.core.expr.SigmaExpression;
import org.arend.ext.core.context.BindingVariance;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class CovariantBindersTest extends TypeCheckingTestCase {
  @Test
  public void paramTest() {
    typeCheckDef("\\func f (x :+ Nat) : Nat => x");
  }

  @Test
  public void applyDataTest() {
    typeCheckModule("""
      \\data D (n :+ Nat)
      \\func f (n :+ Nat) : \\Type => D n
      """);
  }

  @Test
  public void applyDataTest2() {
    typeCheckModule("""
      \\data D (n :+ Nat)
      \\func f (n : Nat) : \\Type => D n
      """);
  }

  @Test
  public void applyDataTest3() {
    typeCheckModule("""
      \\data D (n : Nat)
      \\func f (n :+ Nat) : \\Type => D n
      """, 1);
  }

  @Test
  public void applyConTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (n :+ Nat) : D => con n
      """);
  }

  @Test
  public void applyConTest2() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (n : Nat) : D => con n
      """);
  }

  @Test
  public void applyConTest3() {
    typeCheckModule("""
      \\data D | con (n : Nat)
      \\func f (n :+ Nat) : D => con n
      """, 1);
  }

  @Test
  public void fieldTest() {
    typeCheckModule("""
      \\class C | f (n :+ Nat) : Nat
      \\func test {c : C} (n :+ Nat) => c.f n
      """);
  }

  @Test
  public void fieldTest2() {
    typeCheckModule("""
      \\class C | f (n :+ Nat) : Nat
      \\func test {c : C} (n : Nat) => c.f n
      """);
  }

  @Test
  public void fieldTest3() {
    typeCheckModule("""
      \\class C | f (n : Nat) : Nat
      \\func test {c : C} (n :+ Nat) => c.f n
      """, 1);
  }

  @Test
  public void applyPiTest() {
    typeCheckDef("\\func test (a :+ Nat) (f : \\Pi (y :+ Nat) -> Nat) => f a");
  }

  @Test
  public void applyPiTest2() {
    typeCheckDef("\\func test (a : Nat) (f : \\Pi (y :+ Nat) -> Nat) => f a");
  }

  @Test
  public void applyPiTest3() {
    typeCheckDef("\\func test (a :+ Nat) (f : \\Pi (y : Nat) -> Nat) => f a", 1);
  }

  @Test
  public void applyFunTest() {
    typeCheckModule("""
      \\func f (y :+ Nat) => y
      \\func test (a :+ Nat) => f a
      """);
  }

  @Test
  public void applyFunTest2() {
    typeCheckModule("""
      \\func f (y :+ Nat) => y
      \\func test (a : Nat) => f a
      """);
  }

  @Test
  public void applyFunTest3() {
    typeCheckModule("""
      \\func f (y : Nat) => y
      \\func test (a :+ Nat) => f a
      """, 1);
  }

  @Test
  public void paramTypeError() {
    typeCheckModule(
      "\\func T (n :+ Nat) : \\Type => Nat\n" +
      "\\func test (a :+ Nat) (f : T a) => 0", 1);
  }

  @Test
  public void paramTypeTest() {
    typeCheckModule(
      "\\func T (n :+ Nat) : \\Type => Nat\n" +
      "\\func test (a : Nat) (f : T a) => 0");
  }

  @Test
  public void doubleCovariantTest() {
    typeCheckModule(
      "\\func g (y :+ Nat) : \\Type => Nat\n" +
      "\\func test (a :+ Nat) (f :+ g a) => 0");
  }

  @Test
  public void doubleClearError() {
    typeCheckModule(
      "\\func g (y : Nat) : \\Type => Nat\n" +
      "\\func test (a :+ Nat) (f : g a) => 0", 1);
  }

  @Test
  public void covariantParamElim() {
    typeCheckDef("""
      \\func f (x :+ Nat) : Nat \\elim x
        | 0 => 0
        | suc n => n
      """);
  }

  @Test
  public void covariantParamElim2() {
    typeCheckDef("""
      \\func f (x :+ Nat) : Nat
        | 0 => 0
        | suc n => n
      """);
  }

  @Test
  public void subMatchTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d : D) : Nat
        | con 0 => 0
        | con (suc n) => n
      """);
  }

  @Test
  public void sigmaTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\Sigma (x :+ Nat) Nat");
    assertEquals(BindingVariance.COVARIANT, ((SigmaExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void sigmaLastFieldWarning() {
    typeCheckDef("\\func test => \\Sigma (x : Nat) (_ :+ Nat)", 1);
    assertThatErrorsAre(Matchers.warning());
  }

  @Test
  public void sigmaLastGroupMultiNameNoWarning() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\Sigma (x y :+ Nat)");
    SigmaExpression sigma = (SigmaExpression) Objects.requireNonNull(def.getBody());
    assertEquals(BindingVariance.COVARIANT, sigma.getParameters().getVariance());
    assertEquals(BindingVariance.COVARIANT, sigma.getParameters().getNext().getVariance());
  }

  @Test
  public void sigmaTypeCovariantTest() {
    typeCheckModule("""
      \\func g (y :+ Nat) : \\Type => Nat
      \\func test (a :+ Nat) => \\Sigma (f :+ g a) Nat
      """);
  }

  @Test
  public void sigmaTypeClearError() {
    typeCheckModule("""
      \\func g (y : Nat) : \\Type => Nat
      \\func test (a :+ Nat) => \\Sigma (f : g a) Nat
      """, 1);
  }

  @Test
  public void tupleFieldCovariantTest() {
    typeCheckDef("\\func test (a :+ Nat) : \\Sigma (x :+ Nat) Nat Nat => (a, 0, 0)");
  }

  @Test
  public void tupleFieldClearError() {
    typeCheckDef("\\func test (a :+ Nat) : \\Sigma (x : Nat) Nat Nat => (a, 0, 0)", 1);
  }

  @Test
  public void tupleLastFieldFullContextTest() {
    typeCheckDef("\\func test (a :+ Nat) : \\Sigma Nat Nat Nat => (0, 0, a)");
  }

  @Test
  public void letTest() {
    typeCheckDef("\\func test => \\let | f (x :+ Nat) => x \\in f 0");
  }

  @Test
  public void letTest2() {
    typeCheckDef("\\func test (y : Nat) => \\let | f (x :+ Nat) => x \\in f y");
  }

  @Test
  public void letTest3() {
    typeCheckDef("\\func test (y :+ Nat) => \\let | f (x : Nat) => x \\in f y", 1);
  }

  @Test
  public void metaError() {
    typeCheckModule("\\meta warm (x :+ Nat) => x", 1);
  }

  @Test
  public void obError() {
    typeCheckModule("""
      \\data Ob (X :+ \\Cat)
        | ob X
      """, 1);
  }

  @Test
  public void obTest() {
    typeCheckModule("""
      \\data Ob (X : \\Cat)
        | ob X
      \\func test {X : \\Cat} (x :+ Ob X) : X
        | ob x => x
      """);
  }

  @Test
  public void obTest2() {
    typeCheckModule("""
      \\data Ob (X : \\Cat)
        | ob X
      \\func test {X : \\Cat} (x :+ Ob X) (f : X -> Nat) : Nat \\elim x
        | ob x => f x
      """);
  }

  @Test
  public void lamTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\lam (x :+ Nat) => x");
    assertEquals(BindingVariance.COVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamTest2() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x :+ Nat) -> Nat => \\lam (x :+ Nat) => x");
    assertEquals(BindingVariance.COVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamTest3() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x :+ Nat) -> Nat => \\lam (x : Nat) => x");
    assertEquals(BindingVariance.COVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamTest4() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x :+ Nat) -> Nat => \\lam x => x");
    assertEquals(BindingVariance.COVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamError() {
    typeCheckModule("\\func test : \\Pi (x : Nat) -> Nat => \\lam (x :+ Nat) => x", 1);
  }

  @Test
  public void piComparisonTest() {
    typeCheckDef("\\func test : (\\Pi (x :+ Nat) -> Nat) = (Nat -> Nat) => idp", 1);
  }

  @Test
  public void sigmaComparisonTest() {
    typeCheckDef("\\func test : (\\Sigma (x :+ Nat) Nat) = (\\Sigma Nat Nat) => idp", 1);
  }

  @Test
  public void inferenceTest() {
    typeCheckModule("""
      \\func foo {x : Nat} (p :+ x = x) => 0
      \\func test (x :+ Nat) (p :+ x = x) => foo p
      """, 1);
  }
}
