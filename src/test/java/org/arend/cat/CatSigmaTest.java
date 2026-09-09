package org.arend.cat;

import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.Expression;
import org.arend.core.expr.SigmaExpression;
import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.context.BindingVariance;
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
    assertSort(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), "\\func test (X : \\Cat) => \\Sigma+ (x : X) Nat");
  }

  @Test
  public void sigmaSortTest3() {
    assertSort(new Sort(Level.INFINITY, ConstLevel.INFINITY), "\\func test (X : \\Cat) => \\Sigma (n : Nat) (x : X)");
  }

  @Test
  public void sigmaSortTest4() {
    assertSort(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), "\\func test (X : \\Cat) => \\Sigma+ (n : Nat) (x : X)");
  }

  @Test
  public void sigmaPlusInResultTypeTest() {
    typeCheckDef("\\func test : \\Sigma+ Nat Nat => (0, 0)");
  }

  @Test
  public void sigmaPlusInResultTypeVarianceTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test (X : \\Cat) (x :+ X) : \\Sigma+ (y : X) Nat => (x, 0)");
    assertEquals(BindingVariance.COVARIANT, ((SigmaExpression) def.getResultType()).getVariance());
  }

  @Test
  public void sigmaInResultTypeVarianceError() {
    typeCheckDef("\\func test (X : \\Cat) (x :+ X) : \\Sigma (y : X) Nat => (x, 0)", 1);
  }

  @Test
  public void sigmaFiniteExpectedTypeTest() {
    typeCheckDef("\\func test (C : \\Cat0) : \\Type1 => \\Sigma (x : C) Nat");
  }

  @Test
  public void sigmaPlusVarianceTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\Sigma+ (x : Nat) Nat");
    SigmaExpression sigma = (SigmaExpression) def.getBody();
    Assert.assertNotNull(sigma);
    assertEquals(BindingVariance.COVARIANT, sigma.getVariance());
    assertEquals(BindingVariance.COVARIANT, sigma.getParameters().getVariance());
    assertEquals(BindingVariance.COVARIANT, sigma.getParameters().getNext().getVariance());
  }

  @Test
  public void sigmaVarianceTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\Sigma (x : Nat) Nat");
    SigmaExpression sigma = (SigmaExpression) def.getBody();
    Assert.assertNotNull(sigma);
    assertEquals(BindingVariance.INVARIANT, sigma.getVariance());
    assertEquals(BindingVariance.INVARIANT, sigma.getParameters().getVariance());
  }

  @Test
  public void sigmaPlusGroupVarianceTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\Sigma+ (x y : Nat) Nat");
    SigmaExpression sigma = (SigmaExpression) def.getBody();
    Assert.assertNotNull(sigma);
    assertEquals(BindingVariance.COVARIANT, sigma.getParameters().getVariance());
    assertEquals(BindingVariance.COVARIANT, sigma.getParameters().getNext().getVariance());
  }

  @Test
  public void sigmaFieldVarianceError() {
    typeCheckDef("\\func test (X : \\Cat) => \\Sigma (x :+ X) Nat", 1);
  }

  @Test
  public void sigmaFieldVarianceError2() {
    typeCheckDef("\\func test (X : \\Cat) => \\Sigma+ (x :+ X) Nat", 1);
  }

  @Test
  public void sigmaFieldVarianceError3() {
    typeCheckDef("\\func test (X : \\Cat) => \\Sigma (x :+ X) (y :+ X)", 2);
  }

  @Test
  public void sigmaLastFieldVarianceError() {
    typeCheckDef("\\func test => \\Sigma (x : Nat) (_ :+ Nat)", 1);
  }

  @Test
  public void patternMatchingError() {
    typeCheckModule("""
      \\func test (x :+ \\Sigma Nat Nat) : Nat
        | (x, _) => x
      """, 1);
  }

  @Test
  public void patternMatchingTest() {
    typeCheckModule("""
      \\func test (x :+ \\Sigma+ Nat Nat) : Nat
        | (x, _) => x
      """);
  }

  @Test
  public void invariantPatternMatchingTest() {
    typeCheckModule("""
      \\func test (x : \\Sigma+ Nat Nat) : Nat
        | (x, _) => x
      """);
  }

  @Test
  public void invariantPatternMatchingTest2() {
    typeCheckModule("""
      \\func test (x : \\Sigma Nat Nat) : Nat
        | (x, _) => x
      """);
  }

  @Test
  public void caseCovariantSigmaError() {
    typeCheckModule("""
      \\func test (x :+ \\Sigma Nat Nat) : Nat => \\case \\elim x \\with {
        | (x, _) => x
      }
      """, 1);
  }

  @Test
  public void caseCovariantSigmaTest() {
    typeCheckModule("""
      \\func test (x :+ \\Sigma+ Nat Nat) : Nat => \\case \\elim x \\with {
        | (x, _) => x
      }
      """);
  }
}
