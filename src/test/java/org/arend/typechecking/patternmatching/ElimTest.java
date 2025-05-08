package org.arend.typechecking.patternmatching;

import org.arend.Matchers;
import org.arend.core.context.binding.Binding;
import org.arend.core.context.binding.TypedBinding;
import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.elimtree.ElimBody;
import org.arend.core.elimtree.ElimClause;
import org.arend.core.expr.Expression;
import org.arend.core.expr.ReferenceExpression;
import org.arend.core.pattern.BindingPattern;
import org.arend.core.pattern.ConstructorPattern;
import org.arend.core.pattern.Pattern;
import org.arend.core.subst.Levels;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.prelude.Prelude;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.local.ImpossibleEliminationError;
import org.arend.typechecking.error.local.PathEndpointMismatchError;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.arend.ExpressionFactory.*;
import static org.arend.Matchers.goal;
import static org.arend.core.expr.ExpressionFactory.*;
import static org.junit.Assert.assertEquals;

public class ElimTest extends TypeCheckingTestCase {
  @Test
  public void elim2() {
    typeCheckModule("""
      \\data D Nat (x y : Nat) | con1 Nat | con2 (Nat -> Nat) (a b c : Nat)
      \\func P (a1 b1 c1 : Nat) (d1 : D a1 b1 c1) (a2 b2 c2 : Nat) (d2 : D a2 b2 c2) : \\oo-Type0 \\elim d1
        | con2 _ _ _ _ => Nat -> Nat
        | con1 _ => Nat
      \\func test (q w : Nat) (e : D w 0 q) (r : D q w 1) : P w 0 q e q w 1 r \\elim e, r
        | con2 x y z t, con1 s => x
        | con1 _, con1 s => s
        | con1 s, con2 x y z t => x q
        | con2 _ y z t, con2 x y' z' t' => x
      """);
  }

  @Test
  public void elim3() {
    typeCheckModule("""
      \\data D (x : Nat -> Nat) (y : Nat) | con1 {Nat} Nat | con2 (Nat -> Nat) {a b c : Nat}
      \\func test (q : Nat -> Nat) (e : D q 0) (r : D (\\lam x => x) (q 1)) : Nat \\elim e, r
        | con2 _ {y} {z} {t}, con1 s => q t
        | con1 {z} _, con1 s => z
        | con1 s, con2 y => y s
        | con2 _ {a} {b}, con2 y => y (q b)
      """);
  }

  @Test
  public void elim3_() {
    typeCheckModule("""
      \\data D (x : Nat -> Nat) (y : Nat) | con1 {Nat} Nat | con2 (Nat -> Nat) {a b c : Nat}
      \\func test (q : Nat -> Nat) (e : D q 0) (r : D (\\lam x => x) (q 1)) : Nat \\elim e, r
        | con2 _ {y} {z} {t}, con1 s => q t
        | con1 {z} _, con1 s => z
        | con1 s, con2 y => y s
        | con2 _ {a} {zero} {c}, con2 y => y (q a)
        | con2 _ {a} {suc b} {c}, con2 y => y (q b)
      """);
  }

  @Test
  public void elim4() {
    typeCheckModule(
        "\\func test (x : Nat) : Nat | zero => 0 | _ => 1\n" +
        "\\func test2 (x : Nat) : 1 = 1 => path (\\lam _ => test x)", 2);
    assertThatErrorsAre(Matchers.typecheckingError(PathEndpointMismatchError.class), Matchers.typecheckingError(PathEndpointMismatchError.class));
  }

  @Test
  public void elim5() {
    typeCheckModule(
        "\\data D Nat \\with | zero => d0 | suc n => d1\n" +
        "\\func test (x : D 0) : Nat | d0 => 0");
  }

  @Test
  public void elimUnknownIndex1() {
    typeCheckModule(
        "\\data D Nat \\with | zero => d0 | suc _ => d1\n" +
        "\\func test (x : Nat) (y : D x) : Nat \\elim y | d0 => 0 | d1 => 1", 2);
  }

