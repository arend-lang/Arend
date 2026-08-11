/*
 * Copyright 2003-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.arend.typechecking.termination;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.TerminationCheckError;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class TerminationCheckTest extends TypeCheckingTestCase {

  @Test
  public void test31_1() {
    typeCheckModule("\\func \\infixl 9 ++ (a b : Nat) : Nat \\elim a | suc a' => suc (a' ++ b) | zero => b", 0);
  }

  @Test
  public void test31_2() {
    typeCheckModule("\\func \\infixl 9 + (a b : Nat) : Nat \\elim a | suc a' => suc (suc a' + b) | zero => b", 1);
  }

  private static final String minus =
    """
      \\func \\infix 9 - (x y : Nat) : Nat \\elim x | zero => zero | suc x' => x' - p y
      \\where \\func p (z : Nat) : Nat | zero => zero | suc z' => z'
      """;

  private static final String list =
    "\\data List (A : \\Type0) | nil | \\infixr 5 :-: A (List A)\n";

  @Test
  public void test32() {
    typeCheckModule(minus, 0);
  }

  @Test
  public void test33() {
    typeCheckModule(minus + "\\func \\infix 9 / (x y : Nat) : Nat => div' x (-.p x - y)\n" +
      "\\where \\func div' (x : Nat) (y' : Nat) : Nat\n" +
      "\\elim y' | zero => zero | suc y'' => suc (div' x (x - suc y''))\n", 1);
  }

  @Test
  public void test34_2() {
    typeCheckModule("""
      \\func ack (x y : Nat) : Nat
        | zero, y => suc y
        | suc x', zero => ack x' (suc zero)
        | suc x', suc y' => ack (suc x') y'
      """, 0);
  }

  // Nested recursion through the SAME container (List of List): flatten. Baseline for
  // container nesting; cf. changingIndex_* (different index) and issue130_* (generic).
  @Test
  public void nestedListOfList_flatten() {
    typeCheckModule(list + "\\func flatten {A : \\Type0} (l : List (List A)) : List A \\elim l\n" +
      "| nil => nil\n" +
      "| :-: nil xs => flatten xs\n" +
      "| :-: (:-: y ys) xs => y :-: flatten (ys :-: xs)", 0);
  }

  // Mutual recursion over nested List-of-List (f/g). Same-container nesting, accepted.
  @Test
  public void nestedListOfList_mutual() {
    typeCheckModule(list + "\\func f {A : \\Type0} (l : List (List A)) : List A \\elim l | nil => nil | :-: x xs => g x xs\n" +
      "\\func g {A : \\Type0} (l : List A) (ls : List (List A)) : List A \\elim l | nil => f ls | :-: x xs => x :-: g xs ls", 0);
  }

  @Test
  public void test38_1() {
    typeCheckModule(list + "\\func zip1 {A : \\Type0} (l1 l2 : List A) : List A \\elim l1\n" +
      "| nil => l2\n" +
      "| :-: x xs => x :-: zip2 l2 xs\n" +
      "\\func zip2 {A : \\Type0} (l1 l2 : List A) : List A \\elim l1\n" +
      "| nil => l2\n" +
      "| :-: x xs => x :-: zip1 l2 xs\n", 0);
  }

  @Test
  public void test38_2() {
    typeCheckModule(list + "\\func zip-bad {A : \\Type0} (l1 l2 : List A) : List A \\elim l1\n" +
      "| nil => l2\n" +
      "| :-: x xs => x :-: zip-bad l2 xs", 0);
  }

  // Infinitary (function-valued) constructor Lim (Nat -> ord): the recursive call
  // addord (f z) y descends into an application of the field f. Accepted (W-type child).
  @Test
  public void test310() {
    typeCheckModule("""
      \\data ord | O | S (_ : ord) | Lim (_ : Nat -> ord)
      \\func addord (x y : ord) : ord \\elim x
        | O => y
        | S x' => S (addord x' y)
        | Lim f => Lim (\\lam z => addord (f z) y)
      """, 0);
  }

  @Test
  public void test312_2() {
    typeCheckModule("""
      \\func h (x y : Nat) : Nat
        | zero, zero => zero
        | zero, suc y' => h zero y'
        | suc x', y' => h x' y'
      \\func f (x y : Nat) : Nat
        | zero, _ => zero
        | suc x', zero => zero
        | suc x', suc y' => h (g x' (suc y')) (f (suc (suc (suc x'))) y')
      \\func g (x y : Nat) : Nat
        | zero, _ => zero
        | suc x', zero => zero
        | suc x', suc y' => h (f (suc x') (suc y')) (g x' (suc (suc y')))
      """, 4);
  }

  @Test
  public void selfCallInType() {
    typeCheckModule(
      "\\data D Nat | con\n" +
      "\\func f (x : Nat) (y : D (f x con)) : Nat => x", 1);
  }

  @Test
  public void headerCycle() {
    typeCheckModule(
      "\\func he1 : he2 = he2 => path (\\lam _ => he2)\n" +
      "\\func he2 : he1 = he1 => path (\\lam _ => he1)", 2);
  }

  @Test
  public void headerNoCycle() {
    typeCheckModule(
      "\\func he1 (n : Nat) : Nat | zero => 0 | suc n => he2 n @ right\n" +
      "\\func he2 (n : Nat) : he1 n = he1 n | zero => path (\\lam _ => he1 0) | suc n => path (\\lam _ => he1 (suc n))");
  }

  @Test
  public void oneError() {
    typeCheckModule("""
      \\data D Nat | con
      \\func f (x : Nat) (y : D (f x con)) : Nat => x
      \\func g : Nat => f 0 con
      """, 1);
  }

  @Test
  public void nonMonomialCallMatrixTest() {
    typeCheckModule("""
      \\data Int : \\Set0 | pos Nat | neg Nat { zero => pos zero }
      \\func \\infixl 6 +$ (n m : Int) : Int \\elim n
        | pos zero => m
        | pos (suc n) => pos n +$ m
        | neg zero => m
        | neg (suc n) => neg n +$ m
      """, 0);
  }

  @Test
  public void test121_1() {
    typeCheckModule("""
      \\func foo (p : \\Sigma Nat Nat) : Nat
        | (n, 0) => 0
        | (n, suc m) => foo (7, m)
      """, 0);
  }

  @Test
  public void test121_2() {
    typeCheckModule("""
      \\data List (A : \\Type) | nil | cons A (List A)
      \\data All {A : \\Type} (P : A -> \\Type) (xs : List A) \\elim xs
        | nil => nilAll
        | cons x xs => consAll (P x) (All P xs)
      \\data End1 (n : Nat)
        | end1 (\\Pi (m : Nat) -> End1 m)
      \\func foo1 (xs : List Nat) (ys : All End1 xs) : Nat
        | nil, _ => 0
        | cons x xs, consAll (end1 e) ys => foo1 (cons x xs) (consAll (e x) ys)
      \\data End2 (n : Nat)
        | end2 (m : Nat) (\\Sigma -> End2 m)
      \\func foo2 (xs : List Nat) (ys : All End2 xs) : Nat
        | nil, _ => 0
        | cons x xs, consAll (end2 y e) ys => foo2 (cons y xs) (consAll (e ()) ys)
      \\func bar1 (x : Nat) (e : End1 x) : Nat \\elim e
        | end1 e => bar1 x (e x)
      \\func bar2 (x : Nat) (e : End2 x) : Nat \\elim e
        | end2 y e => bar2 y (e ())
      """, 0);
  }

  @Test
  public void emptySigmaParam() {
    typeCheckModule("""
      \\func f (x : \\Sigma) (n : Nat) : Nat \\elim n
        | 0 => 0
        | suc n => f x n
      """, 0);
  }

  @Test
  public void emptySigmaParamPair() {
    typeCheckModule("""
      \\func f (x : \\Sigma) (y : \\Sigma) (n : Nat) : Nat \\elim n
        | 0 => 0
        | suc n => f x y n
      """, 0);
  }

  @Test
  public void emptySigmaParamUnitPattern() {
    typeCheckModule("""
      \\func f (x : \\Sigma) (n : Nat) : Nat \\elim x, n
        | (), 0 => 0
        | (), suc n => f () n
      """, 0);
  }

  @Test
  public void test_loop1() {
    typeCheckModule("""
      \\func lol (a : \\Sigma Nat Nat) (b : \\Sigma Nat Nat) : Nat \\elim a, b {
        | (n,n1), (n2,n3) => lol (n, n1) (n2, n3)
      }
      """, 1);
  }

  @Test
  public void test_loop2() {
    typeCheckModule(
      """
        \\func fooA (p : \\Sigma Nat (\\Sigma Nat Nat)) : Nat \\elim p
          | (a, (b, c)) => fooB a b c
        \\func fooB (a b c : Nat) : Nat \\with | a, b, c => fooA (a, (b, c))
        """, 4);
  }

  @Test
  public void test_200() {
    typeCheckModule("""
      \\data List (A : \\Type)
        | nil
        | cons A (List A)
      \\func f (xs : List Nat) : Nat
        | nil => 0
        | cons _ nil => 1
        | cons _ (cons x xs) => f (cons x xs)
      """, 0);
  }

  @Test
  public void test_ise(){
    typeCheckModule("""
      \\func h (a : Nat) : Nat \\elim a
        | zero => 1
        | suc a => \\case (a, a) \\with {
          | p => g a p
        }
      \\func g (a : Nat) (p : \\Sigma Nat Nat) : Nat \\elim a
        | 0 => 0
        | suc a => h a
      """, 0);
  }

  @Test
  public void testBug(){
    typeCheckModule("\\data Bool | true | false\n\\func f (p : \\Sigma Bool Nat) => f p\n", 2);
  }

  private static final String listMaybe =
    "\\data List (A : \\Type) | nil | \\infixr 5 :: A (List A)\n" +
    "\\func \\infixl 5 ++ {A : \\Type} (xs ys : List A) : List A \\elim xs | nil => ys | :: x xs => x :: (xs ++ ys)\n" +
    "\\data Maybe (A : \\Type) | nothing | just A\n";

  // Issue #130: recursion through a generic container instantiated at the recursive type.
  // Generic-container nesting (GitHub #130): a datatype recurses through a generic
  // container instantiated at itself (List Pattern, Maybe TE, rose trees). Enabled by
  // this commit; Agda and Lean accept these directly, Coq needs a reformulation.
  @Test
  public void issue130_listOfPattern() {
    typeCheckModule(listMaybe +
      "\\data Pattern | PVar Nat | PCons Nat (List Pattern)\n" +
      "\\func getVars (p : List Pattern) : List Nat\n" +
      "  | nil => nil\n" +
      "  | (PVar n) :: ps => n :: getVars ps\n" +
      "  | (PCons n ps) :: ps' => getVars ps ++ getVars ps'\n" +
      "\\func getVarsP (p : Pattern) : List Nat\n" +
      "  | PVar n => n :: nil\n" +
      "  | PCons n ps => getVarsL ps\n" +
      "\\func getVarsL (p : List Pattern) : List Nat\n" +
      "  | nil => nil\n" +
      "  | p :: ps => getVarsP p ++ getVarsL ps", 0);
  }

  @Test
  public void issue130_maybeOfTE() {
    typeCheckModule(listMaybe +
      "\\data TE | EMaybe (Maybe TE)\n" +
      "\\func sc (e : TE) : Maybe (\\Sigma) | EMaybe mt => scM mt\n" +
      "\\func scM (m : Maybe TE) : Maybe (\\Sigma) | nothing => nothing | just e => sc e\n" +
      "\\func scMR (e : TE) : Maybe (\\Sigma) | EMaybe (just mt) => scMR mt | EMaybe nothing => nothing", 0);
  }

  @Test
  public void issue130_roseTree() {
    typeCheckModule(listMaybe +
      "\\data Rose | rose (List Rose)\n" +
      "\\func size (r : Rose) : Nat | rose rs => sizeL rs\n" +
      "\\func sizeL (rs : List Rose) : Nat | nil => 0 | r :: rs => size r Nat.+ sizeL rs", 0);
  }

  // Negative control: rebuilding a larger term must still be rejected.
  @Test
  public void issue130_roseRebuildRejected() {
    typeCheckModule(listMaybe +
      "\\data Rose | rose (List Rose)\n" +
      "\\func bad (r : Rose) : Nat | rose rs => badL rs\n" +
      "\\func badL (rs : List Rose) : Nat | nil => 0 | r :: rs => bad (rose (r :: rs))", -1);
  }

  @Test
  public void test34() {
    TestVertex ack = new TestVertex("ack", "x", "y");
    Set<BaseCallMatrix<TestVertex>> cms = new HashSet<>();
    cms.add(new TestCallMatrix("1", ack, ack, '<', 0, '?'));
    cms.add(new TestCallMatrix("1", ack, ack, '=', 0, '<', 1));
    BaseCallGraph<TestVertex> callGraph = new BaseCallGraph<>();
    callGraph.add(cms);
    assert callGraph.checkTermination().proj1;
  }

  @Test
  public void artificial1() {
    TestVertex f = new TestVertex("f", "x", "y", "z", "w");
    Set<BaseCallMatrix<TestVertex>> cms = new HashSet<>();
    cms.add(new TestCallMatrix("1", f, f, '<', 0, '?', '?', '?'));
    cms.add(new TestCallMatrix("2", f, f, '=', 0, '<', 1, '?', '?'));
    cms.add(new TestCallMatrix("3", f, f, '=', 0, '=', 1, '<', 2, '?'));
    cms.add(new TestCallMatrix("4", f, f, '=', 0, '=', 1, '=', 2, '<', 3));

    BaseCallGraph<TestVertex> callGraph = new BaseCallGraph<>();
    callGraph.add(cms);
    assert callGraph.checkTermination().proj1;
  }

  @Test
  public void artificial2() {
    TestVertex f = new TestVertex("f", "x", "y", "z", "w");
    Set<BaseCallMatrix<TestVertex>> cms = new HashSet<>();
    cms.add(new TestCallMatrix("1", f, f, '<', 0, '?', '?', '?'));
    cms.add(new TestCallMatrix("2", f, f, '=', 0, '<', 1, '?', '?'));
    cms.add(new TestCallMatrix("3", f, f, '=', 0, '=', 1, '<', 2, '?'));
    cms.add(new TestCallMatrix("4", f, f, '=', 0, '=', 1, '=', 2, '=', 3));

    BaseCallGraph<TestVertex> callGraph = new BaseCallGraph<>();
    callGraph.add(cms);
    assert !callGraph.checkTermination().proj1;
  }

  @Test
  public void artificial3() {
    TestVertex f = new TestVertex("f", "x", "y", "z", "w");
    Set<BaseCallMatrix<TestVertex>> cms = new HashSet<>();
    cms.add(new TestCallMatrix("2", f, f, '?', '<', 1, '?', '=', 3));
    cms.add(new TestCallMatrix("3", f, f, '?', '=', 1, '<', 2, '=', 3));
    cms.add(new TestCallMatrix("1", f, f, '?', '?', '?', '<', 3));
    cms.add(new TestCallMatrix("4", f, f, '<', 0, '=', 1, '=', 2, '=', 3));
    BaseCallGraph<TestVertex> callGraph = new BaseCallGraph<>();
    callGraph.add(cms);
    assert callGraph.checkTermination().proj1;
  }

  @Test
  public void test312() {
    TestVertex h = new TestVertex("h", "hx", "hy");
    TestVertex f = new TestVertex("f", "fx", "fy");
    TestVertex g = new TestVertex("g", "gx", "gy");
    Set<BaseCallMatrix<TestVertex>> cms = new HashSet<>();
    cms.add(new TestCallMatrix("h-h-1", h, h, '<', 0, '=', 1));
    cms.add(new TestCallMatrix("h-h-2", h, h, '=', 0, '<', 1));
    cms.add(new TestCallMatrix("f-f", f, f, '?', '<', 1));
    cms.add(new TestCallMatrix("f-h", f, h, '?', '?'));
    cms.add(new TestCallMatrix("f-g", f, g, '<', 0, '=', 1));
    cms.add(new TestCallMatrix("g-f", g, f, '=', 0, '=', 1));
    cms.add(new TestCallMatrix("g-g", g, g, '<', 0, '?'));
    cms.add(new TestCallMatrix("g-h", g, h, '?', '?'));
    BaseCallGraph<TestVertex> callGraph = new BaseCallGraph<>();
    callGraph.add(cms);
    assert !callGraph.checkTermination().proj1;
  }

  @Test
  public void compareTest() {
    var v = new TestVertex("v");
    var e1 = new TestCallMatrix("1", v, v,'<', 0);
    var e2 = new TestCallMatrix("1", v, v,'=', 0);
    var e3 = new TestCallMatrix("1", v, v,'<', 0, '=', 1);
    var e4 = new TestCallMatrix("1", v, v,'=', 0, '<', 1);
    var e5 = new TestCallMatrix("1", v, v,'<', 0, '<', 1);
    var e6 = new TestCallMatrix("1", v, v,'?');
    assert (e6.compare(e1) == BaseCallMatrix.R.LessThan && e6.compare(e2) == BaseCallMatrix.R.LessThan &&
            e6.compare(e3) == BaseCallMatrix.R.LessThan && e6.compare(e4) == BaseCallMatrix.R.LessThan &&
            e6.compare(e5) == BaseCallMatrix.R.LessThan);
    assert (e2.compare(e1) == BaseCallMatrix.R.LessThan && e1.compare(e2) == BaseCallMatrix.R.Unknown);
    assert (e1.compare(e3) == BaseCallMatrix.R.LessThan && e2.compare(e3) == BaseCallMatrix.R.LessThan &&
            e3.compare(e1) == BaseCallMatrix.R.Unknown && e3.compare(e2) == BaseCallMatrix.R.Unknown);
    assert (e3.compare(e4) == BaseCallMatrix.R.Unknown && e4.compare(e3) == BaseCallMatrix.R.Unknown);
    assert (e3.compare(e5) == BaseCallMatrix.R.LessThan && e4.compare(e5) == BaseCallMatrix.R.LessThan);
  }

  @Test
  public void performanceTest() {
    Set<BaseCallMatrix<TestVertex>> cms = new HashSet<>();
    TestVertex Cut = new TestVertex("a","T", "k", "n", "D", "I", "G", "M", "R", "p1", "p2");
    TestVertex CCut = new TestVertex("b","T", "k", "n", "D", "I", "G", "M", "R", "p1", "p2");

    cms.add(new TestCallMatrix("ab", Cut, CCut, '=', 0, '<', 1, '?', '=', 3, '<', 4, '-', '<', 6, '=', 5, '=', 7, '<', 8, '?'));
    cms.add(new TestCallMatrix("ba", CCut, Cut, '=', 0, '=', 1, '?', '=', 3, '=', 4, '=', 5, '?', '=', 6, '?', '?'));

    cms.add(new TestCallMatrix("aa1", Cut, Cut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '?', '=', 7, '<', 8));
    cms.add(new TestCallMatrix("aa2", Cut, Cut, '=', 0, '=', 1, '<', 2, '?', '=', 4, '=', 5, '=', 6, '?', '<', 8));
    cms.add(new TestCallMatrix("aa3", Cut, Cut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '=', 7, '<', 8));
    cms.add(new TestCallMatrix("aa4", Cut, Cut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '?', '<', 5, '-', '<', 6, '?', '?'));
    cms.add(new TestCallMatrix("aa5", Cut, Cut, '=', 0, '?', '?', '=', 3, '<', 4, '=', 5, '=', 6, '?', '?'));
    cms.add(new TestCallMatrix("aa6", Cut, Cut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '?', '=', 6, '?', '?'));
    cms.add(new TestCallMatrix("aa7", Cut, Cut, '=', 0, '<', 1, '=', 2, '?', '=', 4, '=', 5, '=', 6, '<', 7, '?'));
    cms.add(new TestCallMatrix("aa8", Cut, Cut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '<', 7, '=', 8));
    cms.add(new TestCallMatrix("aa9", Cut, Cut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '?', '=', 7, '<', 8));
    cms.add(new TestCallMatrix("aa10", Cut, Cut, '=', 0, '=', 1, '<', 2, '?', '=', 4, '=', 5, '=', 6, '?', '?'));
    cms.add(new TestCallMatrix("aa11", Cut, Cut, '=', 0, '<', 1, '=', 2, '=', 3, '=', 4, '?', '=', 6, '<', 7, '?'));
    cms.add(new TestCallMatrix("aa12", Cut, Cut, '=', 0, '<', 2, '?', '=', 3, '<', 4, '=', 5, '?', '<', 8, '?'));
    cms.add(new TestCallMatrix("aa13", Cut, Cut, '=', 0, '<', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '<', 7, '=', 8));
    cms.add(new TestCallMatrix("aa14", Cut, Cut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '=', 6, '=', 7, '<', 8));
    cms.add(new TestCallMatrix("aa15", Cut, Cut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '<', 6, '=', 7, '<', 8));
    cms.add(new TestCallMatrix("aa16", Cut, Cut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '?', '=', 7, '<', 6, '-', '<', 8));
    cms.add(new TestCallMatrix("aa17", Cut, Cut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '?', '=', 7, '?'));

    cms.add(new TestCallMatrix("bb1", CCut, CCut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '?', '=', 8, '<', 9));
    cms.add(new TestCallMatrix("bb2", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '=', 6, '?', '=', 8, '<', 7, '-', '<', 9));
    cms.add(new TestCallMatrix("bb3", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '?', '=', 6, '=', 7, '=', 8, '?'));
    cms.add(new TestCallMatrix("bb4", CCut, CCut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '=', 7, '=', 8, '<', 9));
    cms.add(new TestCallMatrix("bb5", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '=', 6, '-', '=', 7, '?', '=', 8, '?'));
    cms.add(new TestCallMatrix("bb6", CCut, CCut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '=', 7, '<', 8, '=', 9));
    cms.add(new TestCallMatrix("bb7", CCut, CCut, '=', 0, '=', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '<', 7, '=', 8, '<', 9));
    cms.add(new TestCallMatrix("bb8", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '=', 6, '=', 7, '=', 8, '<', 9));
    cms.add(new TestCallMatrix("bb9", CCut, CCut, '=', 0, '<', 1, '=', 2, '=', 3, '=', 4, '=', 5, '=', 6, '=', 7, '<', 8, '=', 9));
    cms.add(new TestCallMatrix("bb10", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '=', 5, '=', 6, '?', '=', 8, '<', 9));
    cms.add(new TestCallMatrix("bb11", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '?', '=', 6, '=', 7, '=', 8, '<', 9));
    cms.add(new TestCallMatrix("bb12", CCut, CCut, '=', 0, '=', 1, '<', 2, '?', '=', 4, '=', 5, '=', 6, '=', 7, '?', '?'));
    cms.add(new TestCallMatrix("bb13", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '?', '=', 6, '<', 5, '-', '<', 7, '=', 8, '<', 9));
    cms.add(new TestCallMatrix("bb14", CCut, CCut, '=', 0, '=', 1, '<', 2, '=', 3, '=', 4, '?', '=', 6, '<', 6, '-', '=', 7, '=', 8, '?'));

    BaseCallGraph<TestVertex> callGraph = new BaseCallGraph<>();
    callGraph.add(cms);
    assert callGraph.checkTermination().proj1;
  }

  @Test
  public void factorialTest() {
    typeCheckModule("""
      \\func bad_rec (x1 x2 x3 x4 x5 x6 x7 x8 x9 x10 : Nat) : Nat \\elim x1
        | zero => zero
        | suc x1 => bad_rec x2 x1 x3 x4 x5 x6 x7 x8 x9 x10 Nat.+ bad_rec x10 x1 x2 x3 x4 x5 x6 x7 x8 x9
      """, 1);
  }

  @Test
  public void factorialTest2() {
    typeCheckModule("""
      \\func bad_rec (x1 x2 x3 x4 x5 x6 x7 x8 x9 x10 : Nat) : Nat \\elim x1
        | zero => zero
        | suc x1 => bad_rec x1 x3 x2 x4 x5 x6 x7 x8 x9 x10 Nat.+ bad_rec x1 x10 x2 x3 x4 x5 x6 x7 x8 x9
      """, 0);
  }

  @Test
  public void testArray() {
    typeCheckModule("""
      \\func test {A : \\Type} (l l' : Array A) : Nat \\elim l, l'
        | nil, nil => 0
        | nil, a :: l => 0
        | a :: l, nil => 0
        | a :: l, a' :: l' => \\let | t1 => test l (a' :: l')
                                   | t2 => test (a :: l) l'
                              \\in 0
      """);
  }

  // Impredicative-Prop non-termination (Coquand-Paulin family): Bad is inhabited by the
  // polymorphic identity, so noBad bad loops. Correctly rejected -- the codomain of f is
  // a type variable, not a datatype, so the structural-descent guard refuses it.
  @Test
  public void nonRecursiveConstructor() {
    typeCheckModule("""
      \\data Bad : \\Prop
        | mkBad (\\Pi {P : \\Prop} -> P -> P)
      \\func bad : Bad => mkBad \\lam x => x
      \\func noBad (b : Bad) : Nat
        | mkBad f => noBad (f bad)
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(TerminationCheckError.class));
  }

  @Test
  public void recursiveArray() {
    typeCheckModule("""
      \\data Term
        | apply (Array Term)
      \\func test (t : Term) : Nat
        | apply l => \\let x i => test (l i) \\in 0
      """);
  }

  @Test
  public void testSCC() {
    typeCheckModule("""
      \\data Tree
        | leaf
        | node Tree Tree
      \\func foo (t : Tree) : Nat
        | leaf => 0
        | node t t' => bar t t'
      \\func bar (t t' : Tree) : Nat => foo t Nat.+ foo t'
    """);
  }

  @Test
  public void mutualRecursiveConstructor() {
    typeCheckModule("""
      \\data D
        | con1
        | con2 (Nat -> D)
        | con3 {d1 d2 : D} (E d1 d2) : d1 = d2
      \\data E (d1 d2 : D)
        | con4 (f : Nat -> D) (d1 = f 0) (d2 = con2 f)
      \\func foo {A : D -> \\Type} (B : \\Pi {d1 d2 : D} -> A d1 -> A d2 -> E d1 d2 -> \\Type) (a1 : A con1) (a2 : \\Pi {f : Nat -> D} -> (\\Pi (n : Nat) -> A (f n)) -> A (con2 f)) (a3 : \\Pi {d1 d2 : D} (Ad1 : A d1) (Ad2 : A d2) (e : E d1 d2) -> B Ad1 Ad2 e -> Path (\\lam i => A (con3 e i)) Ad1 Ad2) (a4 : \\Pi {d1 d2 : D} (Ad1 : A d1) (Ad2 : A d2) {f : Nat -> D} (Af : \\Pi (n : Nat) -> A (f n)) (p1 : d1 = f 0) (p2 : d2 = con2 f) -> B Ad1 Ad2 (con4 f p1 p2)) (d : D) : A d \\elim d
        | con1 => a1
        | con2 f => a2 (\\lam n => foo B a1 a2 a3 a4 (f n))
        | con3 {d1} {d2} e => a3 (foo B a1 a2 a3 a4 d1) (foo B a1 a2 a3 a4 d2) e (bar B a1 a2 a3 a4 (foo B a1 a2 a3 a4 d1) (foo B a1 a2 a3 a4 d2) e)
      \\func bar {A : D -> \\Type} (B : \\Pi {d1 d2 : D} -> A d1 -> A d2 -> E d1 d2 -> \\Type) (a1 : A con1) (a2 : \\Pi {f : Nat -> D} -> (\\Pi (n : Nat) -> A (f n)) -> A (con2 f)) (a3 : \\Pi {d1 d2 : D} (Ad1 : A d1) (Ad2 : A d2) (e : E d1 d2) -> B Ad1 Ad2 e -> Path (\\lam i => A (con3 e i)) Ad1 Ad2) (a4 : \\Pi {d1 d2 : D} (Ad1 : A d1) (Ad2 : A d2) {f : Nat -> D} (Af : \\Pi (n : Nat) -> A (f n)) (p1 : d1 = f 0) (p2 : d2 = con2 f) -> B Ad1 Ad2 (con4 f p1 p2)) {d1 d2 : D} (Ad1 : A d1) (Ad2 : A d2) (e : E d1 d2) : B Ad1 Ad2 e \\elim e
        | con4 f p1 p2 => a4 Ad1 Ad2 (\\lam n => foo B a1 a2 a3 a4 (f n)) p1 p2
      """);
  }

  // Changing-index (polymorphic) structural recursion: the recursive call is at a
  // different type instance (A -> Pair A A). Genuinely terminating; accepted (#130 fix).
  @Test
  public void changingIndex_powerTree() {
    typeCheckModule("""
      \\data Pair (A B : \\Type) | pair A B
      \\func mapPair {A B C D : \\Type} (f : A -> C) (g : B -> D) (p : Pair A B) : Pair C D \\elim p
        | pair x y => pair (f x) (g y)
      \\data PowerTree (A : \\Type) | leaf A | fork (PowerTree (Pair A A))
      \\func mapPowerTree {A B : \\Type} (f : A -> B) (t : PowerTree A) : PowerTree B \\elim t
        | leaf x => leaf (f x)
        | fork t => fork (mapPowerTree (mapPair f f) t)
      """, 0);
  }

  // Power-list / nested changing-index recursion (Bird-Meertens). Accepted (#130 fix).
  @Test
  public void changingIndex_powerList() {
    typeCheckModule("""
      \\data Pair (A B : \\Type) | pair A B
      \\func mapPair {A B C D : \\Type} (f : A -> C) (g : B -> D) (p : Pair A B) : Pair C D \\elim p
        | pair x y => pair (f x) (g y)
      \\data PowerList (A : \\Type) | pnil | pcons A (PowerList (Pair A A))
      \\func mapPowerList {A B : \\Type} (f : A -> B) (l : PowerList A) : PowerList B \\elim l
        | pnil => pnil
        | pcons x xs => pcons (f x) (mapPowerList (mapPair f f) xs)
      """, 0);
  }

  // Non-termination that MUST be rejected (soundness). Agda #6654 (fixed 2.6.4):
  // Agda <= 2.6.3 accepts this and proves Empty
  @Test
  public void largeIndexForcingLoop_rejected() {
    typeCheckModule("""
      \\data Empty
      \\func PolyId => \\Pi (P : \\Type) -> P -> P
      \\func identity : PolyId => \\lam P x => x
      \\data Indexed (f : PolyId) | tagged
      \\func seed : Indexed identity => tagged
      \\func descend {f : PolyId} (t : Indexed f) : Empty \\elim t
        | tagged => descend {identity} (f (Indexed identity) seed)
      """, -1);
  }

  // Cast/transport "successor" loop: the recursive argument reduces back to suc n (not smaller). Analogue of Agda #7568 (type-based termination). MUST be rejected.
  @Test
  public void castSuccessorLoop_rejected() {
    typeCheckModule("""
      \\data Empty
      \\func transport' {A : \\Type} (B : A -> \\Type) {a a' : A} (p : a = a') (b : B a) : B a' => coe (\\lam i => B (p @ i)) b right
      \\func inv' {A : \\Type} {a a' : A} (p : a = a') : a' = a => transport' (\\lam x => x = a) p idp
      \\func cast {X Y : \\Type} (e : X = Y) (x : X) : Y => transport' (\\lam T => T) e x
      \\func succ-through {X : \\Type} (e : X = Nat) (n : X) : X => cast (inv' e) (suc (cast e n))
      \\func loop (n : Nat) (imp : (n = 0) -> Empty) : Empty \\elim n
        | 0 => imp idp
        | suc n => loop (succ-through idp n) imp
      """, -1);
  }

  // Cubical HIT forcing loop: view uses coe along a path constructor to fabricate a PairView of any term; crash/peel then loop, cf. Agda #7346 (fixed 2.7.0).
  // Agda <= 2.6.x accepts and proves Empty.
  @Test
  public void hitForcingLoop_rejected() {
    typeCheckModule("""
      \\data Empty
      \\data Term
        | atom
        | pair Term Term
        | left-unit (x : Term) : pair atom x = x
      \\data PairView (t : Term)
        | pair-view (x y : Term) (t = pair x y)
      \\func rp {A : \\Type} {a : A} : a = a => path (\\lam _ => a)
      \\func view (x : Term) : PairView x => coe (\\lam i => PairView (left-unit x @ i)) (pair-view atom x rp) right
      \\func crash (t : Term) : Empty => peel t (view t)
      \\func peel (t : Term) (pv : PairView t) : Empty \\elim pv
        | pair-view x y p => crash x
      """, -1);
  }

  // TODO: Behavior is too stringent (cf. Agda #4702).
  // The recursion is structural but routed through a user-defined caseOf wrapper, which
  // Arend's termination checker does not see through.
  @Test
  public void caseWrapperRecursion_tooStrict() {
    typeCheckModule("""
      \\func caseOf {A B : \\Type} (x : A) (f : A -> B) : B => f x
      \\func add (m n : Nat) : Nat => caseOf m (\\lam m' => \\case m' \\with { | 0 => n | suc k => suc (add k n) })
      """, -1);
  }

  // TODO: Behavior is too stringent (cf. Agda #7615).
  // Structural recursion whose descent is on the implicit Bag index (g' < g), exposed by consLayer
  // through \case (grow seed); the syntactic checker does not track it.
  @Test
  public void hitImplicitIndexDescent_tooStrict() {
    typeCheckModule("""
      \\data Bag (A : \\Type)
        | emptyBag
        | consBag A (Bag A)
        | swapBag (x y : A) (xs : Bag A) : consBag x (consBag y xs) = consBag y (consBag x xs)
      \\data Layer {A : \\Type} (R : Bag A -> \\Type) (b : Bag A)
        | emptyLayer (b = emptyBag)
        | consLayer {g : Bag A} (x : A) (R g) (b = consBag x g)
      \\data IndexedList {A : \\Type} (b : Bag A)
        | emptyList (b = emptyBag)
        | consList {g : Bag A} (x : A) (IndexedList g) (b = consBag x g)
      \\func unfold {A : \\Type} {R : Bag A -> \\Type} (grow : \\Pi {g2 : Bag A} -> R g2 -> Layer R g2) {g : Bag A} (seed : R g) : IndexedList g
        => \\case grow seed \\with {
          | emptyLayer p => emptyList p
          | consLayer {g'} x s' q => consList x (unfold grow s') q
        }
      """, -1);
  }

  // TODO: Behavior is too stringent
  // Bush is a valid truly-nested datatype that Agda accepts. Bush (Bush A)), so a mapBush cannot even be written. cf. "Nested Inductive Types".
  @Test
  public void bushTrulyNested_tooStrict() {
    typeCheckModule("""
      \\data Bush (A : \\Type) | bempty | bpush A (Bush (Bush A))
      """, -1);
  }

  // TODO: Behavior is too stringent (cf. Agda #7669).
  // At u DNat reduces to DNat (positive), but Arend's syntactic positivity check flags the occurrence under the stuck At u _ .
  @Test
  public void definitionalEqualityPositivity_tooStrict() {
    typeCheckModule("""
      \\data One | unit
      \\func At (u : One) (X : \\Type) : \\Type \\elim u | unit => X
      \\data DNat | dzero | dsuc (u : One) (At u DNat)
      """, -1);
  }

  @Test
  public void testClearLemmaBodies() {
    typeCheckModule("""
      \\lemma foobar : Nat => foobar
    """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(TerminationCheckError.class));
  }
}
