package org.arend.typechecking.doubleChecker;

import org.arend.core.context.binding.Binding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.ClassDefinition;
import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.visitor.FreeVariablesCollector;
import org.arend.core.definition.Definition;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.DeferredMetaDefinition;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.typechecking.meta.TrivialMetaTypechecker;
import org.arend.library.MemoryLibrary;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.MetaReferable;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.typechecking.TypeCheckingTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import java.util.Collections;

/**
 * Regressions found by double-checking arend-lib: the double checker must accept what the typechecker accepts,
 * and the typechecker must not accept ill-typed terms.
 */
public class DoubleCheckerRegressionTest extends TypeCheckingTestCase {
  @Test
  public void implementedInfiniteFieldTest() {
    typeCheckModule("""
      \\class Pt (E : \\Type) (base : E)
      \\func loop (X : Pt) : Pt => \\new Pt (X.base = X.base) idp
      \\func id' {A : \\Cat} (a : A) : A => a
      \\func test.{u} (B : Pt.{u}) => id' {\\Type u} (Pt.E {loop B})
      """);
  }

  @Test
  public void idpEliminatedVariableTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\record S \\extends R
      \\truncated \\data Tr (A : \\Type) : \\Prop | inT A
      \\func test (P : R -> \\Prop) : \\Pi {y : R} -> Tr (\\Sigma (y' : S) (y' = {R} y) (P y')) -> Tr (P y)
        => \\lam {_} (inT (y', idp, d)) => inT d
      """);
  }

  @Test
  public void idpSubstitutionTest() {
    typeCheckModule("""
      \\func conc {A : \\Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \\elim q
        | idp => p
      \\func test {A : \\Type} {a a' a'' : A} {p : a' = a} {r : a = a''} {q : a' = a''} (t : q = conc p r) : t = t \\elim p, r, t
        | idp, idp, idp => idp
      """);
  }

  @Test
  public void nestedIdpPatternTest() {
    typeCheckModule("""
      \\truncated \\data Tr (A : \\Type) : \\Prop | inT A
      \\data Empty
      \\data Or (A B : \\Type) | inl A | inr B
      \\func test {A : \\Type} (h : Tr (\\Sigma (b : A -> \\Prop) (Or (b = {A -> \\Prop} (\\lam _ => Tr (\\Sigma Empty Nat))) (b = b)) (Tr (\\Sigma (a : A) (b a))))) : Tr A \\elim h
        | inT (b, inl idp, inT (a, inT ((), _)))
        | inT (b, inr _, inT (a, _)) => inT a
      """);
  }

  @Test
  public void typeConstructorPatternTest() {
    typeCheckModule("""
      \\truncated \\data Tr (A : \\Type) : \\Prop | inT A
      \\type Im {A B : \\Type} (f : A -> B) (b : B) => Tr (\\Sigma (a : A) (f a = b))
      \\func Ov {C : \\Type} (H : C -> \\Set) => \\Sigma (y : C) (H y)
      \\func mp {J X : \\Type} (g : J -> X) (h : \\Pi (t : X) -> Tr (\\Sigma (j : J) (g j = t))) => 0
      \\func test {C J : \\Type} (H : C -> \\Set) (P : J -> Ov H)
        => mp {J} {\\Sigma (px : Ov H) (Im P px)} (\\lam j => (P j, inT (j, idp))) (\\lam (_, inT (j, idp)) => inT (j, idp))
      """);
  }

  @Test
  public void arrayNormalizationTest() {
    typeCheckModule("""
      \\func toA (cs : Array Int) (env : Array Nat cs.len) : Array Nat \\elim cs, env
        | nil, nil => nil
        | 0 :: cs, _ :: env => toA cs env
        | pos (suc m) :: cs, a :: env => a :: toA (pos m :: cs) (a :: env)
        | neg (suc m) :: cs, a :: env => a :: toA (neg m :: cs) (a :: env)
      \\func test (m : Nat) (cs : Array Int) (a : Nat) (env : Array Nat cs.len) : toA (pos (suc m) :: cs) (a :: env) = a :: toA (pos m :: cs) (a :: env) => idp
      \\func test2 (cs : Array Int) {env : Array Nat cs.len} : Nat \\elim cs, env
        | nil, nil => 0
        | 0 :: cs, _ :: env => 0
        | pos (suc m) :: cs, a :: env => \\let t => toA (pos m :: cs) (a :: env) \\in 0
        | neg (suc m) :: cs, a :: env => 0
      """);
  }

  @Test
  public void reverseSubstitutionTest() {
    typeCheckModule("""
      \\data Cov (c : Nat) (U : Nat -> \\Type) | cinj (b : Nat) (U b)
      \\func emb (b : Nat) : \\Sigma (Nat -> \\Type) Nat => (\\lam c => Cov c (\\lam y => b = y), 0)
      \\func univ {x : Nat} {U : \\Sigma (Nat -> \\Type) Nat} (Ux : U.1 x) : U = U => idp
      \\func fix {U : \\Sigma (Nat -> \\Type) Nat} (p : U = U) (q : U = U) : Nat => 0
      \\func test (b c : Nat) (e : emb b = emb b) : Nat => fix (univ {c} (cinj b idp)) e
      """, 1);
  }

  @Test
  public void deferredInvariantArgumentTest() {
    typeCheckModule("""
      \\func app {A B : \\Cat} (f : A -> B) (a : A) : B => f a
      \\func test {B C : \\Cat} (u : B ->⁺ C) (y :⁺ B) : C => app (\\lam (z : Nat) => u y) 0
      """, 1);
  }

  @Test
  public void inferredCatDomainTest() {
    typeCheckModule("""
      \\func test {A :⁺ \\Cat} {B :⁺ A ->⁺ \\Cat} {f g :⁺ \\Pi (x :⁺ A) -> B x} (p :⁺ \\Pi (x :⁺ A) -> f x = g x) : f = g
        => path \\lam i x => p x i
      """, 1);
  }

  @Test
  public void inferredInvariantDomainTest() {
    typeCheckModule("""
      \\func test {A : \\Cat} {B : A -> \\Cat} {f g : \\Pi (x : A) -> B x} (p : \\Pi (x : A) -> f x = g x) : f = g
        => path \\lam i x => p x i
      """);
  }

  private static class IdMeta implements MetaDefinition {
    @Override
    public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
      return typechecker.typecheck(contextData.getArguments().getFirst().getExpression(), contextData.getExpectedType());
    }
  }

  private void addDeferredMeta() {
    ModuleLocation module = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.GENERATED, new ModulePath("Meta"));
    MetaDefinition meta = new DeferredMetaDefinition(new IdMeta(), false, true);
    MetaReferable metaRef = new MetaReferable(AccessModifier.PUBLIC, Precedence.DEFAULT, "defer", new TrivialMetaTypechecker(meta), null, MODULE_REF);
    metaRef.setDefinition(meta);
    ConcreteStatement statement = new ConcreteStatement(new ConcreteGroup(DocFactory.nullDoc(), metaRef, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()), null);
    server.addReadOnlyModule(module, () -> new ConcreteGroup(DocFactory.nullDoc(), new FullModuleReferable(module), null, Collections.singletonList(statement), Collections.emptyList(), Collections.emptyList()));
  }

  @Test
  public void deferredMetaEquationTest() {
    addDeferredMeta();
    typeCheckModule("""
      \\import Meta
      \\func test (n m : Nat) : defer n = m => idp
      """, 1);
  }

  @Test
  public void deferredMetaEquationTest2() {
    addDeferredMeta();
    typeCheckModule("""
      \\import Meta
      \\func test (n : Nat) : defer n = n => idp
      """);
  }

  @Test
  public void classFieldParametersPartialNewTest() {
    typeCheckModule("""
      \\data Empty
      \\record SP (E : \\Set) (lt : E -> E -> \\Prop)
      \\func le {A : SP} (a b : A.E) => A.lt b a -> Empty
      \\record P \\extends SP | le' : E -> E -> \\Type
      \\record C2 \\extends P {
        | le' => le
        | m : E -> E -> E
        | ml {x y : E} : le' (m x y) x
      }
      \\func test {A : \\Set} (c : C2 A) : Nat \\elim c
        | (lt, m, ml) => \\let t => ml \\in 0
      """);
  }

  @Test
  public void uncomputableTypeSolutionTest() {
    typeCheckModule("""
      \\record M (E : \\Set) (zro : E)
      \\data Q (A : \\Type) | inq A
      \\data D {V : M} (l : Array (\\Sigma V.E Nat)) | dcon
      \\func inF {V : M} (l : Array (\\Sigma V.E Nat)) : Q (Array (\\Sigma V.E Nat)) => inq l
      \\func zl {V : M} {l : Array (\\Sigma V.E Nat)} (p : \\Pi (i : Fin l.len) -> (l i).1 = V.zro) : D l => dcon
      \\func sfQ {V : M} {l : Array (\\Sigma V.E Nat)} (d : D l) : inF l = inF l => idp
      \\func test {V : M} {l : Array (\\Sigma V.E Nat)}
        : inF (\\new Array (\\Sigma V.E Nat) l.len (\\lam i => (V.zro, (l i).2))) = inF (\\new Array (\\Sigma V.E Nat) l.len (\\lam i => (V.zro, (l i).2)))
        => sfQ (zl \\lam _ => idp)
      """);
  }

  // The inferred result type has the sort of a \Pi-type whose codomain sort refers to a parameter.
  @Test
  public void parametricPiSortTest() {
    typeCheckModule("""
      \\func test.{u} {P : \\Cat} (Q : \\Type u) (X : \\Cat) : P ->+ \\Cat
        => \\lam (e :+ P) => \\Pi (q :+ Q) -> X
      """);
  }

  @Test
  public void parametricPiSortTest2() {
    typeCheckModule("""
      \\func test.{u} (Q : \\Type u) (X : \\Type) : Nat -> \\Type
        => \\lam n => Q -> X
      """);
  }

  @Test
  public void idpInvariantSigmaPlusTest() {
    typeCheckModule("""
      \\func test {A : \\Type} (a : A) (t : \\Sigma+ (x : A) (x = a)) : A => \\case \\elim a, \\elim t \\with {
        | _, (x, idp) => x
      }
      \\func test2 {A : \\Type} (a : A) (t : \\Sigma+ (x : A) (x = a)) : A \\elim a, t
        | _, (x, idp) => x
      """);
  }

  // The implicit argument C is solved by a variable whose type does not implement E, while the expected type implements it.
  // Such a solution is correct up to eta for records.
  @Test
  public void inferredClassInstanceEtaTest() {
    typeCheckModule("""
      \\class R (E : \\Type) | r : E -> Nat
      \\func aux {X : \\Type} (p : X -> X) {C : R X} (q : \\Pi (e : X) -> C.r e = C.r e) : Nat => 0
      \\func test (C : R) : Nat => aux (\\lam (x : C.E) => x) (\\lam e => idp {Nat} {C.r e})
      """);
  }

  // Since \\Type u is a subtype of \\Cat u, a type can be a component of a \\Sigma-type in \\Cat u in any position, also covariantly varying.
  // The sort inferred for a solution of an inference variable must agree with checking against \\Cat u.
  @Test
  public void sigmaTypeComponentSortTest() {
    typeCheckModule("""
      \\data P {A B : \\Cat} (f : A ->⁺ B) | pl (b :⁺ B) | pa (a :⁺ A)
      \\func Tot.{u} {A B : \\Cat u} {f : A ->⁺ B} (F : P f ->⁺ \\Type u) : \\Cat u => \\Sigma⁺ (b : B) (F (pl b))
      \\func test.{u} (X : \\Type u) (D : \\Cat u) (F : P {\\Sigma⁺ X D} {\\Sigma⁺ X D} (\\lam a => a) ->⁺ \\Type u) => Tot.{u} F
      \\func test2.{u} (X : \\Type u) (D : \\Cat u) : \\Cat u => P {\\Sigma⁺ X D} {\\Sigma⁺ X D} (\\lam a => a)
      \\func test3.{u} (A :⁺ \\Type u) : \\Cat u => \\Sigma⁺ DI A
      \\func test4.{u} (A :⁺ \\Type u) : \\Cat u => \\Sigma⁺ A DI
      """);
  }

  @Test
  public void sigmaTypeReleasedComponentSortTest() {
    typeCheckModule("""
      \\data P {A B : \\Cat} (f : A ->⁺ B) | pl (b :⁺ B) | pa (a :⁺ A)
      \\func Tot.{u} {A B : \\Cat u} {f : A ->⁺ B} (F : P f ->⁺ \\Type u) : \\Cat u => \\Sigma⁺ (b : B) (F (pl b))
      \\func test.{u} (X : \\Type u) (D : \\Cat u) (F : P {\\Sigma⁺ D X} {\\Sigma⁺ D X} (\\lam a => a) ->⁺ \\Type u) => Tot.{u} F
      \\func test2.{u} (X : \\Type u) (D : \\Cat u) : \\Cat u => P {\\Sigma⁺ D X} {\\Sigma⁺ D X} (\\lam a => a)
      \\func test3.{u} (X Y : \\Type u) : \\Type u => \\Sigma⁺ X Y
      \\func test4.{u} (X Y : \\Type u) => \\Sigma⁺ X Y
      \\func test5.{u} (X Y : \\Type u) : \\Type u => test4 X Y
      """);
  }

  // The type of p depends on g, which is declared later, through the implementation of f.
  // Fields must be reordered so that the telescope of fields is well-scoped.
  @Test
  public void implementationForwardDependencyTest() {
    typeCheckModule("""
      \\class A (E : \\Set) {
        | f : E -> Nat
        | p : \\Pi (x : E) -> f x = f x
      }
      \\class B \\extends A {
        | g : E -> Nat
        | f x => g x
      }
      \\func test (b : B) : Nat \\elim b
        | (E, g, p) => 0
      """);
    assertEquals(Arrays.asList("E", "g", "p"), ((ClassDefinition) getDefinition("B")).getNotImplementedFields().stream().map(Definition::getName).toList());
    assertFieldParametersScoped("B");
  }

  // \this is passed as an instance of a superclass whose field is implemented in a subclass via a field the superclass does not have.
  @Test
  public void superClassThisImplementationTest() {
    typeCheckModule("""
      \\class A (E : \\Set) | a : E -> Nat
      \\func F (X : A) : \\Prop => X.a = X.a
      \\class B \\extends A {
        | b : Nat
        | a _ => b
      }
      \\class C \\extends B
        | c : F \\this
      \\func test (x : C) : Nat \\elim x
        | (E, b, c) => b
      """);
    assertFieldParametersScoped("C");
  }

  // The implementation of s in D contains a \\type constructor applied to \\this.
  // It must not be replaced with the whole object being constructed when the telescope of fields of D2 is computed.
  @Test
  public void superClassThisTypeConstructorTest() {
    typeCheckModule("""
      \\class A (E : \\Set) | a : Nat
      \\type T (X : A) => X.a = X.a
      \\class S \\extends A
        | s : \\Sigma (T \\this) Nat
      \\class D \\extends S {
        | d : Nat
        | s => (idp, d)
      }
      \\func F (X : S) : \\Set => Nat
      \\class D2 \\extends D
        | f : F \\this
      \\func test (x : D2) : Nat \\elim x
        | (E, a, d, f) => d
      """);
    assertFieldParametersScoped("D2");
  }

  /**
   * Checks that the type of every parameter returned by {@link ClassCallExpression#getClassFieldParameters} refers only to preceding parameters.
   */
  private void assertFieldParametersScoped(String className) {
    ClassDefinition classDef = (ClassDefinition) getDefinition(className);
    DependentLink params = new ClassCallExpression(classDef, classDef.makeIdLevels(), new LinkedHashMap<>()).getClassFieldParameters();
    Set<Binding> allParams = new HashSet<>(DependentLink.Helper.toList(params));
    Set<Binding> preceding = new HashSet<>();
    for (DependentLink link = params; link.hasNext(); link = link.getNext()) {
      for (Binding binding : FreeVariablesCollector.getFreeVariables(link.getType())) {
        if (allParams.contains(binding) && !preceding.contains(binding)) {
          throw new AssertionError("The type of field parameter '" + link.getName() + "' refers to '" + binding.getName() + "', which is not a preceding parameter");
        }
      }
      preceding.add(link);
    }
  }
}