  @Test
  public void elimUnknownIndex2() {
    typeCheckModule(
        "\\data D Nat \\with | zero => d0 | suc _ => d1\n" +
        "\\func test (x : Nat) (y : D x) : Nat \\elim y | d0 => 0 | _ => 1", 1);
  }

  @Test
  public void elimUnknownIndex3() {
    typeCheckModule(
        "\\data D Nat \\with | zero => d0 | suc _ => d1\n" +
        "\\func test (x : Nat) (y : D x) : Nat \\elim y | _ => 0", 0);
  }

  @Test
  public void elimUnknownIndex4() {
    typeCheckModule("""
      \\data E | A | B | C
      \\data D E \\with | A => d0 | B => d1 | _ => d2
      \\func test (x : E) (y : D x) : Nat \\elim y | d0 => 0 | d1 => 1
      """, 2);
  }

  @Test
  public void elimUnknownIndex5() {
    typeCheckModule("""
      \\data E | A | B | C
      \\data D E \\with | A => d0 | B => d1 | _ => d2
      \\func test (x : E) (y : D x) : Nat \\elim y | d0 => 0 | d1 => 1 | d2 => 2
      """, 2);
  }

  @Test
  public void elimUnknownIndex6() {
    typeCheckModule("""
      \\data E | A | B | C
      \\data D E \\with | A => d0 | B => d1 | _ => d2
      \\func test (x : E) (y : D x) : Nat \\elim y | d0 => 0 | d1 => 1 | _ => 2
      """, 2);
  }

  @Test
  public void elimUnknownIndex7() {
    typeCheckModule("""
      \\data E | A | B | C
      \\data D E \\with | A => d0 | B => d1 | _ => d2
      \\func test (x : E) (y : D x) : Nat \\elim y | _ => 0
      """);
  }

  @Test
  public void elimTooManyArgs() {
    typeCheckModule("\\data A | a Nat Nat \\func test (a : A) : Nat | a _ _ _ => 0", 1);
  }

  @Test
  public void elim6() {
    typeCheckModule(
        "\\data D | d Nat Nat\n" +
        "\\func test (x : D) : Nat | d zero zero => 0 | d (suc _) _ => 1 | d _ (suc _) => 2");
  }

  @Test
  public void elim7() {
    typeCheckModule(
        "\\data D | d Nat Nat\n" +
        "\\func test (x : D) : Nat | d zero zero => 0 | d (suc (suc _)) zero => 0", 1);
  }

  @Test
  public void elim8() {
    typeCheckModule(
        "\\data D | d Nat Nat\n" +
        "\\func test (x : D) : Nat | d zero zero => 0 | d _ _ => 1");
    FunctionDefinition test = (FunctionDefinition) getDefinition("test");
    Constructor d = (Constructor) getDefinition("D.d");
    Binding binding = new TypedBinding("y", Nat());
    Expression call1 = ConCall(d, Levels.EMPTY, Collections.emptyList(), Zero(), Ref(binding));
    Expression call2 = ConCall(d, Levels.EMPTY, Collections.emptyList(), Suc(Zero()), Ref(binding));
    assertEquals(FunCall(test, Levels.EMPTY, call1), FunCall(test, Levels.EMPTY, call1).normalize(NormalizationMode.NF));
    assertEquals(Suc(Zero()), FunCall(test, Levels.EMPTY, call2).normalize(NormalizationMode.NF));
  }

  @Test
  public void elim9() {
    typeCheckModule(
        "\\data D Nat \\with | suc n => d1 | _ => d | zero => d0\n" +
        "\\func test (n : Nat) (a : D (suc n)) : Nat \\elim a | d => 0", 1);
  }

