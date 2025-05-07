package org.arend.typechecking;

import org.arend.core.definition.Definition;
import org.arend.typechecking.error.TerminationCheckError;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class RecursiveTest extends TypeCheckingTestCase {
  @Test
  public void list() {
    assertSame(Definition.TypeCheckingStatus.NO_ERRORS, typeCheckDef("\\data List (A : \\Type0) | nil | cons A (List A)").status());
  }

  @Test
  public void dataLeftError() {
    Definition def = typeCheckDef("\\data List (A : \\Type0) | nil | cons (List A -> A)", 1);
    assertEquals(Definition.TypeCheckingStatus.HAS_ERRORS, def.status());
  }

  @Test
  public void dataRightError() {
    Definition def = typeCheckDef("\\data List (B : \\oo-Type0 -> \\Type0) (A : \\Type0) | nil | cons (B (List B A))", 1);
    assertEquals(Definition.TypeCheckingStatus.HAS_ERRORS, def.status());
  }

  @Test
  public void plus() {
    assertSame(Definition.TypeCheckingStatus.NO_ERRORS, typeCheckDef("\\func \\infixr 9 + (x y : Nat) : Nat \\elim x | zero => y | suc x' => suc (x' + y)").status());
  }

  @Test
  public void doubleRec() {
    assertSame(Definition.TypeCheckingStatus.NO_ERRORS, typeCheckDef("\\func \\infixr 9 + (x y : Nat) : Nat \\elim x | zero => y | suc zero => y | suc (suc x'') => x'' + (x'' + y)").status());
  }

  @Test
  public void functionError() {
    assertSame(Definition.TypeCheckingStatus.HAS_ERRORS, typeCheckDef("\\func \\infixr 9 + (x y : Nat) : Nat => x + y", 1).status());
  }

  @Test
  public void functionError2() {
    assertSame(Definition.TypeCheckingStatus.HAS_ERRORS, typeCheckDef("\\func \\infixr 9 + (x y : Nat) : Nat \\elim x | zero => y | suc zero => y | suc (suc x'') => y + y", 1).status());
  }

  @Test
  public void functionPartiallyApplied() {
    assertSame(Definition.TypeCheckingStatus.NO_ERRORS, typeCheckDef("\\func foo (z : (Nat -> Nat) -> Nat) (x y : Nat) : Nat \\elim x | zero => y | suc x' => z (foo z x')").status());
  }

  @Test
  public void cowithError() {
    typeCheckModule(
      "\\class C (x y : Nat)\n" +
      "\\func f (x : Nat) : C \\cowith | x => x | y => C.y {f x}", 1);
  }

  @Test
  public void mutualCowithError() {
    typeCheckModule("""
      \\class C (x y : Nat)
      \\func f (x : Nat) : C \\cowith | x => x | y => C.y {g x}
      \\func g (x : Nat) : C \\cowith | x => x | y => C.y {f x}
      """, 2);
  }

  @Test
  public void bodyBodyTest() {
    typeCheckModule(
      "\\func f (x : Nat) : \\Type => g 0\n" +
      "\\func g (x : Nat) : \\Type => f 0", 4);
  }

  @Test
  public void bodyBodyTest2() {
    typeCheckModule(
      "\\func f (x : Nat) : Nat => g 0\n" +
      "\\func g (x : Nat) : Nat => f 0", 4);
  }

  @Test
  public void bodyBodyLemmaTest() {
    typeCheckModule(
      "\\lemma f (x : Nat) : x = x => g x\n" +
      "\\lemma g (x : Nat) : x = x => f x", 4);
  }

  @Test
  public void bodyBodyCowithTest() {
    typeCheckModule("""
      \\class C (n : Nat)
      \\func f (x : Nat) : C \\cowith | n => C.n {g x}
      \\func g (x : Nat) : C \\cowith | n => C.n {f x}
      """, 2);
  }

  @Test
  public void bodyBodyNewTest() {
    typeCheckModule("""
      \\class C (n : Nat)
      \\func f (x : Nat) : C => \\new C (C.n {g x})
      \\func g (x : Nat) : C => \\new C (C.n {f x})
      """, 4);
  }

  @Test
  public void parametersTest() {
    typeCheckModule(
      "\\func f (n : \\let t => g 0 \\in Nat) : Nat | 0 => 0 | suc n => g n\n" +
      "\\func g (n : Nat) : Nat | 0 => 0 | suc n => f n", 1);
  }

  @Test
  public void parametersTypeTest() {
    typeCheckModule(
      "\\func f (x : \\let t => g 0 \\in Nat) : \\hType | 0 => Nat | suc x => g x\n" +
      "\\func g (x : Nat) : \\hType | 0 => Nat | suc x => f x", 1);
  }

  @Test
  public void resultTypeTestError() {
    typeCheckModule(
      "\\func f (n : Nat) : \\let t => g 0 \\in Nat | 0 => 0 | suc n => g n\n" +
      "\\func g (n : Nat) : Nat | 0 => 0 | suc n => f n", 4);
    assertThatErrorsAre(instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class));
  }

  @Test
  public void resultTypeTest() {
    typeCheckModule(
      "\\func f (n : Nat) : g n = g n | 0 => path (\\lam _ => g 0) | suc n => path (\\lam _ => g (suc n))\n" +
      "\\func g (n : Nat) : Nat | 0 => 0 | suc n => \\let t => f n \\in n");
  }

  @Test
  public void bodyBodyElimTest() {
    typeCheckModule(
      "\\func f (x : Nat) : \\hType | 0 => g 0 | suc _ => Nat\n" +
      "\\func g (x : Nat) : \\hType | 0 => f 0 | suc _ => Nat", 4);
    assertThatErrorsAre(instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class));
  }

  @Test
  public void bodyBodyElimTest2() {
    typeCheckModule(
      "\\func f (x : Nat) : Nat | 0 => g 0 | suc n => n \n" +
      "\\func g (x : Nat) : Nat | 0 => f 0 | suc n => n", 4);
    assertThatErrorsAre(instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class), instanceOf(TerminationCheckError.class));
  }

  @Test
  public void withType() {
    typeCheckDef("\\func f : Nat => f", 1);
  }

  @Test
  public void withoutType() {
    typeCheckDef("\\func f => f", 2);
  }

  @Test
  public void dataFunctionError() {
    typeCheckModule(
      "\\data D (n : Nat) : \\Set | con1 (f 1) | con2\n" +
      "\\func f (n : Nat) : \\Set => D n", 1);
  }

  @Test
  public void dataFunctionError2() {
    typeCheckModule(
      "\\data D (n : Nat) : \\Set | con1 (f 1) | con2\n" +
      "\\func f (n : Nat) : \\Set | 0 => Nat | suc n => D n", 1);
  }

  @Test
  public void dataFunctionError3() {
    typeCheckModule(
      "\\data D : \\Type | con1 | con2 (f (\\lam x => x))\n" +
      "\\func f (g : D -> D) : \\Type => g con1 = g con1", 2);
  }

  @Test
  public void mutualRecursionOrder() {
    typeCheckModule("""
      \\func g => D'
      \\data D : \\Set | con1 | con2 (d : D) (D' d)
      \\data D' (d : D) : \\Set \\with
        | con1 => con1'
        | con2 _ _ => con2'
      """);
  }

  @Test
  public void withoutPatternMatching() {
    typeCheckModule(
      "\\func f (n : Nat) : Nat | 0 => 0 | suc n => g n\n" +
      "\\func g (n : Nat) : Nat => f n");
  }

  @Test
  public void withoutPatternMatchingError() {
    typeCheckModule(
      "\\func f (n : Nat) : Nat | 0 => 0 | suc n => g n\n" +
      "\\func g (n : Nat) : Nat => f (suc n)", 4);
  }

  @Test
  public void levelsTest() {
    typeCheckModule("""
      \\func test (n : Nat) : Nat
        | 0 => 0
        | suc n => test \\lp n
      """);
  }

  @Test
  public void levelsError() {
    typeCheckModule("""
      \\func test (n : Nat) : Nat
        | 0 => 0
        | suc n => test (\\suc \\lp) n
      """, 1);
  }

  @Test
  public void recursiveLevels() {
    typeCheckDef("\\func test \\plevels lvl (n : Nat) : Nat | 0 => 0 | suc n => test \\levels (\\suc lvl) _ n", 1);
  }

  @Test
  public void recursiveLevels2() {
    typeCheckModule(
      "\\func f \\plevels lvl (n : Nat) : Nat | 0 => 0 | suc n => g \\levels lvl _ n\n" +
      "\\func g \\plevels lvl (n : Nat) : Nat | 0 => 0 | suc n => f \\levels (\\suc lvl) _ n", 1);
  }

  @Test
  public void recursiveLevels3() {
    typeCheckModule(
      "\\data D \\hlevels lvl : \\Set | con (d : D) (E \\levels _ lvl d)\n" +
      "\\func E \\hlevels lvl (d : D \\levels _ (\\suc lvl)) : \\Set | con _ _ => Nat", 2);
  }

  @Test
  public void expectedTypeTest() {
    typeCheckModule("""
      \\func foo (n : Nat) : Nat
        | 0 => 0
        | suc n => bar n
      \\func bar (n : Nat) => foo n
      """, 1);
  }
}
