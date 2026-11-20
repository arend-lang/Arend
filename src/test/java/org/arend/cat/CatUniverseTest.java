package org.arend.cat;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class CatUniverseTest extends TypeCheckingTestCase {
  @Test
  public void subType() {
    typeCheckDef("\\func test (A : \\Type) : \\Cat => A");
  }

  @Test
  public void subType2() {
    typeCheckDef("\\func test (A : \\Cat) : \\Type => A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void idTest() {
    typeCheckDef("\\func test {C : \\Cat} (c : C) => c");
  }

  @Test
  public void setUniverseTest() {
    typeCheckDef("\\func test.{u} : \\1-Type (\\suc u) => \\Set u");
  }

  @Test
  public void typeUniverseTest() {
    typeCheckDef("\\func test.{u} : \\Cat (\\suc u) => \\Type u");
  }

  @Test
  public void typeUniverseError() {
    typeCheckDef("\\func test.{u} : \\Type (\\suc u) => \\Type u", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void catUniverseTest() {
    typeCheckDef("\\func test.{u} : \\Type (\\suc u) => \\Cat u");
  }

  @Test
  public void catUniverseError() {
    typeCheckDef("\\func test.{u} : \\Cat (\\suc u) => \\Cat u", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void sigmaCatUniverseError() {
    typeCheckDef("\\func test => \\Sigma \\Cat (\\Sigma)", 1);
  }

  @Test
  public void sigmaTypeUniverseError() {
   typeCheckDef("\\func test => \\Sigma \\Type (\\Sigma)", 1);
  }

  @Test
  public void sigmaSetUniverseError() {
    typeCheckDef("\\func test => \\Sigma \\Set (\\Sigma)", 1);
  }

  @Test
  public void catSubtypeTest() {
    typeCheckDef("\\func test.{u} (C : \\Cat u) : \\Cat (\\suc u) => C");
  }

  @Test
  public void typeSubtypeTest() {
    typeCheckDef("\\func test.{u} (A : \\Type u) : \\Type (\\suc u) => A");
  }

  @Test
  public void typeCatSubtypeError() {
    typeCheckDef("\\func test.{u} (A : \\Type u) : \\Cat u => A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void typeSucCatSubtypeError() {
    typeCheckDef("\\func test.{u} (A : \\Type u) : \\Cat (\\suc u) => A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void setCatSubtypeTest() {
    typeCheckDef("\\func test.{u} (A : \\Set u) : \\Cat u => A");
  }

  @Test
  public void propCatSubtypeTest() {
    typeCheckDef("\\func test.{u} (A : \\Prop) : \\Cat u => A");
  }

  @Test
  public void natCatTest() {
    typeCheckDef("\\func test : \\Cat 0 => Nat");
  }

  @Test
  public void sigmaCatSubtypeError() {
    typeCheckDef("\\func test.{u} (A : \\Type u) : \\Cat u => \\Sigma (a b : A)", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void typeInfCatSubtypeTest() {
    typeCheckDef("\\func test.{u} (A : \\Type u) : \\Cat => A");
  }

  @Test
  public void catUniverseTypeTest() {
    typeCheckDef("\\func test.{u} : \\Type (\\suc u) => \\Sigma (C : \\Cat u) C");
  }

  @Test
  public void typeCatEqualityError() {
    typeCheckModule("""
      \\func f.{u} (X : \\Type (\\suc u)) (x y : X) => 0
      \\func test.{u} => f.{u} _ (\\Type u) (\\Cat u)
      """, 1);
  }

  @Test
  public void dataInvariantTypeField() {
    typeCheckModule("""
      \\data ToCat.{u} (A : \\Type u) : \\Cat u
        | toCat A
      \\func test.{u} (A : \\Type u) : \\Cat u => ToCat A
      \\func untoCat.{u} {A : \\Type u} (x : ToCat A) : A \\elim x
        | toCat a => a
      """);
  }

  @Test
  public void dataInvariantCatField() {
    typeCheckDef("\\data D.{u} (C : \\Cat u) : \\Cat u | d C");
  }

  @Test
  public void dataCovariantCatField() {
    typeCheckDef("\\data D.{u} (C : \\Cat u) : \\Cat u | d (c :+ C)");
  }

  @Test
  public void dataCovariantTypeField() {
    typeCheckDef("\\data D.{u} (A : \\Type u) : \\Cat u | d (a :+ A)");
  }

  @Test
  public void dataMixedFields() {
    typeCheckDef("\\data D.{u} (A : \\Type u) (C : \\Cat u) : \\Cat u | d (a :+ A) (c :+ C)");
  }

  @Test
  public void dataEmptyCat() {
    typeCheckDef("\\data D : \\Cat0");
  }

  @Test
  public void dataBoolCat() {
    typeCheckDef("\\data D : \\Cat0 | t | f");
  }

  @Test
  public void dataInferredSortError() {
    typeCheckModule("""
      \\data D.{u} (A : \\Type u) | d A
      \\func test.{u} (A : \\Type u) : \\Cat u => D A
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void sigmaTypeCatTest() {
    typeCheckModule("""
      \\func test1.{u} (X : \\Type u) (D : \\Cat u) : \\Cat u => \\Sigma+ X D
      \\func test2.{u} (X : \\Type u) (D : \\Cat u) : \\Cat u => \\Sigma+ D X
      \\func test3.{u} (D : \\Cat u) (F : D ->+ \\Type u) : \\Cat u => \\Sigma+ (d : D) (F d)
      """);
  }

  // \lam (A :+ \Type u) => \Sigma+ DI A would be a covariant map \Type u ->+ \Cat u
  @Test
  public void sigmaCovariantTypeError() {
    typeCheckDef("\\func test.{u} (A :+ \\Type u) : \\Cat u => \\Sigma+ DI A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void sigmaCovariantTypeLargeTest() {
    typeCheckDef("\\func test.{u} (A :+ \\Type u) : \\Cat => \\Sigma+ DI A");
  }

  @Test
  public void sigmaCovariantTypeSmallTypeTest() {
    typeCheckDef("\\func test.{u} (A :+ \\Type u) : \\Type u => \\Sigma+ Nat A");
  }

  @Test
  public void sigmaCovariantCatTest() {
    typeCheckDef("\\func test.{u} {T : \\Cat u} (F : T ->+ \\Cat u) (t :+ T) : \\Cat u => \\Sigma+ DI (F t)");
  }

  @Test
  public void sigmaCovariantSetTest() {
    typeCheckDef("\\func test.{u} {T : \\Cat u} (F : T ->+ \\Set u) (t :+ T) : \\Cat u => \\Sigma+ DI (F t)");
  }

  @Test
  public void lamCovariantTypeError() {
    typeCheckDef("\\func test.{u} {Q T : \\Cat u} (R : T ->+ Q ->+ \\Type u) : T ->+ \\Cat u => \\lam (t :+ T) => \\Sigma+ (k : Q) (R t k)", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  // The inner \Sigma-type depends on t, which is bound by the outer one
  @Test
  public void sigmaReleasedCovariantTypeTest() {
    typeCheckDef("\\func test.{u} {Q T : \\Cat u} (R : T ->+ Q ->+ \\Type u) : \\Cat u => \\Sigma+ (t : T) (\\Sigma+ (k : Q) (R t k))");
  }

  @Test
  public void sigmaFamilyCovariantTypeTest() {
    typeCheckModule("""
      \\func fam.{u} {Q T : \\Cat u} (R : T ->+ Q ->+ \\Type u) (t :+ T) : \\Cat => \\Sigma+ (k : Q) (R t k)
      \\func test.{u} {Q T : \\Cat u} (R : T ->+ Q ->+ \\Type u) : \\Cat => \\Sigma+ (t : T) (fam R t)
      \\func test2.{u} {Q T : \\Cat u} (R : T ->+ Q ->+ \\Type u) : \\Cat u => \\Sigma+ (t : T) (fam R t)
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void dataCovariantParameterError() {
    typeCheckDef("\\data D.{u} {T : \\Cat u} (G : T ->+ \\Type u) (t :+ T) : \\Cat u | d (x :+ G t)", 1);
  }

  @Test
  public void dataCovariantParameterInferredError() {
    typeCheckDef("\\data D.{u} {T : \\Cat u} (G : T ->+ \\Type u) (t :+ T) | d (x :+ G t) (s :+ T)", 1);
  }

  @Test
  public void dataCovariantParameterTypeTest() {
    typeCheckDef("\\data D.{u} {T : \\Cat u} (G : T ->+ \\Type u) (t :+ T) : \\Type u | d (x :+ G t)");
  }
}
