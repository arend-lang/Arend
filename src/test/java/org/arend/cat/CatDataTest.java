package org.arend.cat;

import org.arend.core.definition.DataDefinition;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.level.ConstLevel;
import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.local.PropOnlyPatternError;
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

  private static final String OB_HIT = """
      \\truncated \\data TrP (A : \\Type) : \\Prop | inP A
      \\data Ob (X : \\Cat)
        | con1 (_ :+ X)
        | con2
        | path I {
          | left => con2
          | right => con2
        }
      """;

  @Test
  public void obHitInvariantError() {
    typeCheckModule(OB_HIT + """
      \\func test {X : \\Cat} (o : Ob X) : Nat \\elim o
        | con1 _ => 0
        | con2 => 0
        | path _ => 0
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void obHitInvariantProp() {
    typeCheckModule(OB_HIT + """
      \\lemma test {X : \\Cat} (o : Ob X) : TrP Nat \\elim o
        | con1 _ => inP 0
        | con2 => inP 0
      """);
  }

  @Test
  public void obHitInvariantPropCase() {
    typeCheckModule(OB_HIT + """
      \\lemma test {X : \\Cat} (o : Ob X) : TrP Nat => \\case o \\with {
        | con1 _ => inP 0
        | con2 => inP 0
      }
      """);
  }

  @Test
  public void obHitInvariantSetCaseError() {
    typeCheckModule(OB_HIT + """
      \\func test {X : \\Cat} (o : Ob X) : Nat => \\case o \\with {
        | con1 _ => 0
        | con2 => 0
        | path _ => 0
      }
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void obHitInvariantSFuncError() {
    typeCheckModule(OB_HIT + """
      \\sfunc test {X : \\Cat} (o : Ob X) : Nat \\elim o
        | con1 _ => 0
        | con2 => 0
        | path _ => 0
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void obHitInvariantLevel() {
    typeCheckModule(OB_HIT + """
      \\sfunc test {X : \\Cat} {A : \\Type} (p : \\Pi (x y : A) -> x = y) (a : A) (o : Ob X) : \\level A p \\elim o
        | con1 _ => a
        | con2 => a
      """);
  }

  @Test
  public void obHitInvariantLevelError() {
    typeCheckModule(OB_HIT + """
      \\func test {X : \\Cat} {A : \\Type} (p : \\Pi (x y : A) -> x = y) (a : A) (o : Ob X) : \\level A p \\elim o
        | con1 _ => a
        | con2 => a
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void obHitNestedInvariantError() {
    typeCheckModule(OB_HIT + """
      \\data W (X : \\Cat)
        | w (o : Ob X)
      \\func test {X : \\Cat} (v : W X) : Nat \\elim v
        | w (con1 _) => 0
        | w con2 => 0
        | w (path _) => 0
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void obHitNestedInvariantProp() {
    typeCheckModule(OB_HIT + """
      \\data W (X : \\Cat)
        | w (o : Ob X)
      \\lemma test {X : \\Cat} (v : W X) : TrP Nat \\elim v
        | w (con1 _) => inP 0
        | w con2 => inP 0
      """);
  }

  @Test
  public void obHitNestedCovariantTest() {
    typeCheckModule(OB_HIT + """
      \\data W (X : \\Cat)
        | w (o :+ Ob X)
      \\func test {X : \\Cat} (v :+ W X) : Nat \\elim v
        | w (con1 _) => 0
        | w con2 => 0
        | w (path _) => 0
      """);
  }

  @Test
  public void obHitDataElimError() {
    typeCheckModule(OB_HIT + """
      \\data D {X : \\Cat} (o : Ob X) \\elim o
        | con1 _ => c1
        | con2 => c2
        | path _ => c3
      """, 2);
  }

  @Test
  public void obHitCovariantTest() {
    typeCheckModule(OB_HIT + """
      \\func test {X : \\Cat} (o :+ Ob X) : Nat \\elim o
        | con1 _ => 0
        | con2 => 0
        | path _ => 0
      """);
  }
}
