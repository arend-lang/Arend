package org.arend.cat;

import org.arend.core.definition.ClassDefinition;
import org.arend.core.definition.ClassField;
import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.SmallIntegerExpression;
import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.context.BindingVariance;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CatRecordsTest extends TypeCheckingTestCase {
  @Test
  public void covariantFieldParses() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C | f :+ Nat");
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().getFirst().getVariance());
  }

  @Test
  public void invariantFieldIsDefault() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C | f : Nat");
    assertEquals(BindingVariance.INVARIANT, def.getPersonalFields().getFirst().getVariance());
  }

  @Test
  public void covariantFieldTeleParses() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (x :+ Nat)");
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().getFirst().getVariance());
  }

  @Test
  public void covariantFieldTeleGroupParses() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (x y :+ Nat)");
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().get(0).getVariance());
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().get(1).getVariance());
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
  public void covariantFieldCowithOk() {
    typeCheckModule("""
      \\class C (f :+ Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => a
      """);
  }

  @Test
  public void invariantFieldNewError() {
    typeCheckModule("""
      \\class C (f : Nat)
      \\func test (a :+ Nat) => \\new C { | f => a }
      """, 1);
  }

  @Test
  public void covariantFieldNewOk() {
    typeCheckModule("""
      \\class C (f :+ Nat)
      \\func test (a :+ Nat) => \\new C { | f => a }
      """);
  }

  @Test
  public void mixedVarianceCovariantFieldOk() {
    typeCheckModule("""
      \\class C (f : Nat) (g :+ Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => 0
        | g => a
      """);
  }

  @Test
  public void mixedVarianceInvariantFieldError() {
    typeCheckModule("""
      \\class C (f : Nat) (g :+ Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => a
        | g => 0
      """, 1);
  }

  @Test
  public void thisCovariantFieldAccessErrorViaInvariantField() {
    typeCheckModule("""
      \\class B | g :+ Nat
      \\class C \\extends B | f : Nat
      \\func h (n : Nat) => n
      \\func test (a :+ Nat) : C \\cowith
        | g => a
        | f => h \\this.g
      """, 1);
  }

  @Test
  public void thisCovariantFieldAccessErrorViaInvariantParam() {
    typeCheckModule("""
      \\class B | g :+ Nat
      \\class C \\extends B | f :+ Nat
      \\func h (n : Nat) => n
      \\func test (a :+ Nat) : C \\cowith
        | g => a
        | f => h \\this.g
      """, 1);
  }

  @Test
  public void thisCovariantFieldAccessOkUnderCovariantParam() {
    typeCheckModule("""
      \\class B | g :+ Nat
      \\class C \\extends B | f :+ Nat
      \\func h (n :+ Nat) => n
      \\func test (a :+ Nat) : C \\cowith
        | g => a
        | f => h \\this.g
      """);
  }

  @Test
  public void otherInstanceCovariantFieldAccessOk() {
    typeCheckModule("""
      \\class B (g :+ Nat)
      \\class C \\extends B
      \\func h (n : Nat) => n
      \\func test (c : C) => h c.g
      """);
  }

  @Test
  public void fieldDependencTest() {
    typeCheckModule("""
      \\func foo (n : Nat) => n
      \\record R (a :+ Nat) (p : foo a = 0)
      """, 1);
  }

  @Test
  public void classSortTest() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (X : \\Cat) (x : X)");
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void classSortTest2() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (X : \\Cat) (x :+ X)");
    assertEquals(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void classSortTest3() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (X : \\Cat) (x : X) (n :+ Nat)");
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void classSortTest4() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (X : \\Cat) (x :+ X) (n : Nat)");
    assertEquals(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), def.getSortExpression().withInfLevel());
  }

  private void assertClassCallSort(Sort expected, String classCode) {
    ClassDefinition def = (ClassDefinition) typeCheckDef(classCode);
    ClassField nField = def.getPersonalFields().stream().filter(f -> f.getName().equals("n")).findFirst().orElseThrow();
    ClassCallExpression classCall = new ClassCallExpression(def, def.makeIdLevels(), Collections.singletonMap(nField, new SmallIntegerExpression(0)));
    assertEquals(expected, def.getSort());
    assertEquals(expected, ((UniverseExpression) classCall.getType()).getSortExpression().withInfLevel());
  }

  @Test
  public void classCallSortTest() {
    assertClassCallSort(new Sort(Level.INFINITY, ConstLevel.INFINITY), "\\class C (X : \\Cat) (x : X) (n : Nat)");
  }

  @Test
  public void classCallSortTest2() {
    assertClassCallSort(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), "\\class C (X : \\Cat) (x :+ X) (n : Nat)");
  }
}
