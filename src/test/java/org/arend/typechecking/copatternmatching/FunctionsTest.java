package org.arend.typechecking.copatternmatching;

import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.ClassCallExpression;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FunctionsTest extends TypeCheckingTestCase {
  @Test
  public void checkCowith() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\func f : R \\cowith | x => 0 | y => 0
      """);
  }

  @Test
  public void incompleteCowith() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\func f : R \\cowith | x => 0
      """, 1);
  }

  @Test
  public void withoutType() {
    resolveNamesModule("""
      \\record R (x y : Nat)
      \\func f \\cowith | x => 0 | y => 0
      """, -1);
  }

  @Test
  public void withIncorrectType() {
    resolveNamesDef("""
      \\data D
      \\func f : D \\cowith
      """);
  }

  @Test
  public void withoutTypeIdpPattern() {
    resolveNamesModule("""
      \\data D | con Nat
      \\func f \\cowith
        | g => \\lam (con n, idp) => n
      """, -1);
    typeCheckModule(-1);
  }

  @Test
  public void recursive() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\func f : R \\cowith | x => 0 | y => f.x
      """, 1);
  }

  @Test
  public void recursiveWithoutType() {
    resolveNamesModule("""
      \\record R (x y : Nat)
      \\func f \\cowith | x => 0 | y => f.x
      """, -1);
  }

  @Test
  public void mutuallyRecursive() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\func f : R \\cowith | x => 0 | y => g.x
      \\func g : R \\cowith | x => 0 | y => f.x
      """, 2);
  }

  @Test
  public void tooManyCopatterns() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\func f : R \\cowith | x => 0 | y => 0 | x => 0
      """, 1);
  }

  @Test
  public void wrongCopattern() {
    resolveNamesModule("""
      \\record R (x y : Nat)
      \\record R' (x' y' : Nat)
      \\func f : R \\cowith | x => 0 | y => 0 | x' => 0
      """, 1);
  }

  @Test
  public void hiddenFieldTest() {
    typeCheckModule("""
      \\record R (x : Nat) (p : x = x)
      \\func f : R \\cowith | x => 0 | p => idp
      """);
    ClassCallExpression classCall = (ClassCallExpression) ((FunctionDefinition) getDefinition("f")).getResultType();
    assertTrue(classCall.isImplemented((CoreClassField) getDefinition("R.x")));
    assertFalse(classCall.isImplemented((CoreClassField) getDefinition("R.p")));
  }
}
