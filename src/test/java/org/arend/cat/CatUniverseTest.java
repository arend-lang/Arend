package org.arend.cat;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class CatUniverseTest extends TypeCheckingTestCase {
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
