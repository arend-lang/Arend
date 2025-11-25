package org.arend.typechecking.levels;

import org.arend.core.context.param.TypedSingleDependentLink;
import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.ExpressionFactory;
import org.arend.core.expr.PiExpression;
import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class SortTest extends TypeCheckingTestCase {
  private void checkLevelParameters(String... defNames) {
    for (String defName : defNames) {
      assertEquals("Level check for '" + defName + "' failed", Collections.emptyList(), getDefinition(defName).getLevelParameters());
    }
  }

  private void checkDef(String text) {
    Definition definition = typeCheckDef(text);
    assertEquals(Collections.emptyList(), definition.getLevelParameters());
  }

  @Test
  public void sortTest() {
    typeCheckDef("\\func test => \\Sort", 1);
  }

  @Test
  public void idTest() {
    checkDef("\\func test {A : \\Sort} (a : A) => a");
  }

  @Test
  public void sigmaTest() {
    checkDef("\\func test {A : \\Sort} (B : A -> \\Sort) (p : \\Sigma (x : A) (B x)) => p");
  }

  @Test
  public void piTest() {
    checkDef("\\func test {A : \\Sort} (B : A -> \\Sort) (f : \\Pi (x : A) -> B x) => f");
  }

  @Test
  public void dataTest() {
    typeCheckModule("""
      \\data D (A : \\Sort) (a : A) | con (A -> A)
      \\func test1 => D Nat 7
      \\func test2 => D _ 7
      \\func test3 => D Nat
      \\func test4 (d : D _ 7) => d
      \\func test5 {B : \\Sort} {b : B} (d : D _ b) => d
      """);
    checkLevelParameters("D", "test1", "test2", "test3", "test4", "test5");
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test1")).getResultType());
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test2")).getResultType());
    assertEquals(new PiExpression(new Sort(new Level(1), new Level(1)), new TypedSingleDependentLink(true, null, ExpressionFactory.Nat()), new UniverseExpression(Sort.SET0)), ((FunctionDefinition) getDefinition("test3")).getResultType());
  }

  @Test
  public void partiallyAppliedTest() {
    typeCheckModule("""
      \\data D (A : \\Sort) (a : A)
      \\func test => D
      """, 1);
  }
}
