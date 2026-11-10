package org.arend.typechecking.typeclass;

import org.arend.Matchers;
import org.arend.core.definition.ClassField;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Ignore;
import org.junit.Test;

import static org.arend.ExpressionFactory.Ref;
import static org.arend.Matchers.instanceInference;
import static org.arend.core.expr.ExpressionFactory.FieldCall;
import static org.arend.core.expr.ExpressionFactory.Nat;

public class RecursiveInstancesTest extends TypeCheckingTestCase {
  @Test
  public void instanceWithParameter() {
    typeCheckModule("""
      \\class A { | a : Nat }
      \\class B
      \\instance B-inst : B
      \\instance A-inst {b : B} : A | a => 0
      \\func f => a
      """, 1);
    assertThatErrorsAre(instanceInference(get("B")));
  }

  @Ignore
  @Test
  public void noRecursiveInstance() {
    typeCheckModule("""
      \\class A { | a : Nat }
      \\class B
      \\instance A-inst {b : B} : A | a => 0
      \\func f => a
      """, 1);
    assertThatErrorsAre(instanceInference(get("B")));
  }

  @Ignore
  @Test
  public void correctRecursiveInstance() {
    typeCheckModule("""
      \\class A { | a : Nat }
      \\class B (n : Nat)
      \\instance B-inst : B | n => 1
      \\instance A-inst {b : B 1} : A | a => 0
      \\func f => a
      """);
  }

  @Ignore
  @Test
  public void wrongRecursiveInstance() {
    typeCheckModule("""
      \\class A { | a : Nat }
      \\class B (n : Nat)
      \\instance B-inst : B 0
      \\instance A-inst {b : B 1} : A | a => 0
      \\func f => a
      """, 1);
    assertThatErrorsAre(instanceInference(get("B")));
  }

  @Ignore
  @Test
  public void wrongRecursiveInstance2() {
    typeCheckModule("""
      \\class A { | a : Nat }
      \\data Data (A : \\Set)
      \\data D
      \\data D'
      \\class B (X : \\Set)
      \\instance B-inst : B (Data D)
      \\instance A-inst {b : B (Data D')} : A | a => 0
      \\func f => a
      """, 1);
    assertThatErrorsAre(instanceInference(get("B")));
  }

  @Ignore
  @Test
  public void localRecursiveInstance() {
    typeCheckModule("""
      \\class A { | a : Nat }
      \\class B (n : Nat)
      \\instance A-inst {b : B 0} : A | a => 0
      \\func f {c : B 0} => a
      """);
  }

  @Test
  public void recursiveLocalInstance() {
    typeCheckModule("""
      \\class A (X : \\Set) { | x : X }
      \\data Data (X : \\Set) | con X
      \\instance Nat-inst : A Nat | x => 0
      \\instance Data-inst {T : \\Set} {d : A T} : A (Data T) | x => con x
      \\func f : Data Nat => x
      """);
  }

  @Test
  public void recursiveLocalInstance2() {
    typeCheckModule("""
      \\class A (X Y : \\Set) { | x : X }
      \\data Data (X : \\Set) | con X
      \\instance Nat-inst : A Nat | x => 0 | Y => Nat
      \\instance Data-inst {a : A} : A (Data a.X) | x => con x | Y => Nat
      \\func f : Data Nat => x
      """);
  }

  @Test
  public void noRecursiveLocalInstance() {
    typeCheckModule("""
      \\class A (X : \\Set) { | x : X }
      \\data Data (X : \\Set) | con X
      \\data Nat' | nat
      \\instance Nat-inst : A Nat' | x => nat
      \\instance Data-inst {T : \\Set} {d : A T} : A (Data T) | x => con x
      \\func f : Data Nat => x
      """, 1);
    assertThatErrorsAre(Matchers.instanceInference(get("A"), Nat()));
  }

  @Test
  public void noRecursiveLocalInstance2() {
    typeCheckModule("""
      \\class A (X Y : \\Set) { | x : X }
      \\data Data (X : \\Set) | con X
      \\instance Nat-inst : A Nat | x => 0 | Y => Nat
      \\instance Data-inst {a : A} : A (Data a.Y) | x => con x | Y => Nat
      """, 1);
    assertThatErrorsAre(Matchers.instanceInference(get("A"), FieldCall((ClassField) getDefinition("A.Y"), Ref(getDefinition("Data-inst").getParameters()))));
  }

  @Test
  public void noRecursiveLocalInstance3() {
    typeCheckModule("""
      \\class A (X : \\Set) { | x : X }
      \\data Data1 (X : \\Set) | con1 X
      \\data Data2 (X : \\Set) | con2 X
      \\instance Data1-inst {T : \\Set} {d : A T} : A (Data1 T) | x => con1 x
      \\instance Data2-inst {T : \\Set} {d : A T} : A (Data2 T) | x => con2 x
      \\func f : Data1 (Data2 Nat) => x
      """, 1);
    assertThatErrorsAre(Matchers.instanceInference(get("A"), Nat()));
  }

  @Test
  public void infRecursiveInstance() {
    typeCheckModule("""
      \\class C (X : \\Type) | foo : X -> X
      \\instance inst (c : C Nat) : C Nat | foo => c.foo
      \\func f => foo 3
      """, 1);
    assertThatErrorsAre(Matchers.instanceInference(get("C"), Nat()));
  }

  // The search for {Z} of univ picks ProdMod; then the variable {X} of univ should be solved by unification with ProdMod {?R} ?A ?B,
  // not by instance search, which produced an infinite tower of ProdMod with new inference variables.
  @Test
  public void noInstanceTower() {
    typeCheckModuleWithTimeout("""
      \\class Sp (E : \\Set)
      \\class Mod (R : \\Set) \\extends Sp
      \\instance ProdMod {R : \\Set} (A B : Mod R) : Mod R (\\Sigma A B)
      \\instance ProdSp (A B : Sp) : Sp (\\Sigma A B)
      \\record Map (X Y : Sp) (func : X -> Y)
      \\func idm {X : Sp} : Map X X (\\lam x => x) \\cowith
      \\func const {X Y : Sp} (y : Y) : Map X Y (\\lam _ => y) \\cowith
      \\func tuple {X Y Z : Sp} (f : Map Z X) (g : Map Z Y) : Map Z (ProdSp X Y) (\\lam z => (f.func z, g.func z)) \\cowith
      \\func Transfer {Y : \\Set} {Z : Sp} (g : Y -> Z) : Sp Y \\cowith
      \\func univ {X Z : Sp} {Y : \\Set} (f : X -> Y) (g : Y -> Z) (fg : Map X Z (\\lam x => g (f x))) : Map X (Transfer g) f \\cowith
      \\lemma refl {R : \\Set} {X : Mod R} {a : X} : a = a => idp
      \\func test {R : \\Set} {X : Mod R} {S : Sp} (a : X) : Map S (Transfer {\\Sigma (\\Sigma S (\\Sigma S X)) (a = a)} {ProdSp S (ProdSp S X)} __.1) (\\lam x => ((x,(x,a)), refl))
        => univ (\\lam x => ((x,(x,a)), refl)) __.1 (tuple idm (tuple idm (const a)))
      """, 2, 10000);
  }

  @Test
  public void onlyLocalInstance() {
    typeCheckModule("""
      \\class C | foo : Nat
      \\class D | bar : Nat
      \\instance inst {c : C} : D | bar => c.foo
      \\func f {c : C} => bar
      """);
  }
}