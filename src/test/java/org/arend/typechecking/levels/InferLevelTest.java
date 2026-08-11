package org.arend.typechecking.levels;

import org.arend.Matchers;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.SortExpression;
import org.arend.ext.core.ops.CMP;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.junit.Test;

import java.math.BigInteger;

import static org.arend.Matchers.typeMismatchError;
import static org.junit.Assert.assertTrue;

public class InferLevelTest extends TypeCheckingTestCase {
  @Test
  public void noEquations() {
    // no equations
    // error: cannot infer ?l
    typeCheckModule("""
      \\func A.{u} => \\Type u
      \\func f => A
      """, 1);
    assertThatErrorsAre(Matchers.warning());
  }

  @Test
  public void metaVarEquation() {
    // ?l <= ?l'
    // error: cannot infer ?l, ?l'
    typeCheckModule("""
      \\func A.{u} => \\Type u
      \\func f.{u} (A : \\Type u) => A
      \\func g => f A
      """, 2);
    assertThatErrorsAre(Matchers.warning(), Matchers.warning());
  }

  @Test
  public void universeTest() {
    typeCheckModule("\\func f.{u} (A : \\Type u) : \\Type => A = A");
  }

  @Test
  public void belowTen() {
    // ?l <= 10
    // error: cannot infer ?l
    typeCheckModule("""
      \\func A.{u} => \\Type u
      \\func f : \\Type10 => A
      """);
  }

  @Test
  public void belowParam() {
    // ?l <= c
    // error: cannot infer ?l
    typeCheckModule("""
      \\func A.{u} => \\Type u
      \\func f.{u} : \\Type (\\suc u) => A
      """, 1);
    assertThatErrorsAre(Matchers.warning());
  }

  @Test
  public void belowParam2() {
    typeCheckModule("""
    \\func A.{u} => \\Type u
    \\func f.{u} : \\Type u => A
    """, -1);
  }

