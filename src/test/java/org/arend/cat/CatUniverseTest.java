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
    typeCheckDef("""
      \\func test {C : \\Cat} (c : C) => c
      """);
  }

  @Test
  public void idpTest() {
    typeCheckDef("""
      \\func test {C : \\Cat} {c : C} : c = c => idp
      """);
  }
}
