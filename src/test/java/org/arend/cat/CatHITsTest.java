package org.arend.cat;

import org.arend.Matchers;
import org.arend.core.definition.DataDefinition;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.ext.error.MissingClausesError;
import org.arend.typechecking.error.local.PropOnlyPatternError;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatHITsTest extends TypeCheckingTestCase {
  @Test
  public void directedConditionsTest() {
    typeCheckModule("""
      \\data D | con1 | con2 (i :+ DI) \\with { | dleft => con1 | dright => con1 }
      \\func leftTest : con2 dleft = con1 => idp
      \\func rightTest : con2 dright = con1 => idp
      """);
    assertEquals(ConstLevel.CAT_INFINITY, ((DataDefinition) getDefinition("D")).getSortExpression().withInfLevel().getHLevel());
  }

  @Test
  public void directedPathTest() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1
      \\func f : con1 ~> con1 => con2
      \\func leftTest : con2 dleft = con1 => idp
      \\func rightTest : con2 dright = con1 => idp
      """);
    assertEquals(ConstLevel.CAT_INFINITY, ((DataDefinition) getDefinition("D")).getSortExpression().withInfLevel().getHLevel());
  }

  @Test
  public void directedPathTest2() {
    typeCheckModule("""
      \\data D | con1 | con2 | con3 : con1 ~> con2
      \\func f : con1 ~> con2 => con3
      \\func leftTest : con3 dleft = con1 => idp
      \\func rightTest : con3 dright = con2 => idp
      """);
  }

  @Test
  public void directedConditionsRecTest() {
    typeCheckModule("""
      \\data D | con1 | con2 (i :+ DI) \\with { | dleft => con1 | dright => con1 }
      \\func rec {C :+ \\Cat} (c :+ C) (p :+ c ~> c) (d :+ D) : C \\elim d
        | con1 => c
        | con2 i => p d@ i
      \\func test {C : \\Cat} (c : C) (p : c ~> c) (i : DI) : rec c p (con2 i) = p d@ i => idp
      """);
  }

  @Test
  public void directedPathRecTest() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1
      \\func rec {C :+ \\Cat} (c :+ C) (p :+ c ~> c) (d :+ D) : C \\elim d
        | con1 => c
        | con2 i => p d@ i
      \\func test {C : \\Cat} (c : C) (p : c ~> c) (i : DI) : rec c p (con2 i) = p d@ i => idp
      """);
  }

  @Test
  public void directedPathPatternRecTest() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1
      \\func rec {C :+ \\Cat} (c :+ C) (p :+ c ~> c) (d :+ D) : C \\elim d
        | con1 => c
        | con2 => p
      \\func test {C : \\Cat} (c : C) (p : c ~> c) (i : DI) : rec c p (con2 i) = p d@ i => idp
      """);
  }

  @Test
  public void directedPathMapTest() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1
      \\data E | base1 | base2 | arr : base1 ~> base2
      \\func map (d :+ D) : E
        | con1 => base1
        | con2 i => base1
      \\func map2 (e :+ E) : D
        | base1 => con1
        | base2 => con1
        | arr => con2
      """);
  }

  @Test
  public void directedConditionsRecError() {
    typeCheckModule("""
      \\data D | con1 | con2 (i :+ DI) \\with { | dleft => con1 | dright => con1 }
      \\func rec {C :+ \\Cat} (c c' :+ C) (p :+ c ~> c') (d :+ D) : C \\elim d
        | con1 => c
        | con2 i => p d@ i
      """, 1);
  }

  @Test
  public void directedPathRecError() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1
      \\func rec {C :+ \\Cat} (c c' :+ C) (p :+ c ~> c') (d :+ D) : C \\elim d
        | con1 => c
        | con2 => p
      """, 1);
  }

  @Test
  public void invariantElimError() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1
      \\func f (d : D) : Nat
        | con1 => 0
        | con2 _ => 0
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void functionDIPatternError() {
    typeCheckModule("""
      \\func f (i :+ DI) : Nat
        | dleft => 0
        | dright => 0
      """, 2);
  }

  @Test
  public void directedTwoCellTest() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1 | sq : Path (\\lam _ => con1 ~> con1) con2 con2
      \\func f : Path (\\lam _ => con1 ~> con1) con2 con2 => sq
      \\func faceTest (i : DI) : sq left i = con2 i => idp
      \\func faceTest2 (i : DI) : sq right i = con2 i => idp
      \\func faceTest3 (i : I) : sq i dleft = con1 => idp
      """);
  }

  @Test
  public void directedTwoCellTest2() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1 | sq : DPath (\\lam _ => con1 ~> con1) con2 con2
      \\func f : DPath (\\lam _ => con1 ~> con1) con2 con2 => sq
      \\func faceTest (i : DI) : sq dleft i = con2 i => idp
      \\func faceTest2 (i : DI) : sq dright i = con2 i => idp
      \\func faceTest3 (i : DI) : sq i dleft = con1 => idp
      """);
  }

  @Test
  public void directedThreeCellTest() {
    typeCheckModule("""
      \\data D | con1 | con2 : con1 ~> con1 | sq : Path (\\lam _ => con1 ~> con1) con2 con2
        | cube : Path (\\lam _ => Path (\\lam _ => con1 ~> con1) con2 con2) sq sq
      \\func faceTest (j : I) (k : DI) : cube left j k = sq j k => idp
      \\func faceTest2 (i : I) (k : DI) : cube i right k = con2 k => idp
      \\func faceTest3 (i j : I) : cube i j dright = con1 => idp
      \\func rec {C : \\Cat} (c : C) (p : c ~> c) (s : Path (\\lam _ => c ~> c) p p) (t : Path (\\lam _ => Path (\\lam _ => c ~> c) p p) s s) (d :+ D) : C \\elim d
        | con1 => c
        | con2 => p
        | sq => s
        | cube => t
      """);
  }

  private static final String DI_HIT = """
      \\truncated \\data TrP (A : \\Type) : \\Prop | inP A
      \\data D | con1 | con2 (i :+ DI) \\with { | dleft => con1 | dright => con1 }
      """;

  private static final String PATH_HIT = """
      \\truncated \\data TrP (A : \\Type) : \\Prop | inP A
      \\data D | con1 | con2 : con1 ~> con1 | sq : Path (\\lam _ => con1 ~> con1) con2 con2
      """;

  private static final String TWO_POINTS_HIT = """
      \\truncated \\data TrP (A : \\Type) : \\Prop | inP A
      \\data D | con1 | con2 | con3 : con1 ~> con2
      """;

  @Test
  public void invariantPropElimTest() {
    typeCheckModule(DI_HIT + """
      \\lemma test (d : D) : TrP Nat \\elim d
        | con1 => inP 0
      """);
  }

  @Test
  public void invariantPropElimTest2() {
    typeCheckModule(PATH_HIT + """
      \\lemma test (d : D) : TrP Nat \\elim d
        | con1 => inP 0
      """);
  }

  @Test
  public void invariantPropCaseTest() {
    typeCheckModule(DI_HIT + """
      \\lemma test (d : D) : TrP Nat => \\case d \\with {
        | con1 => inP 0
      }
      """);
  }

  @Test
  public void invariantPropCaseTest2() {
    typeCheckModule(PATH_HIT + """
      \\lemma test (d : D) : TrP Nat => \\case d \\with {
        | con1 => inP 0
      }
      """);
  }

  @Test
  public void invariantPropNestedTest() {
    typeCheckModule(PATH_HIT + """
      \\data W | w (d : D)
      \\lemma test (v : W) : TrP Nat \\elim v
        | w con1 => inP 0
      """);
  }

  @Test
  public void invariantPropLevelTest() {
    typeCheckModule(PATH_HIT + """
      \\sfunc test {A : \\Type} (p : \\Pi (x y : A) -> x = y) (a : A) (d : D) : \\level A p \\elim d
        | con1 => a
      """);
  }

  @Test
  public void invariantPropTwoPointsTest() {
    typeCheckModule(TWO_POINTS_HIT + """
      \\lemma test (d : D) : TrP Nat \\elim d
        | con1 => inP 0
        | con2 => inP 1
      """);
  }

  @Test
  public void invariantPropTwoPointsError() {
    typeCheckModule(TWO_POINTS_HIT + """
      \\lemma test (d : D) : TrP Nat \\elim d
        | con1 => inP 0
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(MissingClausesError.class));
  }

  @Test
  public void invariantSetCaseError() {
    typeCheckModule(PATH_HIT + """
      \\func test (d : D) : Nat => \\case d \\with {
        | con1 => 0
        | con2 _ => 0
        | sq _ _ => 0
      }
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void invariantLevelFuncError() {
    typeCheckModule(PATH_HIT + """
      \\func test {A : \\Type} (p : \\Pi (x y : A) -> x = y) (a : A) (d : D) : \\level A p \\elim d
        | con1 => a
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(PropOnlyPatternError.class));
  }

  @Test
  public void directedPathRecursionTest() {
    typeCheckModule("""
      \\data S | base | tr {a : S} (p : a ~> a) : a ~> a
      \\func rec {B : \\Cat} (b : B) (s :+ S) : B \\elim s
        | base => b
        | tr p => dpath (\\lam k => rec b (p k))
      """);
  }

  @Test
  public void directedTruncRecursionTest() {
    typeCheckModule("""
      \\data S | base | loop : base ~> base | tr {a b : S} {p q : a ~> b} (e e' : p = q) : Path (\\lam _ => p = q) e e'
      \\func rec {B : \\Cat} (Bs : \\Pi (x y : B) (p q : x ~> y) (e e' : p = q) -> e = e') (b : B) (l : b ~> b) (s :+ S) : B \\elim s
        | base => b
        | loop => l
        | tr {a} {b'} {p} {q} e e' => Bs (rec Bs b l a) (rec Bs b l b') (dpath (\\lam k => rec Bs b l (p k))) (dpath (\\lam k => rec Bs b l (q k)))
                                         (path (\\lam j => dpath (\\lam k => rec Bs b l (e j k)))) (path (\\lam j => dpath (\\lam k => rec Bs b l (e' j k))))
      """);
  }
}
