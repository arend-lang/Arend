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
    typeCheckDef("\\func test.{u} : \\Cat (\\suc u) => \\Cat u");
  }

  @Test
  public void catUniverseError() {
    typeCheckDef("\\func test.{u} : \\Type (\\suc u) => \\Cat u", 1);
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
}
