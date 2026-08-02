package org.arend.cat;

import org.arend.core.definition.DataDefinition;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatDataTest extends TypeCheckingTestCase {
  @Test
  public void dataSortTest() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X :. \\Cat)
        | con (_ :. X)
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY, false), def.getSortExpression().withInfLevel());
  }

  @Test
  public void dataSortTest2() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X :. \\Cat)
        | con (_ : X)
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY, true), def.getSortExpression().withInfLevel());
  }

  @Test
  public void dataSortTest3() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X :. \\Cat)
        | con (_ :. X) (n : Nat)
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY, false), def.getSortExpression().withInfLevel());
  }

  @Test
  public void dataSortTest4() {
    DataDefinition def = (DataDefinition) typeCheckDef("""
      \\data Ob (X :. \\Cat)
        | con (_ : X) (n :. Nat)
      """);
    assertEquals(new Sort(Level.INFINITY, ConstLevel.INFINITY, true), def.getSortExpression().withInfLevel());
  }
}
