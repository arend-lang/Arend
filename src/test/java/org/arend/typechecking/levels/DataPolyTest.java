package org.arend.typechecking.levels;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.DataDefinition;
import org.arend.core.expr.DataCallExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.subst.LevelPair;
import org.arend.core.subst.Levels;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DataPolyTest extends TypeCheckingTestCase {
  @Test
  public void dataWithoutTypeOmega() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (n : Nat) | con1 (n = n) | con2 Nat");
    assertEquals(Sort.SET0, dataDefinition.getSort());
  }

  @Test
  public void dataWithoutTypeOmegaExplicit() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (n : Nat) : \\Set1 | con1 (n = n) | con2 Nat");
    assertEquals(Sort.SetOfLevel(1), dataDefinition.getSort());
  }

  @Test
  public void dataWithoutTypeOmegaExplicitError() {
    typeCheckDef("\\data D (n : Nat) : \\Set1 | con1 \\Set0 | con2 Nat", 1);
  }

  @Test
  public void dataWithTypeOmega() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) | con1 (n = n) | con2 Nat | con3 (D A n)");
    assertEquals(Sort.SET0, dataDefinition.getSort());
  }

  @Test
  public void dataWithTypeOmega2() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) : \\Set0 | con1 (n = n) | con2 Nat | con3 (D A n)");
    assertEquals(Sort.SET0, dataDefinition.getSort());
  }

  @Test
  public void dataWithTypeOmegaExplicit() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) : \\Set1 | con1 (n = n) | con2 Nat");
    assertEquals(Sort.SetOfLevel(1), dataDefinition.getSort());
  }

  @Test
  public void dataWithTypeOmegaExplicitSet() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) : \\Set0 | con1 (n = n) | con2 Nat");
    assertEquals(Sort.SET0, dataDefinition.getSort());
  }

  @Test
  public void dataWithTypeOmegaExplicitError() {
    typeCheckDef("\\data D (A : \\Type) (n : Nat) : \\Set1 | con1 (n = n) | con2 \\Set0", 1);
  }

  @Test
  public void dataOmega() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) | con A");
    assertEquals(Sort.STD, dataDefinition.getSort());
  }

  @Test
  public void dataOmegaExplicit() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) : \\Type | con A");
    assertEquals(Sort.STD, dataDefinition.getSort());
  }

  @Test
  public void dataOmegaProp() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) | con1 A | con2 (n = n)");
    assertEquals(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR, 0, 0)), dataDefinition.getSort());
  }

  @Test
  public void dataOmegaPropExplicit() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) : \\Type \\lp (\\max \\lh 0) | con1 (n = n) | con2 A");
    assertEquals(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR, 0, 0)), dataDefinition.getSort());
  }

  @Test
  public void dataOmegaSet() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) | con1 (n = n) | con2 A | con3 Nat");
    assertEquals(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR, 0, 0)), dataDefinition.getSort());
  }

  @Test
  public void dataProp() {
    typeCheckDef("\\data D : \\Prop | con1 | con2", 1);
  }

  @Test
  public void dataOmegaSetExplicit() {
    typeCheckDef("\\data D (A : \\Type) (n : Nat) : \\Type | con1 (n = n) | con2 A | con3", 1);
  }

  @Test
  public void dataOmegaSetExplicitMax() {
    DataDefinition dataDefinition = (DataDefinition) typeCheckDef("\\data D (A : \\Type) (n : Nat) : \\Type \\lp (\\max \\lh 0) | con1 (n = n) | con2 A | con3 Nat");
    assertEquals(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR, 0, 0)), dataDefinition.getSort());
  }

  @Test
  public void recursiveData() {
    Constructor constructor = ((DataDefinition) typeCheckDef("\\data D | con D")).getConstructors().getFirst();
    assertEquals(Levels.EMPTY, ((DataCallExpression) constructor.getParameters().getTypeExpr()).getLevels());
  }

  @Test
  public void recursiveData2() {
    Constructor constructor = ((DataDefinition) typeCheckDef("\\data D (A : \\Type) | con (D A)")).getConstructors().getFirst();
    assertEquals(LevelPair.STD, ((DataCallExpression) constructor.getParameters().getTypeExpr()).getLevels());
  }

  @Test
  public void recursiveDataError() {
    typeCheckDef("\\data D | con (D \\levels 0 \\lh)", 1);
  }

  @Test
  public void recursiveDataWithClass() {
    typeCheckModule(
      "\\record R (A : \\Type)\n" +
      "\\data D | con (R D) | con2");
  }

  @Test
  public void recursiveDataWithLam() {
    typeCheckModule(
      "\\record R (A : Nat -> \\Type)\n" +
      "\\data D | con (R (\\lam _ => D)) | con2");
  }

  @Test
  public void recursiveDataWithTuple() {
    typeCheckModule(
      "\\record R (A : \\Sigma \\Type \\Type)\n" +
      "\\data D | con (R (\\Sigma,D)) | con2 (R (D,\\Sigma))");
  }

  @Test
  public void recursiveDataWithConstructor() {
    typeCheckModule("""
      \\data Data | cons \\Type \\Type
      \\record R (A : Data)
      \\data D | con1 (R (cons (\\Sigma) D)) | con2 (R (cons D (\\Sigma)))
      """);
  }

  @Test
  public void recursiveDataWithNew() {
    typeCheckModule("""
      \\record C (X Y : \\Type)
      \\record R (A : C)
      \\data D | con1 (R (\\new C (\\Sigma) D)) | con2 (R (\\new C D (\\Sigma)))
      """);
  }

  @Test
  public void levelTest() {
    DataDefinition definition = (DataDefinition) typeCheckDef("\\data D (A1 : \\Set1) (A2 : \\Set2) | con (A2 -> D A1 A2) A1");
    assertEquals(Sort.SetOfLevel(2), definition.getSort());
  }

  @Test
  public void propTest() {
    DataDefinition definition = (DataDefinition) typeCheckDef("\\data D (A1 : \\Prop) (A2 : \\Set2) | con (A2 -> D A1 A2) A1");
    assertEquals(Sort.PROP, definition.getSort());
  }

  @Test
  public void setTest() {
    DataDefinition definition = (DataDefinition) typeCheckDef("\\data D (A1 : \\Prop) (A2 : \\Set2) | con1 (A2 -> D A1 A2) A1 | con2");
    assertEquals(Sort.SetOfLevel(2), definition.getSort());
  }

  @Test
  public void setTest2() {
    DataDefinition definition = (DataDefinition) typeCheckDef("\\data D (A1 : \\Prop) (A2 : \\Set2) | con1 {d1 d2 : D A1 A2} (A2 -> d1 = d2) A1 | con2");
    assertEquals(Sort.SET0, definition.getSort());
  }
}
