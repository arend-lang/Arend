package org.arend.typechecking.patternmatching;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.elimtree.ElimBody;
import org.arend.core.expr.*;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.Levels;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.local.HigherConstructorMatchingError;
import org.arend.util.SingletonList;
import org.junit.Test;

import java.util.Collections;
import java.util.Objects;

import static org.arend.Matchers.*;
import static org.arend.core.expr.ExpressionFactory.*;

public class ConditionsTest extends TypeCheckingTestCase {
  @Test
  public void dataTypeWithConditions() {
    typeCheckModule(
        "\\data Z | zneg Nat | zpos Nat { zero => zneg zero }");
  }

  @Test
  public void dataTypeWithConditionsWrongType() {
    typeCheckModule(
        "\\data Z | zneg Nat | zpos Nat { zero => zero }", 1);
  }

  @Test
  public void dataTypeWithConditionsTCFailed1() {
    typeCheckModule(
        "\\data Z | zneg Nat | zpos Nat { zero => zpos 1 }", 1);
  }

  @Test
  public void dataTypeWithConditionsTCFailed2() {
    typeCheckModule(
        "\\data Unit | unit\n" +
        "\\data Z | zneg | zpos Unit { _ => zpos }", 1);
  }

  @Test
  public void dataTypeWithConditionsMutualDep() {
    typeCheckModule(
        "\\data Unit | unit\n" +
        "\\data Z | zpos Unit { _ => zneg unit } | zneg Unit { _ => zpos unit }", 1);
  }

  @Test
  public void simpleTest() {
    typeCheckModule("""
      \\data Z | zpos Nat | zneg Nat { zero => zpos zero }
      \\func test (x : Z) : Nat
        | zneg (suc (suc _)) => 0
        | zneg (suc zero) => 1
        | zneg zero => 2
        | zpos x => suc (suc x)
      """);
  }

  @Test
  public void simpleTestError() {
    typeCheckModule("""
      \\data Z | zpos Nat | zneg Nat { zero => zpos zero }
      \\func test (x : Z) : Nat
        | zneg (suc (suc _)) => 0
        | zneg (suc zero) => 1
        | zneg zero => 2
        | zpos x => suc x
      """, 1);
  }

  @Test
  public void multipleArgTest() {
    typeCheckModule("""
      \\data Z | negative Nat | positive Nat { zero => negative zero }
      \\func test (x : Z) (y : Nat) : Nat
        | positive (suc n), m => n
        | positive zero, m => m
        | negative n, zero => zero
        | negative n, suc m => suc m
      """, 1);
  }

  @Test
  public void multipleArgTestError() {
    typeCheckModule("""
      \\data Z | negative Nat | positive Nat { zero => negative zero }
      \\func test (x : Z) (y : Nat) : Nat
        | positive (suc n), m => n
        | positive zero, m => m
        | negative n, zero => zero
        | negative n, suc m => suc (suc m)
      """, 1);
  }

  @Test
  public void bidirectionalList() {
    typeCheckModule("""
      \\data BD-list (A : \\Type0) | nil | cons A (BD-list A) | snoc (xs : BD-list A) (y : A) \\elim xs
        { | cons x xs => cons x (snoc xs y) | nil => cons y nil }
      \\func length {A : \\Type0} (x : BD-list A) : Nat \\elim x
        | nil => 0
        | cons x xs => suc (length xs)
        | snoc xs x => suc (length xs)
      """, 1);
  }

  @Test
  public void dataTypeWithIndices() {
    typeCheckModule("""
      \\data S | base | loop I
        { left => base
        | right => base
        }
      \\data D Nat \\with | _ => d | zero => di I
        { | left => d | right => d }
      \\func test (x : Nat) (y : D x) : S
        | suc _, d => base
        | zero, d => base
        | zero, di i => loop i
      """);
  }

  @Test
  public void testSelfConditionsError() {
    typeCheckModule("""
      \\data Unit | unit
      \\data D
        | nil0
        | nil1 Unit { _ => nil0 }
        | cons1 D
        | cons2 D
        | cons0 D { | nil0 => cons1 nil0 | nil1 x => cons2 (nil1 x) }
      """, 1);
  }

