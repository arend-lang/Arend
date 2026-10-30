package org.arend.naming;

import org.arend.ext.error.GeneralError;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.arend.Matchers.*;
import static org.junit.Assert.assertTrue;

public class ExtensionFunctionTest extends TypeCheckingTestCase {
  @Test
  public void recordTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .foo (r : R) => r.x
      \\func test (r : R) : r.foo = r.x => idp
      """);
  }

  @Test
  public void dataTest() {
    typeCheckModule("""
      \\data D | con Nat
      \\func .get (d : D) : Nat \\elim d
        | con n => n
      \\func test : (con 3).get = 3 => idp
      \\func test2 (d : D) => d.get
      """);
  }

  @Test
  public void natTest() {
    typeCheckModule("""
      \\func .twice (n : Nat) => n Nat.+ n
      \\func test (n : Nat) => n.twice
      \\func test2 : (2).twice = 4 => idp
      """);
  }

  @Test
  public void implicitParametersTest() {
    typeCheckModule("""
      \\data List (A : \\Type) | nil | cons A (List A)
      \\func .map {A B : \\Type} (l : List A) (f : A -> B) : List B \\elim l
        | nil => nil
        | cons a l => cons (f a) (l.map f)
      \\func test (l : List Nat) : List Nat => l.map suc
      """);
  }

  @Test
  public void extraArgumentsTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .add (r : R) (n m : Nat) => r.x Nat.+ n Nat.+ m
      \\func test (r : R) : Nat => r.add 1 2
      """);
  }

  @Test
  public void untypedVariableTest() {
    typeCheckModule("""
      \\func .twice (n : Nat) => n Nat.+ n
      \\func test : Nat -> Nat => \\lam x => x.twice
      """);
  }

  @Test
  public void patternVariableTest() {
    typeCheckModule("""
      \\func .twice (n : Nat) => n Nat.+ n
      \\func test (n : Nat) : Nat
        | 0 => 0
        | suc n => n.twice
      """);
  }

  @Test
  public void expressionArgumentTest() {
    typeCheckModule("""
      \\func .twice (n : Nat) => n Nat.+ n
      \\func f (n : Nat) => n
      \\func test : (f 2).twice = 4 => idp
      """);
  }

  @Test
  public void chainTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .toR (n : Nat) : R => \\new R n
      \\func .twice (n : Nat) => n Nat.+ n
      \\func test (n : Nat) : n.toR.x.twice = n Nat.+ n => idp
      """);
  }

  @Test
  public void fieldWinsTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .x (r : R) : Nat -> Nat => \\lam n => n
      \\func test (r : R) : Nat => r.x
      """);
  }

  @Test
  public void superClassTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\record S \\extends R
      \\func .foo (r : R) => r.x
      \\func test (s : S) : s.foo = s.x => idp
      """);
  }

  @Test
  public void exactBeforeSuperClassTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\record S \\extends R
      \\module M \\where {
        \\func .foo (r : R) => 0
      }
      \\module N \\where {
        \\func .foo (s : S) => 1
      }
      \\open M
      \\open N
      \\func test (s : S) : s.foo = 1 => idp
      \\func test2 (r : R) : r.foo = 0 => idp
      """);
  }

  @Test
  public void sameNameSameModuleError() {
    resolveNamesModule("""
      \\data D | con Nat
      \\func .size (n : Nat) => n
      \\func .size (d : D) => 0
      """, 1);
    assertThatErrorsAre(duplicateName(GeneralError.Level.ERROR, "size"));
  }

  @Test
  public void shadowingTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .size (n : Nat) => 0
      }
      \\open M
      \\func .size (n : Nat) => n
      \\func test : (3).size = 3 => idp
      """);
  }

  @Test
  public void sameNameDifferentModulesTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .size (n : Nat) => n
      }
      \\module N \\where {
        \\data D | con Nat
        \\func .size (d : D) : Nat \\elim d
          | con n => suc n
      }
      \\open M
      \\open N
      \\func test : (3).size = 3 => idp
      \\func test2 : (con 3).size = 4 => idp
      """);
  }

  @Test
  public void notStaticTest() {
    typeCheckModule("""
      \\func .twice (n : Nat) => n Nat.+ n
      \\func test => twice 3
      """, 1);
  }

  @Test
  public void notOpenedTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .twice (n : Nat) => n Nat.+ n
      }
      \\func test (n : Nat) => n.twice
      """, 1);
  }

  @Test
  public void openedTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .twice (n : Nat) => n Nat.+ n
      }
      \\open M (twice)
      \\func test (n : Nat) => n.twice
      """);
  }

  @Test
  public void openedOtherTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .twice (n : Nat) => n Nat.+ n
        \\func f => 0
      }
      \\open M (f)
      \\func test (n : Nat) => n.twice
      """, 1);
  }

  @Test
  public void hidingTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .twice (n : Nat) => n Nat.+ n
      }
      \\open M \\hiding (twice)
      \\func test (n : Nat) => n.twice
      """, 1);
  }

  @Test
  public void renamingTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .twice (n : Nat) => n Nat.+ n
      }
      \\open M (twice \\as double)
      \\func test : (3).double = 6 => idp
      """);
  }

  @Test
  public void renamingOldNameTest() {
    typeCheckModule("""
      \\module M \\where {
        \\func .twice (n : Nat) => n Nat.+ n
      }
      \\open M (twice \\as double)
      \\func test (n : Nat) => n.twice
      """, 1);
  }

  @Test
  public void noMatchingTypeTest() {
    typeCheckModule("""
      \\data D | con Nat
      \\func .twice (n : Nat) => n Nat.+ n
      \\func test (d : D) => d.twice
      """, 1);
  }

  @Test
  public void notFoundInRecordTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .twice (n : Nat) => n Nat.+ n
      \\func test (r : R) => r.twice
      """, 1);
  }

  @Test
  public void noExplicitParametersError() {
    typeCheckModule("""
      \\func .foo {n : Nat} => n
      """, 1);
  }

  @Test
  public void notDefCallError() {
    typeCheckModule("""
      \\func .foo (A : \\Type) (a : A) => a
      \\func .bar {A : \\Type} (a : A) => a
      """, 2);
  }

  @Test
  public void dynamicError() {
    parseModule("""
      \\record R (x : Nat) {
        \\func .foo (r : R) => 0
      }
      """, 1);
  }

  @Test
  public void useError() {
    parseModule("""
      \\record R (x : Nat) \\where {
        \\use \\coerce .foo (r : R) : Nat => 0
      }
      """, 1);
  }

  @Test
  public void recursiveTest() {
    typeCheckModule("""
      \\func .double (n : Nat) : Nat \\elim n
        | 0 => 0
        | suc n => suc (suc n.double)
      \\func test : (3).double = 6 => idp
      """);
  }

  @Test
  public void classReceiverUnqualifiedTest() {
    typeCheckModule("""
      \\class C (E : \\Set) (e : E)
      \\func .twoE (c : C) => (c.e, c.e)
      \\instance NatC : C Nat 0
      \\func test : twoE = (0, 0) => idp
      \\func test2 (c : C) : twoE {c} = (c.e, c.e) => idp
      \\func test3 (c : C) : c.twoE = twoE {c} => idp
      """);
  }

  @Test
  public void dynamicLocalInstanceTest() {
    typeCheckModule("""
      \\class C (E : \\Set) {
        | e : E
        \\func getE : E => e
      }
      \\func test {c : C} : c.E => C.getE
      """);
  }

  @Test
  public void classReceiverLocalInstanceTest() {
    typeCheckModule("""
      \\class C (E : \\Set) {
        | e : E
      }
      \\func .getE (c : C) : c => e
      \\func test {c : C} : c.E => getE
      """);
  }

  @Test
  public void recordReceiverUnqualifiedTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .foo (r : R) => r.x
      \\func test (r : R) : foo {r} = r.x => idp
      \\func test2 (r : R) : r.foo = foo {r} => idp
      """);
  }

  @Test
  public void receiverIsImplicitTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .add (r : R) (n : Nat) => r.x Nat.+ n
      \\func test (r : R) : add {r} 1 = r.add 1 => idp
      """);
  }

  @Test
  public void subclassBodyTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .foo (r : R) => r.x
      \\record S \\extends R {
        \\func bar : foo = x => idp
      }
      """);
  }

  @Test
  public void receiverPatternMatchingTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .pred (r : R) (n : Nat) : Nat \\elim n
        | 0 => r.x
        | suc n => n
      \\func test (r : R) : r.pred 0 = r.x => idp
      """);
  }

  @Test
  public void classReceiverInOtherModuleTest() {
    typeCheckModule("""
      \\class C (E : \\Set) (e : E)
      \\module M \\where {
        \\func .twoE (c : C) => (c.e, c.e)
      }
      \\open M
      \\func test (c : C) : twoE {c} = (c.e, c.e) => idp
      """);
  }

  @Test
  public void notOpenedUnqualifiedTest() {
    resolveNamesModule("""
      \\record R (x : Nat)
      \\module M \\where {
        \\func .foo (r : R) => r.x
      }
      \\func test (r : R) => foo {r}
      """, 1);
    assertThatErrorsAre(notInScope("foo"));
  }

  @Test
  public void staticWinsTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\module M \\where {
        \\func .foo (r : R) => r.x
      }
      \\open M
      \\func foo => 7
      \\func test : foo = 7 => idp
      \\func test2 (r : R) : r.foo = r.x => idp
      """);
  }

  @Test
  public void nonClassUnqualifiedError() {
    typeCheckModule("""
      \\module M \\where {
        \\func .twice (n : Nat) => n Nat.+ n
      }
      \\open M
      \\func test => twice 3
      """, 1);
  }

  @Test
  public void ambiguousUnqualifiedError() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\record Q (y : Nat)
      \\module M \\where {
        \\func .foo (r : R) => r.x
      }
      \\module N \\where {
        \\func .foo (q : Q) => q.y
      }
      \\open M
      \\open N
      \\func test (r : R) => foo {r}
      \\func test2 (r : R) (q : Q) : r.foo Nat.+ q.foo = r.x Nat.+ q.y => idp
      """, 1);
  }

  @Test
  public void instanceAfterReceiverTest() {
    typeCheckModule("""
      \\class C (E : \\Set)
      \\class F (X : \\Set) | elem : X
      \\instance NatF : F Nat 0
      \\func .foo (c : C) {f : F} (x : f -> c) : c => x f.elem
      \\func test (c : C) (x : Nat -> c) => c.foo x
      \\func test2 (c : C) (x : Nat -> c) => foo {c} x
      """);
  }

  @Test
  public void extensionNamespaceTest() {
    typeCheckModule("""
      \\class C (E : \\Set)
      \\func .foo (c : C) => 0
        \\where
          \\func bar => 1
      \\open foo
      \\func t1 : bar = 1 => idp
      \\func t2 : foo.bar = 1 => idp
      """);
  }

  @Test
  public void unqualifiedFieldCallTest() {
    typeCheckModule("""
      \\record P (a b : Nat)
      \\class C {
        | e : Nat
      }
      \\func .pair (c : C) : P => \\new P 1 2
      \\func test {c : C} : pair.a = 1 => idp
      """);
  }

  @Test
  public void receiverIsThisTest() {
    typeCheckModule("""
      \\class C (E : \\Set) {
        | e : E
      }
      \\func .a (c : C) : c => e
      \\func .b (c : C) => a
      \\func test (c : C) : c.b = c.e => idp
      """);
  }

  @Test
  public void receiverInClausesTest() {
    typeCheckModule("""
      \\class C (E : \\Set) {
        | e : E
      }
      \\func .pick (c : C) (n : Nat) : c
        | 0 => e
        | suc n => pick n
      \\func test (c : C) : c.pick 3 = c.e => idp
      """);
  }

  @Test
  public void qualifiedInfixTest() {
    typeCheckModule("""
      \\record R (x : Nat)
      \\func .\\infixl 6 +r (r : R) (a b : Nat) : Nat => a Nat.+ b Nat.+ r.x
      \\func test (r : R) : 1 r.+r 2 = 3 Nat.+ r.x => idp
      """);
  }

  @Test
  public void dynamicOnExtensionResultTest() {
    typeCheckModule("""
      \\record R (x : Nat) {
        \\func double => x Nat.+ x
      }
      \\class C {
        | e : Nat
      }
      \\func .toR (c : C) : R => \\new R 1
      \\func test {c : C} : toR.double = 2 => idp
      """);
  }

  @Test
  public void prettyPrintReceiverTest() {
    ConcreteGroup group = resolveNamesModule("""
      \\record R (x : Nat)
      \\func .foo (r : R) => r.x
      """, 0);
    StringBuilder builder = new StringBuilder();
    ((Concrete.FunctionDefinition) group.statements().get(1).group().definition()).accept(new PrettyPrintVisitor(builder, 0), null);
    assertTrue(builder.toString(), builder.toString().startsWith("\\func .foo (r : R)"));
  }

  @Test
  public void prettyPrintTest() {
    ConcreteGroup group = parseModule("\\func .twice (n : Nat) => n");
    StringBuilder builder = new StringBuilder();
    ((Concrete.FunctionDefinition) group.statements().getFirst().group().definition()).accept(new PrettyPrintVisitor(builder, 0), null);
    assertTrue(builder.toString(), builder.toString().startsWith("\\func .twice"));
    assertTrue(parseModule(builder.toString()).statements().getFirst().group().referable().isDynamicExtension());
  }
}
