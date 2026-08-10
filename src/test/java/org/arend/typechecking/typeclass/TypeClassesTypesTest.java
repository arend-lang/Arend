package org.arend.typechecking.typeclass;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class TypeClassesTypesTest extends TypeCheckingTestCase {
  @Test
  public void testType() {
    typeCheckModule("""
      \\class C (X : \\Type)
        | idf : X -> X
      \\instance inst : C \\Set0
        | idf A => A
      \\func foo : idf Nat => 0
      """);
  }

  @Test
  public void testTypeType() {
    typeCheckModule("""
      \\class C (X : \\Type)
        | idf : X -> X
      \\instance inst.{u} : C (\\Type u)
        | idf A => A
      \\func foo.{u} (P : \\Type u) => idf P
      """);
  }

  @Test
  public void testTypeProp() {
    typeCheckModule("""
      \\class C (X : \\Type)
        | idf : X -> X
      \\instance inst.{u} : C (\\Type u)
        | idf A => A
      \\func foo (P : \\Prop) => idf P
      """, 1);
    assertThatErrorsAre(Matchers.argInferenceError());
  }

  @Test
  public void testTypeSet() {
    typeCheckModule("""
      \\class C (X : \\Type)
        | idf : X -> X
      \\instance inst.{u} : C (\\Type u)
        | idf A => A
      \\func foo (P : \\Set) => idf P
      """, 1);
    assertThatErrorsAre(Matchers.argInferenceError());
  }

  @Test
  public void testTypeError() {
    typeCheckModule("""
      \\class C (X : \\Type)
        | idf : X -> X
      \\instance inst : C \\Type0
        | idf A => A
      \\func foo (P : \\Set1) => idf P
      """, 1);
    assertThatErrorsAre(Matchers.argInferenceError());
  }

  @Test
  public void testTypeError2() {
    typeCheckModule("""
      \\class C (X : \\Type)
        | idf : X -> X
      \\instance inst.{u} : C (\\Set u)
        | idf A => A
      \\func foo.{u} (P : \\1-Type u) => idf P
      """, 1);
    assertThatErrorsAre(Matchers.argInferenceError());
  }

  @Test
  public void testTypeError3() {
    typeCheckModule("""
      \\class C (X : \\Type)
        | idf : X -> X
      \\instance inst.{u} : C (\\1-Type u)
        | idf A => A
      \\func foo.{u} (P : \\Set u) => idf P
      """, 1);
    assertThatErrorsAre(Matchers.argInferenceError());
  }
}
