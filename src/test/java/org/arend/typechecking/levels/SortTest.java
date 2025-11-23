package org.arend.typechecking.levels;

import org.arend.core.definition.Definition;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class SortTest extends TypeCheckingTestCase {
  private void checkDef(String text) {
    Definition definition = typeCheckDef(text);
    assertEquals(Collections.emptyList(), definition.getLevelParameters());
  }

  @Test
  public void sortTest() {
    typeCheckDef("\\func test => \\Sort", 1);
  }

  @Test
  public void idTest() {
    checkDef("\\func test {A : \\Sort} (a : A) => a");
  }

  @Test
  public void sigmaTest() {
    checkDef("\\func test {A : \\Sort} (B : A -> \\Sort) (p : \\Sigma (x : A) (B x)) => p");
  }

  @Test
  public void piTest() {
    checkDef("\\func test {A : \\Sort} (B : A -> \\Sort) (f : \\Pi (x : A) -> B x) => f");
  }
}
