package org.arend.cat;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.local.CertainTypecheckingError;
import org.junit.Test;

public class CatUniverseTest extends TypeCheckingTestCase {
  @Test
  public void idTest() {
    typeCheckDef("""
      \\func test {C : \\Cat} (c : C) => c
      """);
  }

  @Test
  public void dataTest() {
    typeCheckDef("""
      \\data D {C : \\Cat} (c : C)
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(CertainTypecheckingError.Kind.CAT_SORT_NOT_ALLOWED));
  }

  @Test
  public void recordTest() {
    typeCheckDef("""
      \\record R (C : \\Cat) (n : Nat)
      """);
  }

  @Test
  public void recordTest2() {
    typeCheckDef("""
      \\record R (C : \\Cat) (c : C)
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(CertainTypecheckingError.Kind.CAT_SORT_NOT_ALLOWED));
  }
}