  @Test
  public void testSelfConditions() {
    typeCheckModule("""
      \\data Unit | unit
      \\data D
        | nil0
        | nil1 Unit { _ => nil0 }
        | cons1 D
        | cons2 D { x => cons1 x }
        | cons0 D { | nil0 => cons1 nil0 | nil1 x => cons2 (nil1 x) }
      """);
  }

  @Test
  public void nestedCheck() {
    typeCheckModule("""
      \\data Z | pos Nat | neg Nat { zero => pos zero }
      \\func test (x y z : Z) : Nat
        | pos zero, pos zero, neg zero => 0
        | _, _, _ => 1
      """, -1);
  }

  @Test
  public void nonStatic() {
    typeCheckClass("""
      | S' : \\Type0
      | base' : S'
      | loop' : I -> S'
      \\data S | base | loop I
        { left => base
        | right => base
        }
      \\func test (s : S) : S'
        | base => base'
        | loop i => loop' i
      """, "", 2);
  }

  @Test
  public void constructorArgumentWithCondition() {
    typeCheckModule(
        "\\data S | base | loop Nat { zero => base }\n" +
        "\\data D | cons' | cons S { loop zero => cons' }", 1);
  }

  @Test
  public void cc() {
    typeCheckModule("""
      \\data Z | pos Nat | neg Nat { zero => pos zero }
      \\func test (z : Z) : Nat
        | pos n => 0
        | neg (suc n) => 1
      """);
  }

  @Test
  public void ccOtherDirectionError() {
    typeCheckModule("""
      \\data Z | pos Nat | neg Nat { zero => pos zero }
      \\func test (z : Z) : Nat
        | pos (suc n) => 0
        | neg n => 1
      """, 2);
  }

  @Test
  public void ccComplexBranch() {
    typeCheckModule("""
      \\data D | snd | fst Nat { | zero => snd | suc _ => snd }
      \\func test (d : D) : Nat
        | snd => zero
      """, 1);
  }

  @Test
  public void whatIfNormalizeError() {
    typeCheckModule("""
      \\data Z | pos Nat | neg Nat { zero => pos zero }
      \\func test (x : Z) : Nat
        | neg x => 1
        | pos x => 2
      """, 1);
  }

  @Test
  public void whatIfDontNormalizeConditionRHS() {
    typeCheckModule("""
      \\data Unit | unit
      \\data D | d2 | d1 Unit { _ => d2 }
      \\data E | e1 D | e2 D { _ => e1 (d1 unit) }
      \\func test (e : E) : Nat
        | e2 d2 => 1
        | e1 (d1 _) => 2
        | e1 d2 => 1
      """, 1);
  }

  @Test
  public void dataIntervalCondition() {
    typeCheckModule("\\data D I \\with | left => c", 1);
  }

  @Test
  public void dataCondition2() {
    typeCheckModule("""
      \\data D | c | l Nat
        { 3 => c
        }
      \\data E D \\with
       | l 2 => e
      \\data E' D \\with
        | l (suc (suc (suc (suc x)))) => e'
      """);
  }

  @Test
  public void dataConditionError() {
    typeCheckModule("""
      \\data D | c | c' | l I
        { left => c
        | right => c'
        }
      \\data E D \\with
        | l i => e
      """, 1);
  }

  @Test
  public void dataConditionError2() {
    typeCheckModule("""
      \\data D | c | l Nat
        { 3 => c
        }
      \\data E D \\with
        | l (suc (suc (suc x))) => e
      """, 1);
  }

  @Test
  public void dataConditionError3() {
    typeCheckModule("""
      \\data D | c | l Nat
        { suc x => c
        }
      \\data E D \\with
        | l (suc (suc x)) => e
      """, 1);
  }

  @Test
  public void dataConditionEmptyPatternError() {
    typeCheckModule("""
      \\data D | c | c' | l I
        { left => c
        | right => c'
        }
      \\data E D \\with
        | () => e
      """, 1);
  }

