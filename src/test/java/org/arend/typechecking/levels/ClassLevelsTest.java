package org.arend.typechecking.levels;

import org.arend.Matchers;
import org.arend.core.definition.ClassDefinition;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.subst.Levels;
import org.arend.core.subst.ListLevels;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.local.SuperLevelsMismatchError;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ClassLevelsTest extends TypeCheckingTestCase {
  @Test
  public void superTest() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S.{u} \\extends R.{\\suc u}
        \\func test.{u} (s : S.{u}) : \\Type (\\suc u) => s.A
        """);
    ClassDefinition def = (ClassDefinition) getDefinition("S");
    assertEquals(new Sort(new Level(def.getLevelParameters().getFirst(), 2), ConstLevel.INFINITY), def.getSort());
  }

  @Test
  public void superError() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S.{u} \\extends R.{\\suc u}
        \\func test.{u} (s : S.{u}) : \\Type u => s.A
        """, 1);
  }

  @Test
  public void multiTest() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record R'.{u} (B : \\Type u)
        \\record S.{u} \\extends R.{\\suc u}, R'.{u}
        \\func test.{u} (s : S.{u}) : \\Type u => s.B
        """);
  }

  @Test
  public void transitiveTest() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S.{u} \\extends R.{\\suc u}
        \\record T \\extends S
        \\func test.{u} (t : T.{u}) : \\Type (\\suc u) => t.A
        """);
  }

  @Test
  public void transitiveError() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S.{u} \\extends R.{\\suc u}
        \\record T \\extends S
        \\func test.{u} (t : T.{u}) : \\Type u => t.A
        """, 1);
  }

  @Test
  public void transitiveTest2() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S.{u} (B : \\Type u) \\extends R.{\\suc u}
        \\record T \\extends S
        \\func test.{u} (t : T.{u}) : \\Type u => t.B
        """);
  }

  @Test
  public void doubleTransitiveTest() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S.{u} \\extends R.{\\suc u}
        \\record T.{u} \\extends S.{\\suc u}
        \\func test.{u} (t : T.{u}) : \\Type (\\suc (\\suc u)) => t.A
        """);
  }

  @Test
  public void doubleTransitiveError() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S.{u} \\extends R.{\\suc u}
        \\record T.{u} \\extends S.{\\suc u}
        \\func test.{u} (t : T.{u}) : \\Type (\\suc u) => t.A
        """, 1);
  }

  @Test
  public void diamondTest() {
    typeCheckModule(
      """
        \\record Base.{u} (A : \\Type u)
        \\record R.{u} \\extends Base.{\\suc u}
        \\record S.{u} \\extends Base.{\\suc u}
        \\record T.{u} \\extends R.{u}, S.{u}
        """);
    ClassDefinition def = (ClassDefinition) getDefinition("T");
    Levels levels = def.getSuperLevels().get((ClassDefinition) getDefinition("Base"));
    assertEquals(new ListLevels(new Level(def.getLevelParameters().getFirst(), 1)), levels);
  }

  @Test
  public void diamondTest2() {
    typeCheckModule(
      """
        \\record Base.{u} (A : \\Type u)
        \\record R.{u} \\extends Base.{\\suc u}
        \\record S \\extends Base
        \\record T.{u} \\extends R.{u}, S.{\\suc u}
        """);
    ClassDefinition def = (ClassDefinition) getDefinition("T");
    Levels levels = def.getSuperLevels().get((ClassDefinition) getDefinition("Base"));
    assertEquals(new ListLevels(new Level(def.getLevelParameters().getFirst(), 1)), levels);
  }

  @Test
  public void diamondError() {
    typeCheckModule(
      """
        \\record Base.{u} (A : \\Type u)
        \\record R.{u} \\extends Base.{\\suc u}
        \\record S \\extends Base
        \\record T.{u} \\extends R.{u}, S.{u}
        """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(SuperLevelsMismatchError.class));
  }

  @Test
  public void diamondError2() {
    typeCheckModule(
      """
        \\record Base.{u} (A : \\Type u)
        \\record R.{u} \\extends Base.{\\suc (\\suc u)}
        \\record S.{u} \\extends Base.{\\suc u}
        \\record T.{u} \\extends R.{u}, S.{u}
        """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(SuperLevelsMismatchError.class));
  }

  @Test
  public void extendsTest2() {
    typeCheckModule(
      """
        \\record R.{p2,p1}
        \\record S.{p4,p3} \\extends R.{p4,p3}
          | A : \\Type p4
        \\record T.{p7,p6,p5} \\extends R.{p7,p6}
        \\record X.{u} \\extends S.{u,u}, T.{u,u,u}
          | B : \\Type u
        """);
    assertEquals(1, getDefinition("X").getLevelParameters().size());
  }

  @Test
  public void lpTest() {
    typeCheckModule(
      "\\record R.{p1,p2}\n" +
      "\\record S.{u} \\extends R.{u,u}");
    assertEquals(1, getDefinition("S").getLevelParameters().size());
  }

  @Test
  public void lpTest2() {
    typeCheckModule(
      "\\record R.{p1,p2}\n" +
      "\\record S.{u} \\extends R.{u, \\suc u}");
  }

  @Test
  public void extendsTest5() {
    typeCheckModule(
      """
        \\record R
        \\record S \\extends R
        \\record T.{p1,p2} \\extends S
        """);
  }

  @Test
  public void extendsTest6() {
    typeCheckModule(
      """
        \\record R.{u} (A : \\Type u)
        \\record S \\extends R
        \\record T.{p1,p2} \\extends S.{p1}
        """);
    ClassDefinition tClass = ((ClassDefinition) getDefinition("T"));
    assertEquals(new ListLevels(new Level(tClass.getLevelParameters().getFirst())), tClass.getSuperLevels().get((ClassDefinition) getDefinition("S")));
    assertEquals(new ListLevels(new Level(tClass.getLevelParameters().getFirst())), tClass.getSuperLevels().get((ClassDefinition) getDefinition("R")));
  }

  @Test
  public void extendsResolveTest() {
    typeCheckModule(
      """
        \\record R.{p1,p2}
        \\record S.{u} (A : \\Type u)
        \\record T.{u} \\extends R.{u,u}, S.{u}
        """);
  }

  @Test
  public void extendsResolveError() {
    resolveNamesModule(
      """
        \\record R.{p1,p2}
        \\record S
        \\record T \\extends R, S.{p3}
        """, 1);
    assertThatErrorsAre(Matchers.notInScope("p3"));
  }

  @Test
  public void derivedLevels() {
    typeCheckModule(
      "\\record R.{p1,p2}\n" +
      "\\record S \\extends R");
    assertNull(((ClassDefinition) getDefinition("S")).getSuperLevels().get((ClassDefinition) getDefinition("R")));
  }

  @Test
  public void subclassTest() {
    typeCheckModule(
      """
        \\record A (X : \\Set)
        \\record B (Y : \\Type)
        \\record C \\extends A, B
        \\func test : A => \\new C Nat Nat
        """);
  }
}
