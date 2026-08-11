package org.arend.typechecking.levels;

import org.arend.Matchers;
import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.*;
import org.arend.core.expr.*;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.subst.ListLevels;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.*;

public class LevelParametersTest extends TypeCheckingTestCase {
  @Test
  public void levelsTest() {
    typeCheckDef("\\func test.{p1,p2} (A : \\Type p2) (B : \\Type p1) => A");
  }

  @Test
  public void levelsError() {
    typeCheckDef("\\func test.{p2,p1} (A : \\Type p2) : \\Type p1 => A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void resolveError() {
    resolveNamesDef("\\func test.{p1,p2} => \\Type p3", 1);
    assertThatErrorsAre(Matchers.notInScope("p3"));
  }

  @Test
  public void lpInferTest() {
    typeCheckDef("\\func test.{p2,p1} (A : \\Type) : \\Type p2 => A", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }

  @Test
  public void noPLevelsTest2() {
    typeCheckDef("\\func test (A : \\Type) : \\Type => A");
  }

  @Test
  public void maxLevelTest() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("\\func test.{p1,p2} (A : \\Type p1) (B : \\Type p2) => A -> B");
    assertEquals(new Sort(new Level(Map.of(def.getLevelParameters().get(0), BigInteger.ZERO, def.getLevelParameters().get(1), BigInteger.ZERO), BigInteger.ZERO), ConstLevel.INFINITY), def.getResultType().toSort());
  }

  @Test
  public void applyLevels() {
    typeCheckModule(
      "\\func f.{p1,p2} (A : \\Type) => A\n" +
      "\\func test.{p2,p1} => f.{p2,p1} Nat");
  }

  @Test
  public void applyLevels2() {
    typeCheckModule(
      "\\func f.{p1,p2} (A : \\Type) => A\n" +
      "\\func test => f.{7,3} Nat");
  }

  @Test
  public void useTest() {
    typeCheckModule(
      "\\data D.{p2,p1} (A : \\Type p2) | con Nat\n" +
      "  \\where \\use \\coerce test.{p4,p3} (A : \\Type p4) (n : Nat) : D A => con n", 2);
    assertThatErrorsAre(Matchers.warning(), Matchers.warning());
    assertEquals(2, getDefinition("D.test").getLevelParameters().size());
  }

  @Test
  public void useTest2() {
    typeCheckModule(
      "\\data D.{p2,p1} (A : \\Type p2) | con Nat\n" +
      "  \\where \\use \\coerce test.{p2,p1} (A : \\Type p2) (n : Nat) : D A => con n", 2);
    assertThatErrorsAre(Matchers.warning(), Matchers.warning());
    assertEquals(2, getDefinition("D.test").getLevelParameters().size());
  }

  @Test
  public void useError() {
    resolveNamesModule(
      "\\data D.{p1,p2} (A : \\Type p2) | con Nat\n" +
      "  \\where \\use \\coerce test {A : \\Type p1} (n : Nat) : D A => con n", 1);
    assertThatErrorsAre(Matchers.error());
  }

  @Test
  public void useDerived() {
    typeCheckModule(
      """
        \\record R.{p1,p2}
        \\data D.{p1,p2} (r : R.{p1,p2}) | con Nat
          \\where \\use \\coerce test (n : Nat) => con n
        """, 2);
    assertThatErrorsAre(Matchers.warning(), Matchers.warning());
  }

  @Ignore
  @Test
  public void useDerivedError() {
    typeCheckModule(
      """
        \\record R.{p1 <= p2}
        \\data D (r : R) | con Nat
          \\where \\use \\coerce test.{p1 >= p2} (n : Nat) => con n
        """, 1);
  }

  @Ignore
  @Test
  public void useDerivedError2() {
    typeCheckModule(
      """
        \\record R.{p1 <= p2}
        \\data D (r : R) | con Nat
          \\where \\use \\coerce test.{p1 >= p2} (r : R) (n : Nat) => con n
        """, 1);
  }

  @Test
  public void defaultTest() {
    typeCheckModule(
      """
        \\record R.{p1,p2,p3}
          | f : Nat
        \\record S \\extends R {
          \\default f : Nat => 0
        }
        """);
    assertEquals(3, getDefinition("R").getLevelParameters().size());
    assertEquals(3, getDefinition("S").getLevelParameters().size());
    assertEquals(3, getDefinition("S.f").getLevelParameters().size());
    ClassDefinition classDef = (ClassDefinition) getDefinition("S");
    Expression impl = Objects.requireNonNull(classDef.getDefault((ClassField) getDefinition("R.f"))).getExpression();
    List<? extends Level> levels = ((FunCallExpression) impl).getLevels().toList();
    List<? extends LevelVariable> params = classDef.getLevelParameters();
    assertEquals(Arrays.asList(new Level(params.get(0)), new Level(params.get(1)), new Level(params.get(2))), levels);
  }

  @Ignore
  @Test
  public void defaultTest2() {
    typeCheckModule(
      """
        \\record R.{p1,p2}
          | f : \\Type (\\suc p2)
        \\record S \\extends R {
          \\default f.{p1,p2} : \\Type (\\suc p2) => \\Type p1
        }
        """);
    assertEquals(3, getDefinition("S.f").getLevelParameters().size());
    ClassDefinition classDef = (ClassDefinition) getDefinition("S");
    Expression impl = Objects.requireNonNull(classDef.getDefault((ClassField) getDefinition("R.f"))).getExpression();
    List<? extends Level> levels = ((FunCallExpression) impl).getLevels().toList();
    List<? extends LevelVariable> params = classDef.getLevelParameters();
    assertEquals(Arrays.asList(new Level(params.get(0)), new Level(params.get(1)), new Level(params.get(2))), levels);
  }

  @Test
  public void coclauseTest() {
    typeCheckModule(
      """
        \\record R (A : \\Type)
        \\func g.{p1,p2} : R \\cowith
          | A : \\Type => \\Sigma
        """);
    assertEquals(2, getDefinition("g.A").getLevelParameters().size());
    Expression impl = ((ClassCallExpression) ((FunctionDefinition) getDefinition("g")).getResultType()).getAbsImplementationHere((ClassField) getDefinition("R.A"));
    assertNotNull(impl);
    List<? extends Level> levels = ((FunCallExpression) impl).getLevels().toList();
    List<? extends LevelVariable> params = getDefinition("g").getLevelParameters();
    assertEquals(Arrays.asList(new Level(params.get(0)), new Level(params.get(1))), levels);
  }

  @Test
  public void coclauseTest2() {
    typeCheckModule(
      """
        \\record R.{p1,p2} (x : \\Type (\\max (\\suc p1) p2))
        \\func g.{p1,p2} : R.{p1,p2} \\cowith
          | x : \\Type (\\suc p1) => \\let t => \\Type p2 \\in \\Type p1
        """);
  }

  @Test
  public void metaTest() {
    typeCheckModule(
      "\\meta m.{p2,p1} => \\Sigma (\\Type p1) (\\Type p2)\n" +
      "\\func f => m.{2,1}");
    DependentLink params = ((SigmaExpression) Objects.requireNonNull(((FunctionDefinition) getDefinition("f")).getBody())).getParameters();
    assertEquals(new UniverseExpression(Sort.TypeOfLevel(1)), params.getType());
    assertEquals(new UniverseExpression(Sort.TypeOfLevel(2)), params.getNext().getType());
  }

  @Test
  public void metaTest2() {
    typeCheckModule("""
      \\meta T.{p} => \\Type p
      \\func test => T
      """, 1);
    assertThatErrorsAre(Matchers.warning());
  }

  @Test
  public void dynamicTest() {
    typeCheckModule(
      """
        \\record R.{p1,p2,p3} {
          \\func f => 0
        }
        """);
    FunctionDefinition def = (FunctionDefinition) getDefinition("R.f");
    List<? extends LevelVariable> levels = def.getLevelParameters();
    assertEquals(3, levels.size());
    assertEquals(List.of(new Level(levels.get(0)), new Level(levels.get(1)), new Level(levels.get(2))), ((ClassCallExpression) def.getParameters().getType()).getLevels().toList());
  }

  @Test
  public void dynamicTest2() {
    typeCheckModule(
      """
        \\record S.{p1,p2,p3}
        \\record R \\extends S {
          \\func f => 0
        }
        """);
    FunctionDefinition def = (FunctionDefinition) getDefinition("R.f");
    List<? extends LevelVariable> levels = def.getLevelParameters();
    assertEquals(3, levels.size());
    assertEquals(List.of(new Level(levels.get(0)), new Level(levels.get(1)), new Level(levels.get(2))), ((ClassCallExpression) def.getParameters().getType()).getLevels().toList());
  }

  @Test
  public void levelsNotErased() {
    Definition def = typeCheckDef("\\record C.{lp} (A : \\Type)");
    assertEquals(1, def.getLevelParameters().size());
  }

  @Test
  public void dynamicClassTest() {
    typeCheckModule("""
      \\record T.{q} (B : \\Type q)
      \\record R.{p} (A : \\Type p) {
        \\record S.{p} \\extends T.{p}
      }
      """);
  }

  @Test
  public void enclosingClassTest() {
    typeCheckModule("""
      \\record R.{a,b} (A : \\Type a) (B : \\Type b) {
        \\func test.{c,d} (C : \\Type c) (D : \\Type d) => 0
      }
      """);
    Definition def = getDefinition("R.test");
    assertEquals(4, def.getLevelParameters().size());
    assertEquals(new ListLevels(Arrays.asList(new Level(def.getLevelParameters().get(0)), new Level(def.getLevelParameters().get(1)))), ((ClassCallExpression) def.getParameters().getType()).getLevels());
    assertEquals(new UniverseExpression(new Sort(new Level(def.getLevelParameters().get(2)), ConstLevel.INFINITY)), def.getParameters().getNext().getType());
    assertEquals(new UniverseExpression(new Sort(new Level(def.getLevelParameters().get(3)), ConstLevel.INFINITY)), def.getParameters().getNext().getNext().getType());
  }
}
