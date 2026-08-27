package org.arend.cat;

import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.Expression;
import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatSigmaTest extends TypeCheckingTestCase {
  private void assertSort(Sort expected, String code) {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef(code);
    assertEquals(expected, ((UniverseExpression) def.getResultType()).getSortExpression().withInfLevel());
    Expression body = (Expression) def.getBody();
    Assert.assertNotNull(body);
    assertEquals(expected, ((UniverseExpression) body.getType()).getSortExpression().withInfLevel());
  }

  @Test
  public void sigmaSortTest() {
    assertSort(new Sort(Level.INFINITY, ConstLevel.INFINITY), "\\func test (X : \\Cat) => \\Sigma (x : X) Nat");
  }

  @Test
  public void sigmaSortTest2() {
    assertSort(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), "\\func test (X : \\Cat) => \\Sigma (x :+ X) Nat");
  }

  @Test
  public void sigmaSortTest3() {
    assertSort(new Sort(Level.INFINITY, ConstLevel.INFINITY), "\\func test (X : \\Cat) => \\Sigma (n :+ Nat) (x : X)");
  }

  @Test
  public void sigmaSortTest4() {
    assertSort(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), "\\func test (X : \\Cat) => \\Sigma (x :+ X) (n : Nat)");
  }

  @Test
  public void sigmaFiniteExpectedTypeTest() {
    typeCheckDef("\\func test (C : \\Cat0) : \\Type1 => \\Sigma (x : C) Nat");
  }
}
