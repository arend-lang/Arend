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
  public void setCatSubtypeError() {
    typeCheckDef("\\func test.{u} (A : \\Set u) : \\Cat u => A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void propCatSubtypeError() {
    typeCheckDef("\\func test.{u} (A : \\Prop) : \\Cat u => A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void natCatError() {
    typeCheckDef("\\func test : \\Cat 0 => Nat", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
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
  public void dataCovariantTypeFieldError() {
    typeCheckDef("\\data D.{u} (A : \\Type u) : \\Cat u | d (a :+ A)", 1);
  }

  @Test
  public void dataMixedFields() {
    typeCheckDef("\\data D.{u} (A : \\Type u) (C : \\Cat u) : \\Cat u | d (a :+ A) (c :+ C)", 1);
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
}
