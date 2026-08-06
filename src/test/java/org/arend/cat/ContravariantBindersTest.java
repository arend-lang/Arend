package org.arend.cat;

import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.LamExpression;
import org.arend.ext.core.context.BindingVariance;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class ContravariantBindersTest extends TypeCheckingTestCase {
  @Test
  public void paramTest() {
    typeCheckDef("\\func f (x :- Nat) : Nat => 0");
  }

  @Test
  public void bareReferenceError() {
    typeCheckDef("\\func f (x :- Nat) : Nat => x", 1);
  }

  @Test
  public void applyDataTest() {
    typeCheckModule("""
      \\data D (n :- Nat)
      \\func f (n :- Nat) : \\Type => D n
      """);
  }

  @Test
  public void applyDataTest2() {
    typeCheckModule("""
      \\data D (n :- Nat)
      \\func f (n : Nat) : \\Type => D n
      """);
  }

  @Test
  public void applyDataError() {
    typeCheckModule("""
      \\data D (n : Nat)
      \\func f (n :- Nat) : \\Type => D n
      """, 1);
  }

  @Test
  public void applyDataError1() {
    typeCheckModule("""
      \\data D (n :+ Nat)
      \\func f (n :- Nat) : \\Type => D n
      """, 1);
  }

  @Test
  public void applyDataError2() {
    typeCheckModule("""
      \\data D (n :- Nat)
      \\func f (n :+ Nat) : \\Type => D n
      """, 1);
  }

  @Test
  public void applyConTest() {
    typeCheckModule("""
      \\data D | con (n :- Nat)
      \\func f (n : Nat) : D => con n
      """);
  }

  @Test
  public void applyConTest2() {
    typeCheckModule("""
      \\data D | con (n : Nat)
      \\func f (n :- Nat) : D => con n
      """, 1);
  }

  @Test
  public void fieldTest() {
    typeCheckModule("""
      \\class C | f (n :- Nat) : Nat
      \\func test {c : C} (n : Nat) => c.f n
      """);
  }

  @Test
  public void fieldTest2() {
    typeCheckModule("""
      \\class C | f (n : Nat) : Nat
      \\func test {c : C} (n :- Nat) => c.f n
      """, 1);
  }

  @Test
  public void applyPiTest() {
    typeCheckDef("\\func test (a :- Nat) (f : \\Pi (y :- Nat) -> Nat) => f a");
  }

  @Test
  public void applyPiTest2() {
    typeCheckDef("\\func test (a : Nat) (f : \\Pi (y :- Nat) -> Nat) => f a");
  }

  @Test
  public void applyPiTest3() {
    typeCheckDef("\\func test (a :- Nat) (f : \\Pi (y : Nat) -> Nat) => f a", 1);
  }

  @Test
  public void singleSwapTest() {
    // T's own parameter is covariant, so applying T to `a` doesn't add a second swap: checking
    // f's type `T a` swaps once (f is contravariant), so `a` (contravariant) is referenceable there.
    typeCheckModule("""
      \\func T (n :+ Nat) : \\Type => Nat
      \\func test (a :- Nat) (f :- T a) => 0
      """);
  }

  @Test
  public void doubleSwapComposesBackError() {
    // T's own parameter is contravariant too, so applying T to `a` swaps a second time, landing
    // back at the unswapped state - `a` (contravariant) is not referenceable there, same as at top level.
    typeCheckModule("""
      \\func T (n :- Nat) : \\Type => Nat
      \\func test (a :- Nat) (f :- T a) => 0
      """, 1);
  }

  @Test
  public void typeDependsOnInvariantTest() {
    typeCheckModule("""
      \\func T (n : Nat) : \\Type => Nat
      \\func test (a : Nat) (f :- T a) => 0
      """);
  }

  @Test
  public void typeDependsOnCovariantError() {
    // Checking f's contravariant type `T a` swaps once, so the ambient covariant `a` becomes
    // unreferenceable there - a contravariant binding's type cannot depend on covariant bindings.
    typeCheckModule("""
      \\func T (n :+ Nat) : \\Type => Nat
      \\func test (a :+ Nat) (f :- T a) => 0
      """, 1);
  }

  @Test
  public void sigmaError() {
    typeCheckDef("\\func test => \\Sigma (x :- Nat) Nat", 1);
  }

  @Test
  public void metaError() {
    typeCheckModule("\\meta warm (x :- Nat) => x", 1);
  }

  @Test
  public void diMatchError() {
    typeCheckModule("""
      \\func f (i :- DI) : Nat
        | dleft => 0
        | dright => 0
      """, 2);
  }

  @Test
  public void lamTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\lam (x :- Nat) => 0");
    assertEquals(BindingVariance.CONTRAVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamTest2() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x :- Nat) -> Nat => \\lam (x :- Nat) => 0");
    assertEquals(BindingVariance.CONTRAVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamTest3() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x :- Nat) -> Nat => \\lam (x : Nat) => 0");
    assertEquals(BindingVariance.CONTRAVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamTest4() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test : \\Pi (x :- Nat) -> Nat => \\lam x => 0");
    assertEquals(BindingVariance.CONTRAVARIANT, ((LamExpression) Objects.requireNonNull(def.getBody())).getParameters().getVariance());
  }

  @Test
  public void lamError() {
    typeCheckModule("\\func test : \\Pi (x : Nat) -> Nat => \\lam (x :- Nat) => 0", 1);
  }

  @Test
  public void lamMismatchError() {
    typeCheckModule("\\func test : \\Pi (x :+ Nat) -> Nat => \\lam (x :- Nat) => 0", 1);
  }

  @Test
  public void covariantUnderContravariantTest() {
    typeCheckDef("\\func test (f : (Nat ->+ Nat) ->- Nat) => f (\\lam x => x)");
  }
}
