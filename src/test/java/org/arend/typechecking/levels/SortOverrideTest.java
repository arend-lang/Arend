package org.arend.typechecking.levels;

import org.arend.Matchers;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.Expression;
import org.arend.core.expr.UniverseExpression;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.ext.core.level.ConstLevel;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class SortOverrideTest extends TypeCheckingTestCase {
  @Test
  public void overrideTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test : \\Type4 => R.{3}
      """);
    assertEquals(new UniverseExpression(new Sort(new Level(BigInteger.valueOf(4)), ConstLevel.INFINITY)), ((Expression) Objects.requireNonNull(((FunctionDefinition) getDefinition("test")).getBody())).getType());
  }

  @Test
  public void overrideError() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test : \\Type3 => R.{3}
      """, 1);
  }

  @Test
  public void overrideSeveral() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test : \\Type4 => R.{3,2}
      """);
  }

  @Test
  public void overrideSeveral2() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test : \\Type4 => R.{2,3}
      """);
  }

  @Test
  public void overrideSeveral3() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A) (B : A -> \\Type)
      \\func test : \\Type4 => R.{2,3}
      """);
  }

  @Test
  public void overrideTooMany() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test => R.{3,2}
      """, 1);
  }

  @Test
  public void overrideType() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test : R.{2,3} => \\new R \\Set1 \\Set2 Nat Nat
      """);
  }

  @Test
  public void overrideTypeError() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A)
      \\func test : R.{3,2} => \\new R \\Set1 \\Set2 Nat
      """, 1);
  }

  @Test
  public void overrideWithCustom() {
    typeCheckModule("""
      \\record R.{s} (A B : \\Type) (C : \\Type s) (a : A) (b : B)
      \\func test : R.{1,2,3} => \\new R \\Set1 \\Set2 \\Set0 Nat Nat
      """);
  }

  @Test
  public void overrideWithCustom2() {
    typeCheckModule("""
      \\record R.{s} (C : \\Type s) (A B : \\Type) (a : A) (b : B)
      \\func test : R.{1,2,3} => \\new R \\Set0 \\Set1 \\Set2 Nat Nat
      """);
  }

  @Test
  public void overrideWithCustomError() {
    typeCheckModule("""
      \\record R.{s} (A B : \\Type) (C : \\Type s) (a : A) (b : B)
      \\func test : R.{1,2,3} => \\new R \\Set0 \\Set0 \\Set1 Nat Nat
      """, 1);
  }

  @Test
  public void overrideWithCustomError2() {
    typeCheckModule("""
      \\record R.{s} (C : \\Type s) (A B : \\Type) (a : A) (b : B)
      \\func test : R.{1,2,3} => \\new R \\Set1 \\Set0 \\Set0 Nat Nat
      """, 1);
  }

  @Test
  public void classExtTest() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test : \\Type4 => R.{3} { | A => Nat }
      """);
    assertEquals(new UniverseExpression(new Sort(new Level(BigInteger.valueOf(4)), ConstLevel.INFINITY)), ((Expression) Objects.requireNonNull(((FunctionDefinition) getDefinition("test")).getBody())).getType());
  }

  @Test
  public void classExtTest2() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test : \\Type4 => R.{3} { | B => Nat }
      """);
    assertEquals(new UniverseExpression(new Sort(new Level(BigInteger.valueOf(4)), ConstLevel.INFINITY)), ((Expression) Objects.requireNonNull(((FunctionDefinition) getDefinition("test")).getBody())).getType());
  }

  @Test
  public void classExtError() {
    typeCheckModule("""
      \\record R (A B : \\Type) (a : A) (b : B)
      \\func test : \\Type4 => R.{3,2} { | A => Nat }
      """, 1);
  }

  @Test
  public void fieldType() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test (r : R.{3}) : \\Type 3 => r.A
      """);
    assertEquals(new UniverseExpression(new Sort(new Level(BigInteger.valueOf(3)), ConstLevel.INFINITY)), ((Expression) Objects.requireNonNull(((FunctionDefinition) getDefinition("test")).getBody())).getType());
  }

  @Test
  public void compareTest() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test (r : R.{3}) : R.{4} => r
      """);
  }

  @Test
  public void compareError() {
    typeCheckModule("""
      \\record R (A : \\Type) (a : A)
      \\func test (r : R.{4}) : R.{3} => r
      """, 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }
}