  @Test
  public void partialIntervalCondition() {
    typeCheckModule("\\data D | con1 | con2 I { | left => con1 }");
  }

  @Test
  public void partialIntervalConditionError() {
    typeCheckModule("\\data D | con1 | con2 I { | left => con2 right }", 1);
  }

  @Test
  public void goalTest() {
    typeCheckModule("""
      \\data II | point1 | point2 | seg (i : I) \\with { | left => point1 | right => point2 }
      \\func f (x : II) : Nat
        | point2 => 7
        | point1 => 3
        | seg i => {?}
      """, 1);
    DependentLink binding = ((ElimBody) Objects.requireNonNull(((FunctionDefinition) getDefinition("f")).getBody())).getClauses().get(2).getPatterns().getFirst().getFirstBinding();
    assertThatErrorsAre(goalError(
      new Condition(null, new ExprSubstitution(binding, Left()), new SmallIntegerExpression(3)),
      new Condition(null, new ExprSubstitution(binding, Right()), new SmallIntegerExpression(7))));
  }

  @Test
  public void goalCaseTest() {
    typeCheckModule("""
      \\data II | point1 | point2 | seg (i : I) \\with { | left => point1 | right => point2 }
      \\func f (x : II) : Nat => \\case x \\with {
        | point2 => 7
        | point1 => 3
        | seg i => {?}
      }
      """, 1);
    DependentLink binding = ((CaseExpression) Objects.requireNonNull(((FunctionDefinition) getDefinition("f")).getBody())).getElimBody().getClauses().get(2).getPatterns().getFirst().getFirstBinding();
    assertThatErrorsAre(goalError(
      new Condition(null, new ExprSubstitution(binding, Left()), new SmallIntegerExpression(3)),
      new Condition(null, new ExprSubstitution(binding, Right()), new SmallIntegerExpression(7))));
  }

  @Test
  public void goalTest2() {
    typeCheckModule("""
      \\data S1 | base | loop (i : I) \\with { | left => base | right => base }
      \\func f (x y : S1) : S1
        | base, y => y
        | loop i, base => loop i
        | loop i, loop j => {?}
      """, 1);

    DependentLink i = ((ElimBody) Objects.requireNonNull(((FunctionDefinition) getDefinition("f")).getBody())).getClauses().get(2).getPatterns().getFirst().getFirstBinding();
    DependentLink j = i.getNext();
    Constructor loop = (Constructor) getDefinition("S1.loop");
    Expression iResult = ConCallExpression.make(loop, Levels.EMPTY, Collections.emptyList(), new SingletonList<>(new ReferenceExpression(j)));
    Expression jResult = ConCallExpression.make(loop, Levels.EMPTY, Collections.emptyList(), new SingletonList<>(new ReferenceExpression(i)));

    assertThatErrorsAre(goalError(
      new Condition(null, new ExprSubstitution(i, Left()), iResult), new Condition(null, new ExprSubstitution(i, Right()), iResult),
      new Condition(null, new ExprSubstitution(j, Left()), jResult), new Condition(null, new ExprSubstitution(j, Right()), jResult)));
  }

  @Test
  public void goalCaseTest2() {
    typeCheckModule("""
      \\data S1 | base | loop (i : I) \\with { | left => base | right => base }
      \\func f (x y : S1) : S1 => \\case x, y \\with {
        | base, y => y
        | loop i, base => loop i
        | loop i, loop j => {?}
      }
      """, 1);

    DependentLink i = ((CaseExpression) Objects.requireNonNull(((FunctionDefinition) getDefinition("f")).getBody())).getElimBody().getClauses().get(2).getPatterns().getFirst().getFirstBinding();
    DependentLink j = i.getNext();
    Constructor loop = (Constructor) getDefinition("S1.loop");
    Expression iResult = ConCallExpression.make(loop, Levels.EMPTY, Collections.emptyList(), new SingletonList<>(new ReferenceExpression(j)));
    Expression jResult = ConCallExpression.make(loop, Levels.EMPTY, Collections.emptyList(), new SingletonList<>(new ReferenceExpression(i)));

    assertThatErrorsAre(goalError(
      new Condition(null, new ExprSubstitution(i, Left()), iResult), new Condition(null, new ExprSubstitution(i, Right()), iResult),
      new Condition(null, new ExprSubstitution(j, Left()), jResult), new Condition(null, new ExprSubstitution(j, Right()), jResult)));
  }

