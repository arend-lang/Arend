package org.arend.cat;

import org.arend.core.definition.ClassDefinition;
import org.arend.core.definition.ClassField;
import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.SmallIntegerExpression;
import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CatRecordsTest extends TypeCheckingTestCase {
  @Test
  public void fieldParameterVarianceTest() {
    typeCheckModule("""
      \\class C | f (n :+ Nat) : Nat
      \\func test (c : C) (n :+ Nat) => c.f n
      """);
  }

  @Test
  public void invariantFieldCowithError() {
    typeCheckModule("""
      \\class C (f : Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => a
      """, 1);
  }

  @Test
  public void invariantFieldNewError() {
    typeCheckModule("""
      \\class C (f : Nat)
      \\func test (a :+ Nat) => \\new C { | f => a }
      """, 1);
  }

  @Test
  public void otherInstanceFieldAccessTest() {
    typeCheckModule("""
      \\class B (g : Nat)
      \\class C \\extends B
      \\func h (n : Nat) => n
      \\func test (c : C) => h c.g
      """);
  }

  @Test
  public void classSortTest() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (X : \\Cat) (x : X)");
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void classSortTest2() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (X : \\Cat) (x : X) (n : Nat)");
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void classCallSortTest() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (X : \\Cat) (x : X) (n : Nat)");
    ClassField nField = def.getPersonalFields().stream().filter(f -> f.getName().equals("n")).findFirst().orElseThrow();
    ClassCallExpression classCall = new ClassCallExpression(def, def.makeIdLevels(), Collections.singletonMap(nField, new SmallIntegerExpression(0)));
    Sort expected = new Sort(Level.INFINITY, ConstLevel.INFINITY);
    assertEquals(expected, def.getSort());
    assertEquals(expected, ((UniverseExpression) classCall.getType()).getSortExpression().withInfLevel());
  }

  @Test
  public void patternMatchingError() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\func test (x :+ R) : Nat
        | (x, _) => x
      """, 1);
  }

  @Test
  public void patternMatchingTest() {
    typeCheckModule("""
      \\record R (x y : Nat)
      \\func test (r : R) : Nat
        | (x, _) => x
      """);
  }
}