  @Test
  public void elim10() {
    typeCheckModule("""
      \\data Bool | true | false
      \\func tp : \\Pi (x : Bool) -> \\oo-Type0 => \\lam x => \\case x \\with {
        | true => Bool
        | false => Nat
      }
      \\func f (x : Bool) : tp x
        | true => true
        | false => zero
      """);
  }

  @Test
  public void elimEmptyBranch() {
    typeCheckModule(
        "\\data D Nat \\with | suc n => dsuc\n" +
        "\\func test (n : Nat) (d : D n) : Nat | zero, () | suc n, dsuc => 0");
  }

  @Test
  public void elimEmptyBranchError() {
    typeCheckModule(
        "\\data D Nat \\with | suc n => dsuc\n" +
        "\\func test (n : Nat) (d : D n) : Nat | suc n, () | zero, ()", 1);
  }

  @Test
  public void testNoPatterns() {
    typeCheckModule("\\func test (n : Nat) : 0 = 1 \\elim n", 1);
  }

  @Test
  public void testAbsurdPattern() {
    typeCheckModule("\\func test (n : Nat) : 0 = 1 \\elim n | ()", 1);
  }

  @Test
  public void testAuto() {
    typeCheckModule(
        "\\data Empty\n" +
        "\\func test (n : Nat) (e : Empty) : Empty \\elim n, e");
  }

  @Test
  public void testAuto1() {
    typeCheckModule("""
      \\data Geq Nat Nat \\with | _, zero => Geq-zero | suc n, suc m => Geq-suc (Geq n m)
      \\func test (n m : Nat) (p : Geq n m) : Nat
        | _, zero, Geq-zero => 0
        | suc n, suc m, Geq-suc p => 1
      """);
  }

  @Test
  public void testAutoNonData() {
    typeCheckModule("""
      \\data D Nat \\with | zero => dcons
      \\data E (n : Nat) (Nat -> Nat) (D n) | econs
      \\func test (n : Nat) (d : D n) (e : E n (\\lam x => x) d) : Nat
        | zero, dcons, econs => 1
      """);
  }

  @Test
  public void testElimNeedNormalize() {
    typeCheckModule("""
      \\data D Nat \\with | suc n => c
      \\func f => D (suc zero)
      \\func test (x : f) : Nat
        | c => 0
      """);
  }

  @Test
  public void elimFail() {
      typeCheckModule("""
        \\func test (x y : Nat) : y = 0
          | _, zero => idp
          | zero, suc y' => test zero y'
          | suc x', suc y' => test (suc x') y'
        \\func zero-is-one : 1 = 0 => test 0 1
        """, 2);
  }

  @Test
  public void testSmthing() {
    typeCheckModule("""
      \\data Geq Nat Nat \\with
        | m, zero => EqBase\s
        | suc n, suc m => EqSuc (p : Geq n m)
      
      \\func f (x y : Nat) (p : Geq x y) : Nat =>
        \\case x, y, p \\with {
          | m, zero, EqBase => zero\s
          | zero, suc _, ()
          | suc _, suc _, EqSuc q => suc zero
        }
      """, 3);
  }

  @Test
  public void testElimOrderError() {
    typeCheckModule("""
      \\data \\infix 4
      =< Nat Nat \\with
        | zero, m => le_z
        | suc n, suc m => le_ss (n =< m)
      
      \\func leq-trans {n m k : Nat} (nm : n =< m) (mk : m =< k) : n =< k \\elim n, nm, m
        | zero, le_z, _ => {?}
        | suc n', le_ss nm', suc m' => {?}
      """, 1);
  }

  @Test
  public void testEmptyNoElimError() {
    typeCheckModule("""
      \\data D Nat \\with | zero => d0
      \\func test (x : Nat) (d : D x) : Nat \\elim d
        | () => 0
      """, 1);
  }