  @Test
  public void goalTest3() {
    typeCheckModule("""
      \\func f (x : Int) : Nat
        | pos n => suc (suc n)
        | neg n => {?}
      """, 1);
    DependentLink binding = ((ElimBody) Objects.requireNonNull(((FunctionDefinition) getDefinition("f")).getBody())).getClauses().get(1).getPatterns().getFirst().getFirstBinding();
    assertThatErrorsAre(goalError(new Condition(null, new ExprSubstitution(binding, Zero()), new SmallIntegerExpression(2))));
  }

  @Test
  public void goalTest4() {
    typeCheckModule("""
      \\func f (x : Int) : Nat
        | pos n => n
        | neg n => {?(suc n)}
      """, 1);
    DependentLink binding = ((ElimBody) Objects.requireNonNull(((FunctionDefinition) getDefinition("f")).getBody())).getClauses().get(1).getPatterns().getFirst().getFirstBinding();
    assertThatErrorsAre(goalError(new Condition(null, new ExprSubstitution(binding, Zero()), new SmallIntegerExpression(0))));
  }

  @Test
  public void goalPathConditionsTest() {
    typeCheckModule(
      "\\data S1 | base | loop (i : I) \\with { | left => base | right => base }\n" +
      "\\func f (x : S1) : base = x => path (\\lam i => {?})", 1);

    FunctionDefinition f = (FunctionDefinition) getDefinition("f");
    DependentLink binding = ((LamExpression) ((PathExpression) Objects.requireNonNull(f.getBody())).getArgument()).getParameters();
    Constructor base = (Constructor) getDefinition("S1.base");
    assertThatErrorsAre(goalError(
      new Condition(null, new ExprSubstitution(binding, Left()), ConCallExpression.make(base, Levels.EMPTY, Collections.emptyList(), Collections.emptyList())),
      new Condition(null, new ExprSubstitution(binding, Right()), new ReferenceExpression(f.getParameters()))));
  }

  @Test
  public void varPattern() {
    typeCheckModule("""
      \\data D | con1 | con2 | con3 (i : I) \\with { | left => con1 | right => con2 }
      \\func f (d : D) : Nat
        | con1 => 0
        | con2 => 1
        | _ => 2
      """, 1);
    assertThatErrorsAre(typecheckingError(HigherConstructorMatchingError.class));
  }

  @Test
  public void varPattern2() {
    typeCheckModule("""
      \\data S1 | base | base2 | loop I \\with { | left => base | right => base }
      \\func f (x y : S1) : S1
        | base, y => y
        | base2, y => y
        | x, base => x
        | x, base2 => x
        | loop i, loop j => {?}
      """, 2);
    assertThatErrorsAre(goal(2), typecheckingError(HigherConstructorMatchingError.class));
  }

  @Test
  public void constructorsOnlyOnTopLevel() {
    typeCheckModule("""
      \\func \\infixr 5 *> {A : \\Type} {a a' a'' : A} (p : a = a') (q : a' = a'')
        => coe (\\lam i => a = q @ i) p right
      \\data D
        | base
        | loop I \\with { | left => base | right => base }
        | loop2 (i j : I) \\elim i { | left => base | right => (path loop *> path loop) @ j }
      """, 1);
  }

  @Test
  public void sfunc() {
    typeCheckModule("""
      \\data D (A : \\Type)
        | con A
        | pathCon (a a' : A) (i : I) \\elim i {
          | left => con a
          | right => con a'
        }
      \\sfunc f (d : D Nat) : Nat
        | con _ => 0
        | pathCon _ _ _ => 0
      """);
  }
}
