package org.arend.cat;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

/**
 * Path types that depend on the categorical context are forced to the infinite level,
 * but once the covariant variables they depend on are bound by \Pi-types, these \Pi-types can be small.
 */
public class CatForcedPathTest extends TypeCheckingTestCase {
  @Test
  public void piTest() {
    typeCheckDef("\\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : \\Type u => \\Pi (x :+ C) -> a x = b x");
  }

  @Test
  public void piSeveralParamsTest() {
    typeCheckDef("\\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : \\Type u => \\Pi (x y :+ C) -> a x = b y");
  }

  @Test
  public void piNestedTest() {
    typeCheckDef("\\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : \\Type u => \\Pi (x :+ C) -> \\Pi (y :+ C) -> a x = b y");
  }

  @Test
  public void piDataTest() {
    typeCheckModule("""
      \\data W {C D : \\Cat} (a b : C ->+ D)
        | w (\\Pi (x :+ C) -> a x = b x)
      \\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : \\Type u => W a b
      """);
  }

  @Test
  public void piInvariantTest() {
    typeCheckDef("\\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : \\Type u => \\Pi (x : C) -> a x = b x");
  }

  @Test
  public void familyTest() {
    typeCheckDef("\\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : C ->+ \\Type u => \\lam (x :+ C) => a x = b x", 1);
  }

  @Test
  public void piOuterCategoricalTest() {
    typeCheckDef("\\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : C ->+ \\Type u => \\lam (y :+ C) => \\Pi (x :+ C) -> a y = b x", 1);
  }

  @Test
  public void piDataOuterCategoricalTest() {
    typeCheckModule("""
      \\data W {C D : \\Cat} (a b : C ->+ D) (y : C)
        | w (\\Pi (x :+ C) -> a y = b x)
      \\func test.{u} {C D : \\Cat u} (a b : C ->+ D) : C ->+ \\Type u => \\lam (y :+ C) => W a b y
      """, 1);
  }

  // The path type in the type of c is still forced, so it cannot be used to build a family.
  @Test
  public void inferenceLeakTest() {
    typeCheckModule("""
      \\func f.{u} {X : \\Cat u} {B : X ->+ \\Type u} (s : \\Pi (x :+ X) -> B x) : X ->+ \\Type u => B
      \\func test.{u} {X D : \\Cat u} (v w : X ->+ D) (c : \\Pi (x :+ X) -> v x = w x) : X ->+ \\Type u => f c
      """, 2);
  }

  // The level of a function without a result type is computed through projections of its parameters.
  @Test
  public void resultTypeProjectionTest() {
    typeCheckModule("""
      \\class Fam {
        | J : \\Type
        | X : J -> \\Cat
      }
      \\data W {F : Fam} (j : F.J)
        | w (x :+ F.X j)
      \\func hat {F : Fam} => \\new Fam F.J (\\lam j => W j)
      \\data L {F : Fam}
        | l (j : F.J) (a b : F.X j ->+ F.X j) (\\Pi (y :+ F.X j) -> a y = b y)
      \\type T {F : Fam} => L {hat {F}}
      \\func test.{u} {F : Fam.{u,u}} : \\Cat u => T {F}
      """);
  }

  // The type family of dpath is inferred later than the forcedness of the path is decided.
  @Test
  public void dpathInferredFamilyTest() {
    typeCheckModule("""
      \\func comp {C :+ \\Cat} {a b c :+ C} (f :+ a ~> b) (g :+ b ~> c) : a ~> c => dpath (\\lam i => fill2 f g i dright)
      \\data E {C :+ \\Cat} {a c :+ C} (x y :+ a ~> c) | e
      \\sfunc g {C :+ \\Cat} {a b c :+ C} {ab :+ a ~> b} {bc :+ b ~> c} {ac :+ a ~> c} (p :+ DPath (\\lam i => ab i ~> ac i) idd bc) : E (comp ab bc) ac => e
      \\sfunc test {C :+ \\Cat} {a b :+ C} {f :+ a ~> b} : E (comp f idd) f => g (dpath \\lam i => idd)
      """);
  }

  // A partially applied path type is eta-expanded over a covariant parameter.
  @Test
  public void etaExpandedFamilyTest() {
    typeCheckDef("\\func test.{u} {C : \\Cat u} (a : C) : C ->+ \\Type u => DPath (\\lam _ => C) a", 1);
  }

  @Test
  public void etaExpandedFamilyInfiniteTest() {
    typeCheckDef("\\func test {C : \\Cat} (a : C) : C ->+ \\Type => DPath (\\lam _ => C) a");
  }

  // The hole is solved with a term depending on y only after the sort of the \\Pi-type is computed.
  @Test
  public void inferenceReleaseTest() {
    typeCheckModule("""
      \\func k.{u} (T :+ \\Type u) (t :+ T) : \\Type u => T
      \\func test.{u} {C D : \\Cat u} (a b : C ->+ D) (c : \\Pi (y x :+ C) -> a x = b y) : C ->+ \\Type u
        => \\lam (y :+ C) => k.{u} (\\Pi (x :+ C) -> a x = _) (c y)
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }
}
