package org.arend.typechecking.levels;

import org.arend.Matchers;
import org.arend.core.context.param.TypedSingleDependentLink;
import org.arend.core.definition.ClassField;
import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.*;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.sort.SortExpression;
import org.arend.ext.core.level.ConstLevel;
import org.arend.prelude.Prelude;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Objects;

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
    typeCheckDef("\\func test => \\Type", 1);
  }

  @Test
  public void idTest() {
    checkDef("\\func test {A : \\Type} (a : A) => a");
  }

  @Test
  public void sigmaTest() {
    checkDef("\\func test {A : \\Type} (B : A -> \\Type) (p : \\Sigma (x : A) (B x)) => p");
  }

  @Test
  public void piTest() {
    checkDef("\\func test {A : \\Type} (B : A -> \\Type) (f : \\Pi (x : A) -> B x) => f");
  }

  @Test
  public void pairTest() {
    typeCheckDef("\\func test (A : \\Type) => (A,A)", 1);
  }

  @Test
  public void dataTest() {
    typeCheckModule("""
      \\data D (A : \\Type) (a : A) | con (A -> A)
      \\func test1 => D Nat 7
      \\func test2 => D _ 7
      \\func test3 => D Nat
      \\func test4 (d : D _ 7) => d
      \\func test5 {B : \\Type} {b : B} (d : D _ b) => d
      """);
    checkLevelParameters("D", "test1", "test2", "test3", "test4", "test5");
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test1")).getResultType());
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test2")).getResultType());
    assertEquals(new PiExpression(new TypedSingleDependentLink(true, null, ExpressionFactory.Nat()), new UniverseExpression(Sort.SET0)), ((FunctionDefinition) getDefinition("test3")).getResultType());
  }

  @Test
  public void partiallyAppliedTest() {
    typeCheckModule("""
      \\data D (A : \\Type) (a : A)
      \\func test => D
      """, 1);
  }

  @Test
  public void functionTest() {
    typeCheckModule("""
      \\data D (A : \\Type) (a a' : A) | con A
      \\func fun (A : \\Type) (a : A) => D A a a
      \\func test => fun Nat 7
      """);
    checkLevelParameters("D", "fun", "test");
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test")).getResultType());
  }

  @Test
  public void functionTest2() {
    typeCheckModule("\\func test (A : \\Type) (n : Nat) : \\Type => A");
  }

  @Test
  public void functionTest3() {
    typeCheckModule("""
      \\func test (A : \\Type) (n : Nat) : \\Type \\elim n
        | 0 => A
        | suc _ => A
      """, 1);
  }

  @Test
  public void functionTest4() {
    typeCheckModule("""
      \\record R (A : \\Type) (a a' : A)
      \\func test (A : \\Type) (a : A) => R A a
      """);
    checkLevelParameters("R", "test");
    FunctionDefinition function = (FunctionDefinition) getDefinition("test");
    assertEquals(new UniverseExpression(new SortExpression.Var(0, Collections.emptyList())), function.getResultType());
  }

  @Test
  public void recordTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\record S \\extends R
        | B : A -> \\Type
        | b : B a
      """);
    checkLevelParameters("R", "S");
  }

  @Test
  public void recordTest2() {
    typeCheckModule("""
      \\record R (A : \\Type) (a a' : A)
      \\func test1 => R Nat 7
      \\func test2 => R _ 7
      \\func test3 => R Nat
      \\func test4 (d : R _ 7) => d
      \\func test5 {B : \\Type} {b : B} (r : R _ b) => r
      """);
    checkLevelParameters("R", "test1", "test2", "test3", "test4", "test5");
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test1")).getResultType());
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test2")).getResultType());
    assertEquals(new UniverseExpression(Sort.SET0), ((FunctionDefinition) getDefinition("test3")).getResultType());
  }

  @Test
  public void recordTest3() {
    typeCheckModule("""
      \\record R (A : \\Type) (a a' : A)
      \\func test => R
      """, 1);
  }

  @Test
  public void recordTest4() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test => R Nat
      """, 1);
  }

  @Test
  public void recordTest5() {
    typeCheckModule("""
      \\record R (A : \\Type) (a a' : A)
      \\func test (x : R) => x.a
      """);
  }

  @Test
  public void recordTest6() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test (x : R Nat) => x.b
      """);
  }

  @Test
  public void inferenceTest() {
    typeCheckModule("""
      \\func foo {A : \\Prop} (a a' : A) => 0
      \\func test : 0 = 0 -> Nat => foo idp
      """);
  }

  @Test
  public void truncatedTest() {
    typeCheckModule("""
      \\truncated \\data Trunc (A : \\Type) : \\Set
        | in A
      \\func test (A : \\3-Type7) : \\Set7 => Trunc A
      """);
    checkLevelParameters("Trunc", "test");
  }

  @Test
  public void truncatedTest2() {
    typeCheckModule("""
      \\truncated \\data Trunc (A : \\Type) : \\Set
        | in A
      \\func map {A B : \\Type} (t : Trunc A) (f : A -> B) : Trunc B \\elim t
        | in a => in (f a)
      """);
  }

  @Test
  public void truncatedTest3() {
    typeCheckModule("""
      \\truncated \\data Trunc (A : \\Type) : \\Prop
        | in A
      \\lemma test {A : \\Type} (a : A) (n : Nat) : Trunc A \\elim n
        | 0 => in a
        | suc _ => in a
      """);
  }

  @Test
  public void dataTwoVars() {
    typeCheckModule("""
      \\data Or (A B : \\Type)
        | inl A
        | inr B
      \\func test : \\Set0 => Or Nat \\Set1
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void dataTwoVars2() {
    typeCheckModule("""
      \\data Or (A B : \\Type)
        | inl A
        | inr B
      \\func test : \\Set0 => Or \\Set1 Nat
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void lamTest() {
    typeCheckDef("""
      \\func test {A : \\Type} (B : A -> \\Type) : Nat
        => \\let T => \\lam x => B x \\in 0
      """);
  }

  @Test
  public void splitFieldsExtend() {
    typeCheckModule("""
      \\record R (A B : \\Type)
      \\record S (C D : \\Type) (c : C) (d : D)
      \\record T \\extends R, S
      \\func test : \\Type3 => T { | A => \\Set0 | B => \\Set1 | C => \\Set2 | D => \\Set3 }
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void splitFieldsExtend2() {
    typeCheckModule("""
      \\record R (A B : \\Type)
      \\record S (C D : \\Type)
      \\record T \\extends R, S
      \\func test (t : T \\Set0 \\Set1 \\Set2 \\Set3) : \\Set3 => t.C
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void splitFieldsExtend3() {
    typeCheckModule("""
      \\record R (A B : \\Type)
      \\record S (C D : \\Type)
      \\record T \\extends R, S
      \\func test (t : T \\Set0 \\Set1 \\Set2 \\Set3) : \\Set1 => t.C
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void arrayTest() {
    typeCheckDef("\\func test (A : \\Set) : \\Set => Array A");
  }

  @Test
  public void preludeTest() {
    typeCheckModule("");
    assertEquals(Sort.SET0, Prelude.NAT.getSort());
    assertEquals(Sort.SET0, Prelude.FIN.getSort());
  }

  @Test
  public void fieldTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func fun (r : R) (x : r.A) => x
      \\func test => fun (\\new R Nat 7)
      """);
    checkLevelParameters("R", "fun", "test");
    assertEquals(Sort.SET0, ((FunctionDefinition) getDefinition("test")).getResultType().getSortOfType());
  }

  @Test
  public void fieldTest2() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test (r : R) (n : Nat) : \\Type => r.A
      """);
  }

  @Test
  public void fieldTest3() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test (r : R) (n : Nat) : \\Type \\elim n
        | 0 => r.A
        | suc _ => r.A
      """, 1);
  }

  @Test
  public void fieldDataTest() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\data D (r : R) | con1 r.A | con2 r.A
      \\func test => D (\\new R \\Set0 \\3-Type7 Nat Nat)
      """);
    assertEquals(new Sort(new Level(BigInteger.ONE), new ConstLevel(BigInteger.ONE)), ((FunctionDefinition) getDefinition("test")).getResultType().toSort());
  }

  @Test
  public void fieldDataTest2() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\data D (r : R) | con1 r.B | con2 r.B
      \\func test => D (\\new R \\3-Type7 Nat \\Set0 0)
      """);
    assertEquals(Sort.SET0, ((FunctionDefinition) getDefinition("test")).getResultType().toSort());
  }

  @Test
  public void fieldDataTest3() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\data D (r : R) | con1 r.A | con2 r.B
      \\func test => D (\\new R \\3-Type7 \\7-Type3 Nat Nat)
      """);
    assertEquals(new Sort(new Level(BigInteger.valueOf(8)), new ConstLevel(BigInteger.valueOf(8))), ((FunctionDefinition) getDefinition("test")).getResultType().toSort());
  }

  @Test
  public void fieldDataTest4() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\record S (C : \\Type) (r : R)
      \\data D (s : S) | con1 s.r.B | con2 s.C
      \\func test => D (\\new S \\3-Type7 (\\new R \\100-Type100 \\7-Type3 Nat Nat))
      """);
    assertEquals(new Sort(new Level(BigInteger.valueOf(8)), new ConstLevel(BigInteger.valueOf(8))), ((FunctionDefinition) getDefinition("test")).getResultType().toSort());
  }

  @Test
  public void overrideTest() {
    typeCheckModule("""
      \\record R (A : \\Type)
      \\record S \\extends R
      \\record T (r : R)
      \\record U \\extends T {
        \\override r : S
      }
      """);
  }

  @Test
  public void customClassLevelsTest() {
    typeCheckModule("""
      \\record T (A : \\Type)
      \\record R.{s} (F : T.{s} -> T.{s})
      \\func test (r : R.{3}) : R.{4} => r
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void overriddenClassLevelsTest() {
    typeCheckModule("""
      \\record T (A : \\Type)
      \\record R.{s} (B : T.{s} -> T.{s})
      \\func test (r : R) : R.{4} => r
      """, 2);
    assertThatErrorsAre(Matchers.warning(), Matchers.typeMismatchError());
  }

  @Test
  public void overriddenClassLevelsTest2() {
    typeCheckModule("""
      \\record R (A : \\Type)
      \\func test (r : R) : R.{4} => r
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void implementedFieldTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test (X : \\Set3) (r : R X) : R.{3} => r
      """);
  }

  @Test
  public void implementedFieldError() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test (X : \\Set4) (r : R X) : R.{3} => r
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void classImplTest() {
    typeCheckModule("""
      \\record R (f : Nat -> \\Set)
      \\func test : R => \\new R \\case __ \\with {
        | 0 => \\Sigma
        | suc _ => Nat
        }
      """);
  }

  @Test
  public void classImplTest2() {
    typeCheckModule("""
      \\record R (f : Nat -> \\Set)
      \\func rrr => \\new R.{0} \\case __ \\with {
        | 0 => \\Sigma
        | suc _ => Nat
        }
      \\func test : R.{0} => rrr
      """);
    ClassCallExpression classCall = (ClassCallExpression) ((FunctionDefinition) getDefinition("rrr")).getResultType();
    assertEquals(new UniverseExpression(Sort.SET0), ((CaseExpression) ((LamExpression) Objects.requireNonNull(classCall.getAbsImplementationHere((ClassField) getDefinition("R.f")))).getBody()).getResultType());
    assertEquals(0, classCall.getLevels().size());
  }

  @Test
  public void classImplTest3() {
    typeCheckModule("""
      \\record R (f : Nat -> \\Set)
      \\func rrr : R.{0} => \\new R \\case __ \\with {
        | 0 => \\Sigma
        | suc _ => Nat
        }
      \\func test : R.{0} => rrr
      """);
    ClassCallExpression classCall = (ClassCallExpression) ((FunctionDefinition) getDefinition("rrr")).getResultType();
    assertEquals(new UniverseExpression(Sort.SET0), ((CaseExpression) ((LamExpression) Objects.requireNonNull(classCall.getAbsImplementationHere((ClassField) getDefinition("R.f")))).getBody()).getResultType());
    assertEquals(0, classCall.getLevels().size());
  }

  @Test
  public void classImplTest4() {
    typeCheckModule("""
      \\record R (f : Nat -> \\Set)
      \\func rrr : R => \\new R.{0} \\case __ \\with {
        | 0 => \\Sigma
        | suc _ => Nat
        }
      \\func test : R.{0} => rrr
      """);
    ClassCallExpression classCall = (ClassCallExpression) ((FunctionDefinition) getDefinition("rrr")).getResultType();
    assertEquals(new UniverseExpression(Sort.SET0), ((CaseExpression) ((LamExpression) Objects.requireNonNull(classCall.getAbsImplementationHere((ClassField) getDefinition("R.f")))).getBody()).getResultType());
    assertEquals(0, classCall.getLevels().size());
  }

  @Test
  public void skipImplementedTest() {
    typeCheckModule("""
      \\record T (A : \\Type)
      \\record R (t : T) (B : \\Type)
      \\func test => R.{0} (\\new T Nat)
      """);
  }

  @Test
  public void doubleFieldTest() {
    typeCheckModule("""
      \\record R (A : \\Set)
      \\record S (r : R)
      \\func K (s : S) : \\Set => s.r.A
      \\func foo (X : \\Set) => X
      \\func test (s : S) => foo (K s)
      """);
  }

  @Test
  public void skipImplementedInfiniteFieldTest() {
    typeCheckModule("""
      \\record R (A B : \\Type)
      \\func test (A : \\Type1) => R.{0} A
      """);
  }

  @Test
  public void implementInfiniteFieldTest() {
    typeCheckModule("""
      \\record R (A : \\Type)
      \\record S (B : \\Type) \\extends R
        | A => B
      \\func foo.{u} (r : R.{u}) => 0
      \\func bar.{u} (s : S.{u}) => foo.{0} s
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void implementInfiniteFieldTest2() {
    typeCheckModule("""
      \\record R (A : \\Type)
      \\record S (B : \\Type) \\extends R
        | A => B
      \\func test.{u} (s : S.{u}) : R.{u} => s
      """);
  }

  @Test
  public void classInferenceLevelTest() {
    typeCheckModule("""
      \\record R (A B : \\Type)
      \\func foo {r : R.{2} Nat} (p : (\\new R Nat \\Set4) = {R.{5} Nat} r) => 0
      \\func test => foo idp
      """, 1);
    assertThatErrorsAre(Matchers.argInferenceError());
  }

  @Test
  public void doubleInferenceClassCallLevelTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func foo {X : \\Type} (x y : X) => 0
      \\func test {A B : \\Set3} (r : R A) (s : R B) => foo r s
      """);
  }
}
