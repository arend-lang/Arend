package org.arend.cat;

import org.arend.core.definition.ClassDefinition;
import org.arend.ext.core.context.BindingVariance;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatRecordsTest extends TypeCheckingTestCase {
  @Test
  public void covariantFieldParses() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C | f :+ Nat");
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().getFirst().getVariance());
  }

  @Test
  public void invariantFieldIsDefault() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C | f : Nat");
    assertEquals(BindingVariance.INVARIANT, def.getPersonalFields().getFirst().getVariance());
  }

  @Test
  public void covariantFieldTeleParses() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (x :+ Nat)");
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().getFirst().getVariance());
  }

  @Test
  public void covariantFieldTeleGroupParses() {
    ClassDefinition def = (ClassDefinition) typeCheckDef("\\class C (x y :+ Nat)");
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().get(0).getVariance());
    assertEquals(BindingVariance.COVARIANT, def.getPersonalFields().get(1).getVariance());
  }

  @Test
  public void invariantFieldCowithError() {
    typeCheckModule("""
      \\class C (f : Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => a
      """, 1);
  }

  @Test
  public void covariantFieldCowithOk() {
    typeCheckModule("""
      \\class C (f :+ Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => a
      """);
  }

  @Test
  public void invariantFieldNewError() {
    typeCheckModule("""
      \\class C (f : Nat)
      \\func test (a :+ Nat) => \\new C { | f => a }
      """, 1);
  }

  @Test
  public void covariantFieldNewOk() {
    typeCheckModule("""
      \\class C (f :+ Nat)
      \\func test (a :+ Nat) => \\new C { | f => a }
      """);
  }

  @Test
  public void mixedVarianceCovariantFieldOk() {
    typeCheckModule("""
      \\class C (f : Nat) (g :+ Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => 0
        | g => a
      """);
  }

  @Test
  public void mixedVarianceInvariantFieldError() {
    typeCheckModule("""
      \\class C (f : Nat) (g :+ Nat)
      \\func test (a :+ Nat) : C \\cowith
        | f => a
        | g => 0
      """, 1);
  }

  @Test
  public void thisCovariantFieldAccessErrorViaInvariantField() {
    typeCheckModule("""
      \\class B | g :+ Nat
      \\class C \\extends B | f : Nat
      \\func h (n : Nat) => n
      \\func test (a :+ Nat) : C \\cowith
        | g => a
        | f => h \\this.g
      """, 1);
  }

  @Test
  public void thisCovariantFieldAccessErrorViaInvariantParam() {
    typeCheckModule("""
      \\class B | g :+ Nat
      \\class C \\extends B | f :+ Nat
      \\func h (n : Nat) => n
      \\func test (a :+ Nat) : C \\cowith
        | g => a
        | f => h \\this.g
      """, 1);
  }

  @Test
  public void thisCovariantFieldAccessOkUnderCovariantParam() {
    typeCheckModule("""
      \\class B | g :+ Nat
      \\class C \\extends B | f :+ Nat
      \\func h (n :+ Nat) => n
      \\func test (a :+ Nat) : C \\cowith
        | g => a
        | f => h \\this.g
      """);
  }

  @Test
  public void otherInstanceCovariantFieldAccessOk() {
    typeCheckModule("""
      \\class B (g :+ Nat)
      \\class C \\extends B
      \\func h (n : Nat) => n
      \\func test (c : C) => h c.g
      """);
  }

  @Test
  public void fieldDependencTest() {
    typeCheckModule("""
      \\func foo (n : Nat) => n
      \\record R (a :+ Nat) (p : foo a = 0)
      """, 1);
  }
}