  @Test
  public void testElimTranslationSubst() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef(
      "\\func test (n m : Nat) : Nat \\elim m\n" +
        " | _ => n"
    );
    assertEquals(new ElimBody(Collections.singletonList(new ElimClause<>(Arrays.asList(new BindingPattern(def.getParameters()), new BindingPattern(def.getParameters().getNext())), new ReferenceExpression(def.getParameters()))), null), def.getBody());
  }

  @Test
  public void testElimTranslationSubst2() {
    FunctionDefinition def = (FunctionDefinition) typeCheckDef("""
      \\func test (n m : Nat) : Nat \\elim m
       | zero => n
       | _ => n
     """);
    DependentLink nParam = DependentLink.Helper.take(def.getParameters(), 1);
    List<ElimClause<Pattern>> clauses = new ArrayList<>(2);
    clauses.add(new ElimClause<>(Arrays.asList(new BindingPattern(nParam), ConstructorPattern.make(Prelude.ZERO, Collections.emptyList())), new ReferenceExpression(nParam)));
    clauses.add(new ElimClause<>(Arrays.asList(new BindingPattern(def.getParameters()), new BindingPattern(def.getParameters().getNext())), new ReferenceExpression(def.getParameters())));
    assertEquals(new ElimBody(clauses, null), def.getBody());
  }

  @Test
  public void testElimTranslationSubst3() {
    typeCheckModule("""
      \\data D | A | B | C
      \\func f (n m : D) : D \\elim m
       | A => n
       | _ => n
     """);
    FunctionDefinition def = (FunctionDefinition) getDefinition("f");
    DependentLink nParam = DependentLink.Helper.take(def.getParameters(), 1);
    List<ElimClause<Pattern>> clauses = new ArrayList<>(3);
    clauses.add(new ElimClause<>(Arrays.asList(new BindingPattern(nParam), ConstructorPattern.make(getDefinition("D.A"), Collections.emptyList())), new ReferenceExpression(nParam)));
    clauses.add(new ElimClause<>(Arrays.asList(new BindingPattern(def.getParameters()), new BindingPattern(def.getParameters().getNext())), new ReferenceExpression(def.getParameters())));
    assertEquals(new ElimBody(clauses, null), def.getBody());
  }

  @Test
  public void emptyAfterAFewError() {
    typeCheckModule("""
      \\data D Nat \\with | zero => d
      \\func test (x : Nat) (y : \\Pi(z : Nat) -> x = z) (a : D (suc x)) : Nat \\elim x
      """, 1);
  }

  @Test
  public void emptyAfterAFew() {
    typeCheckModule("""
      \\data D Nat \\with | zero => d
      \\func test (x : Nat) (y : \\Pi(z : Nat) -> x = z) (a : D (suc x)) : Nat \\elim a
      """);
  }

  @Test
  public void testElimEmpty1() {
    typeCheckModule("""
      \\data D Nat \\with | zero => d1 | suc zero => d2\s
      \\data E (n : Nat) | e (D n)
      \\func test (n : Nat) (e : E n) : Nat
        | zero, _ => 0
        | suc zero, _ => 1
        | suc (suc _), e ()
      """);
  }

  @Test
  public void testMultiArg() {
    typeCheckModule("""
      \\data D (A B : \\Type0) | c A B
      \\func test (f : Nat -> Nat) (d : D Nat (Nat -> Nat)) : Nat \\elim d
        | c x y => f x
      """);
  }

  @Test
  public void testEmptyCase() {
    typeCheckModule(
        "\\data D\n" +
        "\\func test (d : D) : 0 = 1 => \\case d \\with { () }"
    );
  }

  @Test
  public void threeVars() {
    typeCheckModule("""
      \\func f (x y z : Nat) : Nat
        | zero, zero, zero => zero
        | zero, zero, suc k => k
        | zero, suc m, zero => m
        | zero, suc m, suc k => k
        | suc n, zero, zero => n
        | suc n, zero, suc k => k
        | suc n, suc m, zero => m
        | suc n, suc m, suc k => n
      """);
  }

  @Test
  public void dependentElim() {
    typeCheckModule("""
      \\data Bool | true | false
      \\func if (b : Bool) : \\Set | true => Nat | false => Nat -> Nat
      \\func test (b : Bool) (x : if b) : Nat | true, zero => 0 | true, suc n => n | false, _ => 0
      """);
  }

  @Test
  public void numberElim() {
    typeCheckModule("\\func f (n : Nat) : Nat | 2 => 0 | 0 => 1 | 1 => 2 | suc (suc (suc n)) => n");
  }

  @Test
  public void numberElim2() {
    typeCheckModule("\\func f (n : Nat) : Nat | 0 => 1 | 1 => 2 | suc (suc (suc n)) => n", 1);
  }

  @Test
  public void threePatternsEval() {
    typeCheckModule("""
      \\open Nat(+,*)
      \\func f (n m k : Nat) : Nat
        | zero, m, zero => m
        | n, zero, suc k => 10 * n + k
        | n, m, k => 100 * n + 10 * m + k
      \\func test1 : f 0 2 0 = 2 => idp
      \\func test2 : f 1 0 3 = 12 => idp
      \\func test3 : f 1 2 3 = 123 => idp
      """);
  }

  @Test
  public void threePatterns() {
    typeCheckModule("""
      \\func f (n m k : Nat) : Nat
        | zero, _, zero => 1
        | _, zero, suc _ => 2
        | _, _, _ => 0
      \\func g (n : Nat) : f 0 n 0 = 1 => idp
      """, 1);
  }

  @Test
  public void threePatterns2() {
    typeCheckModule("""
      \\func f (n m k : Nat) : Nat
        | zero, zero, _ => 1
        | _, zero, zero => 2
        | _, _, _ => 0
      \\func g (n : Nat) : f 0 0 n = 1 => idp
      """, 1);
  }

  @Test
  public void threePatternsError() {
    typeCheckModule("""
      \\func f (n m k : Nat) : Nat
        | _, zero, zero => 1
        | zero, zero, _ => 2
        | _, _, _ => 0
      \\func g (n : Nat) : f 0 0 n = 2 => idp
      """, 1);
  }

  @Test
  public void elimExpression() {
    parseModule("""
      \\func + (a b : Nat) => a
      \\func f (a b : Nat) : Nat \\elim (a + b)
        | zero => zero
        | suc n' => zero
      """, -1);
  }

  @Test
  public void testAbsurd() {
    typeCheckModule("""
      \\data Bool | true | false
      \\func not (b : Bool) : Bool | true => false | false => true
      \\data T (b : Bool) \\with | true => tt
      \\func f (b : Bool) (p : T (not b)) : Nat
        | false, tt => 0
      """);
  }

  @Test
  public void doubleVarError() {
    typeCheckModule(
      "\\func f (n : Nat) : Nat \\elim n, n\n" +
      "  | _, _ => 0", 1);
  }

  @Test
  public void elimNoClauses() {
    typeCheckModule("""
      \\data D (n : Nat) \\with
        | 0 => con
      \\func f (n : Nat) (d : D n) : Nat \\elim d
      """, 1);
    assertThatErrorsAre(Matchers.typecheckingError(ImpossibleEliminationError.class));
  }

  @Test
  public void contextTest() {
    typeCheckModule("""
      \\func f (n : Nat) : Nat \\elim n
        | 0 => 0
        | suc n => {?}
      """, 1);
    assertThatErrorsAre(goal(1));
  }

  @Test
  public void contextTest2() {
    typeCheckModule("""
      \\func f (n m : Nat) : Nat
        | _, 0 => 0
        | _, suc m => {?}
      """, 1);
    assertThatErrorsAre(goal(1));
  }

  @Test
  public void contextTest3() {
    typeCheckModule("""
      \\func f (n m k l : Nat) : Nat \\elim n, m, l
        | _, suc m, suc _ => {?}
        | _, _, _ => 0
      """, 1);
    assertThatErrorsAre(goal(2));
  }
}
