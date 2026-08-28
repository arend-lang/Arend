package org.arend.cat;

import org.arend.core.definition.DataDefinition;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatDataTest extends TypeCheckingTestCase {
  @Test
  public void dataSortTest() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X : \\Cat)
        | con X
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void dataSortTest2() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X : \\Cat)
        | con (_ :+ X)
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void dataSortTest3() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X : \\Cat)
        | con X (n :+ Nat)
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void dataSortTest4() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X : \\Cat)
        | con (_ :+ X) (n : Nat)
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY), def.getSortExpression().withInfLevel());
  }

  @Test
  public void catContextTest() {
    typeCheckDef("\\data D (x :+ Nat)");
  }

  @Test
  public void catContextTest2() {
    typeCheckDef("\\data D (x :+ Nat) | con1 | con2");
  }

  @Test
  public void condCatContextTest() {
    typeCheckDef("""
      \\data D (x :+ Nat)
        | con1
        | con2 Nat {
          | 0 => con1
        }
      """, 1);
  }

  @Test
  public void hitCatContextTest() {
    typeCheckDef("""
      \\data D (x :+ Nat)
        | con
        | con2 I {
          | left => con
          | right => con
        }
      """, 1);
  }

  @Test
  public void dHitCatContextTest() {
    typeCheckDef("""
      \\data D (x :+ Nat)
        | con
        | con2 DI {
          | dleft => con
          | dright => con
        }
      """, 1);
  }

  @Test
  public void truncatedCatContextTest() {
    typeCheckDef("""
      \\truncated \\data D (x :+ Nat) : \\Prop
        | con1
        | con2
      """, 1);
  }

  @Test
  public void truncatedCatUniverseTest() {
    typeCheckDef("""
      \\truncated \\data D (C : \\Cat) : \\Prop
        | con C
      """);
  }

  @Test
  public void truncatedCatUniverseError() {
    typeCheckDef("""
      \\truncated \\data D (C : \\Cat) : \\Prop
        | con (x :+ C)
      """, 1);
  }

  @Test
  public void useLevelCatContextTest() {
    typeCheckModule("""
      \\data ToProp (A : \\Type) (p :+ \\Pi (x y : A) -> x = y)
        | toProp A
        \\where {
          \\use \\level levelProp {A : \\Type} {p : \\Pi (x y : A) -> x = y} (x y : ToProp A p) : x = y \\elim x, y
            | toProp a, toProp a' => path \\lam i => toProp (p a a' i)
          }
      """, 1);
  }

  @Test
  public void useLevelCatContextTest2() {
    typeCheckModule("""
      \\data ToProp (A : \\Type) (n :+ Nat) (p : \\Pi (x y : A) -> x = y)
        | toProp A
        \\where {
          \\use \\level levelProp {A : \\Type} {n :+ Nat} {p : \\Pi (x y : A) -> x = y} (x y :+ ToProp A n p) : x = y \\elim x, y
            | toProp a, toProp a' => path \\lam i => toProp (p a a' i)
          }
      """, 1);
  }

  @Test
  public void useLevelCatUniverseTest() {
    typeCheckModule("""
      \\data ToProp (A : \\Cat) (p : \\Pi (x y : A) -> x = y)
        | toProp A
        \\where {
          \\use \\level levelProp {A : \\Cat} {p : \\Pi (x y : A) -> x = y} (x y : ToProp A p) : x = y \\elim x, y
            | toProp a, toProp a' => path \\lam i => toProp (p a a' i)
        }
      """);
  }

  @Test
  public void useLevelCatUniverseError() {
    typeCheckModule("""
      \\data ToProp (A : \\Cat) (p : \\Pi (x y : A) -> x = y)
        | toProp (a :+ A)
        \\where {
          \\use \\level levelProp {A : \\Cat} {p : \\Pi (x y : A) -> x = y} (x y : ToProp A p) : x = y \\elim x, y
            | toProp a, toProp a' => path \\lam i => toProp (p a a' i)
          }
      """, 1);
  }
}