  @Test
  public void btwZeroAndParam() {
    // 0 <= ?l, 0 <= c
    // ok: ?l = 0
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) => A
      \\func g.{u} : \\Type u => f Nat
      """);
  }

  @Test
  public void btwOneAndParam() {
    // 1 <= ?l, 1 <= c
    // error: cannot solve 1 <= c
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) => A
      \\func g.{u} : \\Type u => f \\Type0
      """, 1);
  }

  @Test
  public void btwOneAndParamWithH() {
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) => A
      \\func g => f \\Type0
      """);
  }

  @Test
  public void btwZeroAndTen() {
    // 0 <= ?l <= 10
    // ok: ?l = 0
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) => A
      \\func g : \\Type10 => f Nat
      """);
  }

  @Test
  public void btwOneAndTen() {
    // 1 <= ?l <= 10
    // ok: ?l = 1
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) => A
      \\func g : \\Type10 => f \\Type0
      """);
  }

  @Test
  public void greaterThanZero() {
    // 0 <= ?l
    // ok: ?l = 0
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) => A
      \\func g => f Nat
      """);
  }

  @Test
  public void greaterThanOne() {
    // 1 <= ?l
    // ok: ?l = 1
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) => A
      \\func g => f \\Type0
      """);
  }

  @Test
  public void propImpredicative() {
    typeCheckModule("\\func g (X : \\Set10) (P : X -> \\Prop) : \\Prop => \\Pi (a : X) -> P a");
  }

  @Test
  public void propImpredicative2() {
    typeCheckModule("""
      \\func f (X : \\Set10) (P : X -> \\Type) => \\Pi (a : X) -> P a
      \\func g (X : \\Set10) (P : X -> \\Prop) : \\Prop => f X P
      """);
  }

  @Test
  public void propImpredicative3() {
    typeCheckModule("""
      \\func f.{u} (X : \\Set10) (P : X -> \\Type u) => \\Pi (a : X) -> P a
      \\func g (X : \\Set10) (P : X -> \\Prop) : \\Prop => f.{0} X P
      """, 1);
  }

  @Test
  public void propImpredicative4() {
    typeCheckModule("""
      \\func f.{u} (X : \\Set10) (P : X -> \\Type u) => \\Pi (a : X) -> P a
      \\func g (X : \\Set10) (P : X -> \\Prop) : \\Prop => f X P
      """, 1);
  }

  @Test
  public void levelOfPath() {
    typeCheckModule("\\func f (X : \\Set10) (x : X) : \\Prop => x = x");
  }

  @Test
  public void levelOfPath2() {
    typeCheckModule("\\func f (X : \\Set10) (x : X) : \\1-Type1 => x = x -> \\Set0");
  }

  @Test
  public void levelOfPath3() {
    typeCheckModule("\\func f (X : \\Set10) (x : X) : \\1-Type1 => (x = x : \\Prop) -> \\Set0");
  }

  @Test
  public void constantUpperBound() {
    typeCheckModule("""
      \\func f (A : \\Type) => A
      \\func g (B : \\Type) : \\Set => f B
      """, 1);
  }

  @Test
  public void expectedType() {
    typeCheckModule("""
      \\func X.{u} => \\Type u
      \\func f.{u} : X => \\Type u
      """);
  }

  @Test
  public void parameters() {
    typeCheckModule("""
      \\func X.{u} => \\Type u
      \\func f.{u} (A : X.{u}) => 0
      \\func g => f \\Set0
      """
    );
  }

  @Test
  public void lhLessThanInf() {
    typeCheckModule("""
      \\func f.{u} (A : \\Type u) (a a' : A) (p : a = a') => p
      \\func X : \\Type => Nat
      \\func g.{u} : X = X => f (\\Type u) X X idp
      """);
  }

  @Test
  public void pLevelTest() {
    typeCheckModule("""
      \\func squeeze1 (i j : I) : I =>
        coe (\\lam x => left = x) idp j @ i
      \\func squeeze (i j : I) =>
        coe (\\lam i => Path (\\lam j => left = squeeze1 i j) idp (path (\\lam j => squeeze1 i j))) idp right @ i @ j
      \\func psqueeze {A : \\Type} {a a' : A} (p : a = a') (i : I) : a = p @ i =>
        path (\\lam j => p @ squeeze i j)
      \\func Jl {A : \\Type} {a : A} (B : \\Pi (a' : A) -> a = a' -> \\Type) (b : B a idp) {a' : A} (p : a = a') : B a' p =>
        coe (\\lam i => B (p @ i) (psqueeze p i)) b right
      \\func foo.{u} (A : \\Type u) (a0 a1 : A) (p : a0 = a1) =>
        Jl (\\lam _ q => (idp {A} {a0} = idp {A} {a0}) = (q = q)) idp p
      """);
  }

  @Test
  public void classLevelTest() {
    typeCheckModule("""
      \\class A.{u} {
        | X : \\Type u
      }
      \\func f : A.{0} => \\new A { | X => \\Type0 }
      """, 1);
  }

  @Test
  public void setIsNotProp() {
    typeCheckDef("""
      \\func isSur {A B : \\Set} (f : A -> B) : \\Prop =>
        \\Pi (b : B) -> \\Sigma (a : A) (b = f a)
      """, 1);
  }

  @Test
  public void idTest() {
    typeCheckModule("""
      \\class Functor.{u} (F : \\Type u -> \\Type u)
        | fmap {A B : \\Type u} : (A -> B) -> F A -> F B
      \\data Maybe.{u} (A : \\Type u) | nothing | just A
      \\func id'.{u} {A : \\Type u} (a : A) => a
      \\func idTest.{u} : \\Type1 => id'.{\\suc u} (Functor Maybe)
      """, -1);
  }

  @Test
  public void idTest2() {
    typeCheckModule("""
      \\class Functor.{u} (F : \\Type u -> \\Type u)
        | fmap {A B : \\Type u} : (A -> B) -> F A -> F B
      \\data Maybe.{u} (A : \\Type u) | nothing | just A
      \\func id'.{u} {A : \\Type u} (a : A) => a
      \\func idTest.{u} : \\Type1 => id'.{\\suc (\\suc u)} (Functor Maybe)
      """, 2);
    assertThatErrorsAre(Matchers.warning(), Matchers.warning());
  }

  @Test
  public void idTest3() {
    typeCheckModule("""
      \\class Functor.{u} (F : \\Type u -> \\Type u)
        | fmap {A B : \\Type u} : (A -> B) -> F A -> F B
      \\data Maybe.{u} (A : \\Type u) | nothing | just A
      \\func id'.{u} {A : \\Type u} (a : A) => a
      \\func idTest.{u} => id'.{\\suc (\\suc u)} (Functor Maybe.{0})
      """);
  }

  @Test
  public void idTest4() {
    typeCheckModule("""
      \\class Functor.{u} (F : \\Type u -> \\Type u)
        | fmap {A B : \\Type u} : (A -> B) -> F A -> F B
      \\data Maybe.{u} (A : \\Type u) | nothing | just A
      \\func id'.{u} {A : \\Type u} (a : A) => a
      \\func idTest.{u} => id'.{\\suc (\\suc u)} (Functor.{0} Maybe)
      """);
  }

  @Test
  public void idTest5() {
    typeCheckModule("""
      \\func type.{u} => \\Type u
      \\func test : \\Type10 => type
      """);
  }

  @Test
  public void idTestError() {
    typeCheckModule("""
      \\class Functor.{u} (F : \\Type u -> \\Type u)
        | fmap {A B : \\Type u} : (A -> B) -> F A -> F B
      \\data Maybe.{u} (A : \\Type u) | nothing | just A
      \\func id'.{u} {A : \\Type u} (a : A) => a
      \\func idTest.{u} => id'.{\\suc (\\suc u)} (Functor Maybe)
      """, 2);
    assertThatErrorsAre(Matchers.warning(), Matchers.warning());
  }

  @Test
  public void dataLevelsTest1() {
    typeCheckModule("""
      \\data D.{u} | con (\\Type u)
      \\func f (d : D.{1}) : D.{0} => d
      """, 1);
  }

  @Test
  public void dataLevelsTest2() {
    typeCheckModule("""
      \\data D.{u} | con (\\Type u)
      \\func fromD.{u} (d : D.{u}) : \\Type u | con A => A
      \\func ddd : \\Type0 => fromD (con \\Type0)
      """, 1);
  }

  @Test
  public void funcLevelsTest() {
    typeCheckModule("""
      \\func F.{u} => \\Type u
      \\func f (d : F.{1}) : F.{0} => d
      """, 1);
  }

  @Test
  public void classTest() {
    typeCheckModule("\\class B.{u} (F : \\Type u -> \\Type u) (A : \\Type0) | foo : F A");
  }

  @Test
  public void classTest2() {
    typeCheckModule("\\class B.{u} (F : \\Type u -> \\Type u) (A : \\Type1) | foo : F A", 1);
  }

  @Test
  public void fieldTest() {
    typeCheckModule("""
      \\record R.{u}
        | f : \\Type u -> \\Type u
      \\record S.{u}
        | inst : R.{u}
        | func (X : \\Type0) : f {inst} X
      """);
  }

  @Test
  public void fieldTest2() {
    typeCheckModule("""
      \\record R.{u}
        | f : \\Type u -> \\Type u
      \\record S.{u}
        | inst : R.{u}
        | func (X : \\Type1) : f {inst} X
      """, 1);
  }

  @Test
  public void funcTest() {
    typeCheckModule("""
      \\data Bool | true | false
      \\func T (b : Bool) : \\Type0
        | true => Nat
        | false => \\Sigma
      """);
  }

  @Test
  public void funcTest2() {
    typeCheckModule("""
      \\data Bool | true | false
      \\func T (b : Bool) : \\Set0
        | true => Nat
        | false => \\Sigma
      \\func test (b : Bool) : \\Prop => T b
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Test
  public void pathTest() {
    typeCheckModule("""
      \\func eq {A : \\Type} (x y : A) => x = y
      \\func id {A : \\Prop} (a : A) => a
      \\func test {A : \\Set} {x y : A} (p : eq x y) => id p
      """);
  }

  @Test
  public void pathTest2() {
    typeCheckModule("""
      \\data Test {A : \\Type} (x y : A)
        | con (x = y)
      \\func test {A : \\Type} (As : \\Pi {a a' : A} (p q : a = a') -> p = q) {x y : A} (t s : Test x y) : t = s \\elim t, s
        | con p, con q => path (\\lam i => con (As p q @ i))
      """);
  }

  @Test
  public void propTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test => \\Pi (A : \\Set0) (a : A) -> a = a");
    assertTrue(Level.compare(new Level(BigInteger.ZERO), ((SortExpression.Const) ((UniverseExpression) def.getResultType()).getSortExpression()).getSort().getPLevel(), CMP.EQ, DummyEquations.getInstance(), null));
  }

  @Test
  public void recordTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A) (p : a = a)
      \\lemma test {A : \\Set} (a : A) : R A a \\cowith
        | p => idp
      """);
  }

  @Test
  public void fieldLevelTest() {
    typeCheckModule("""
      \\record SomeSigma (A : \\Type) (J : \\Set)
      \\class SomeWrapper (X : SomeSigma Nat)
      \\func test (w : SomeWrapper) : \\Type => w.X.J
      """);
  }

  @Test
  public void transitivityTest() {
    typeCheckModule("""
      \\class C.{u} (A : \\Type u) (a : A)
      \\data Wrap.{u} (A : \\Type u) | wrap A
      \\func foo.{u} {A : \\Type u} (c : C (Wrap A)) => c.a
      \\func test.{u} {A : \\Type u} (c : C (Wrap.{\\suc u} A)) => foo c
      """);
  }

  @Test
  public void transitivityTest2() {
    typeCheckModule("""
      \\class C.{u} {A : \\Type u} (a : A)
      \\data Wrap.{u} (A : \\Type u) | wrap A
      \\class D.{u} (B : \\Type u) \\extends C.{u}
        | A => Wrap B
      \\func foo.{u} (d : D.{u}) => d.a
      \\func test.{u} {B : \\Type u} (d : D.{\\suc u} { | B => B }) => foo d
      """);
  }

  @Test
  public void transitivityTest3() {
    typeCheckModule("""
      \\class C.{u} (A : \\Type u) (a : A)
      \\data Wrap.{u} (A : \\Type u) | wrap A
      \\func test1.{u} {A : \\Type u} (c : C (Wrap.{u} A)) : C (Wrap.{u} A) => c
      \\func test2.{u} {A : \\Type u} (c : C (Wrap.{u} A)) : C.{u} => c
      \\func test.{u} {A : \\Type u} (c : C (Wrap.{\\suc u} A)) : C.{\\suc u} => c
      """);
  }

  @Test
  public void transitivityTest4() {
    typeCheckModule("""
      \\class C.{u} {A : \\Type u} (a : A)
      \\class D \\extends C
        | A => Nat
      \\class E.{u} (B : \\Type u) \\extends D.{u}
      \\func test1.{u} (e : E.{\\suc u}) : D.{u} => e
      """, 1);
    assertThatErrorsAre(typeMismatchError());
  }

  @Test
  public void classLevelsTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test => R.{1}
      """);
  }

  @Test
  public void commonLevelTest() {
    typeCheckDef("\\func test (A : \\1-Type1) (B : \\2-Type2) => A = B");
  }
}
