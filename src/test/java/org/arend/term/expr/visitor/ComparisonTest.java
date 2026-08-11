package org.arend.term.expr.visitor;

import org.arend.core.context.binding.Binding;
import org.arend.core.context.binding.TypedBinding;
import org.arend.core.context.binding.inference.ExpressionInferenceVariable;
import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.SingleDependentLink;
import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.DataCallExpression;
import org.arend.core.expr.Expression;
import org.arend.core.expr.InferenceReferenceExpression;
import org.arend.core.expr.PiExpression;
import org.arend.core.expr.visitor.CompareVisitor;
import org.arend.core.expr.let.LetClause;
import org.arend.core.sort.Level;
import org.arend.core.subst.LevelPair;
import org.arend.core.subst.Levels;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.error.ListErrorReporter;
import org.arend.prelude.Prelude;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.arend.typechecking.implicitargs.equations.TwoStageEquations;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.junit.Test;

import java.util.Collections;

import static org.arend.ExpressionFactory.*;
import static org.arend.core.expr.Expression.compare;
import static org.arend.core.expr.ExpressionFactory.*;
import static org.junit.Assert.*;

public class ComparisonTest extends TypeCheckingTestCase {
  @Test
  public void lambdas() {
    SingleDependentLink x = singleParam("x", Nat());
    SingleDependentLink y = singleParam("y", Nat());
    SingleDependentLink xy = singleParam(true, vars("x", "y"), Nat());
    Expression expr1 = Lam(xy, Ref(xy));
    Expression expr2 = Lam(x, Lam(y, Ref(x)));
    assertEquals(expr1, expr2);
  }

  @Test
  public void lambdas2() {
    SingleDependentLink x = singleParam("x", Nat());
    SingleDependentLink yz = singleParam(true, vars("y", "z"), Nat());
    SingleDependentLink xyz = singleParam(true, vars("x", "y", "z"), Nat());
    Expression expr1 = Lam(xyz, Ref(xyz.getNext()));
    Expression expr2 = Lam(x, Lam(yz, Ref(yz)));
    assertEquals(expr1, expr2);
  }

  @Test
  public void lambdasNotEqual() {
    SingleDependentLink x = singleParam("x", Pi(Nat(), Nat()));
    SingleDependentLink xy = singleParam(true, vars("x", "y"), Nat());
    Expression expr1 = Lam(xy, Ref(xy.getNext()));
    Expression expr2 = Lam(x, Ref(x));
    assertNotEquals(expr1, expr2);
  }

  @Test
  public void lambdasImplicit() {
    SingleDependentLink x = singleParam("x", Nat());
    SingleDependentLink y = singleParam("y", Nat());
    SingleDependentLink yImpl = singleParam(false, vars("y"), Nat());
    SingleDependentLink xImpl = singleParam(false, vars("x"), Nat());
    Expression expr1 = Lam(x, Lam(yImpl, Ref(x)));
    Expression expr2 = Lam(xImpl, Lam(y, Ref(xImpl)));
    assertEquals(expr1, expr2);
  }

  @Test
  public void pi() {
    SingleDependentLink x = singleParam("x", Nat());
    SingleDependentLink y = singleParam("y", Nat());
    SingleDependentLink xy = singleParam(true, vars("x", "y"), Nat());
    SingleDependentLink _Impl = singleParam(false, Collections.singletonList(null), Nat());
    Binding A = new TypedBinding("A", Pi(Nat(), Pi(Nat(), Universe(0))));
    Expression expr1 = Pi(xy, Pi(_Impl, Apps(Ref(A), Ref(xy), Ref(xy.getNext()))));
    Expression expr2 = Pi(x, Pi(y, Pi(_Impl, Apps(Ref(A), Ref(x), Ref(y)))));
    assertEquals(expr1, expr2);
  }

  @Test
  public void pi2() {
    SingleDependentLink xy = singleParam(true, vars("x", "y"), Nat());
    SingleDependentLink zw = singleParam(true, vars("z", "w"), Nat());
    SingleDependentLink xyz = singleParam(true, vars("x", "y", "z"), Nat());
    SingleDependentLink ts = singleParam(true, vars("t", "s"), Nat());
    SingleDependentLink wts = singleParam(true, vars("w", "t", "s"), Nat());
    Expression expr1 = Pi(xy, Pi(zw, Pi(ts, Nat())));
    Expression expr2 = Pi(xyz, Pi(wts, Nat()));
    assertEquals(expr1, expr2);
  }

  @Test
  public void pi3() {
    SingleDependentLink xy = singleParam(true, vars("x", "y"), Nat());
    SingleDependentLink zw = singleParam(true, vars("z", "w"), Nat());
    SingleDependentLink ts = singleParam(true, vars("t", "s"), Nat());
    Expression expr1 = Pi(xy, Pi(zw, Pi(ts, Nat())));
    Expression expr2 = Pi(singleParam(null, Nat()), Pi(singleParam(null, Nat()), Pi(singleParam(null, Nat()), Pi(singleParam(null, Nat()), Pi(singleParam(null, Nat()), Pi(singleParam(null, Nat()), Nat()))))));
    assertEquals(expr1, expr2);
  }

  @Test
  public void piNotEquals() {
    SingleDependentLink xy = singleParam(true, vars("x", "y"), Nat());
    SingleDependentLink xyz = singleParam(true, vars("x", "y", "z"), Nat());
    SingleDependentLink zw = singleParam(true, vars("z", "w"), Nat());
    SingleDependentLink w = singleParam("w", Pi(singleParam(null, Nat()), Nat()));
    Expression expr1 = Pi(xy, Pi(zw, Nat()));
    Expression expr2 = Pi(xyz, Pi(w, Nat()));
    assertNotEquals(expr1, expr2);
  }

  @Test
  public void compareLeq() {
    // Expression expr1 = Pi("X", UniverseOld(1), Pi(Index(0), Index(0)));
    // Expression expr2 = Pi("X", UniverseOld(0), Pi(Index(0), Index(0)));
    Expression expr1 = Universe(0);
    Expression expr2 = Universe(1);
    assertTrue(compare(expr1, expr2, null, CMP.LE));
  }

  @Test
  public void compareNotLeq() {
    SingleDependentLink X0 = singleParam("X", Universe(0));
    SingleDependentLink X1 = singleParam("X", Universe(1));
    Expression expr1 = Pi(X0, Pi(singleParam(null, Ref(X0)), Ref(X0)));
    Expression expr2 = Pi(X1, Pi(singleParam(null, Ref(X1)), Ref(X1)));
    assertFalse(compare(expr1, expr2, null, CMP.LE));
  }

  @Test
  public void letsNotEqual() {
    SingleDependentLink y = singleParam("y", Nat());
    LetClause let1 = let("x", Lam(y, Ref(y)));
    Expression expr1 = let(lets(let1), Apps(Ref(let1), Zero()));
    SingleDependentLink y_ = singleParam("y", Universe(0));
    LetClause let2 = let("x", Lam(y_, Ref(y_)));
    Expression expr2 = let(lets(let2), Apps(Ref(let2), Nat()));
    assertNotEquals(expr1, expr2);
  }

  @Test
  public void letsTelesEqual() {
    DependentLink A = param(null, Universe(0));
    SingleDependentLink yz = singleParam(true, vars("y", "z"), Ref(A));
    SingleDependentLink y = singleParam("y", Ref(A));
    SingleDependentLink z = singleParam("z", Ref(A));
    LetClause let1 = let("x", Lam(yz, Ref(A)));
    Expression expr1 = let(lets(let1), Apps(Ref(let1), Zero()));
    LetClause let2 = let("x", Lam(y, Lam(z, Ref(A))));
    Expression expr2 = let(lets(let2), Apps(Ref(let2), Zero()));
    assertEquals(expr1, expr2);
    assertEquals(expr2, expr1);
  }

  @Test
  public void letsNotEquiv() {
    LetClause let1 = let("x", Universe(0));
    Expression expr1 = let(lets(let1), Ref(let1));
    LetClause let2 = let("x", Universe(1));
    Expression expr2 = let(lets(let2), Ref(let2));
    assertNotEquals(expr1, expr2);
  }

  @Test
  public void letsLess() {
    Expression expr1 = let(lets(let("x", Nat())), Universe(0));
    Expression expr2 = let(lets(let("x", Nat())), Universe(1));
    assertTrue(compare(expr1, expr2, null, CMP.LE));
  }

  @Test
  public void letsNested() {
    Definition def1 = typeCheckDef("\\func test => \\let x => 0 \\in \\let y => 1 \\in zero");
    Definition def2 = typeCheckDef("\\func test => \\let | x => 0 | y => 1 \\in zero");
    assertEquals(((FunctionDefinition) def1).getBody(), ((FunctionDefinition) def2).getBody());
  }

  @Test
  public void etaLam() {
    PiExpression type = Pi(singleParam(null, Nat()), DataCall(Prelude.PATH, LevelPair.SET0,
            Lam(singleParam("i", Interval()), Nat()), Zero(), Zero()));
    TypecheckingResult result1 = typeCheckExpr("\\lam a x => path (\\lam i => a x @ i)", Pi(singleParam(null, type), type));
    TypecheckingResult result2 = typeCheckExpr("\\lam a => a", Pi(singleParam(null, type), type));
    assertEquals(result2.expression, result1.expression);
  }

  @Test
  public void etaLamBody() {
    PiExpression type = Pi(singleParam(null, Nat()), DataCall(Prelude.PATH, LevelPair.SET0,
      Lam(singleParam("i", Interval()), Nat()), Zero(), Zero()));
    TypecheckingResult result1 = typeCheckExpr("\\lam a x => path (\\lam i => a x @ i)", Pi(singleParam(null, type), type));
    TypecheckingResult result2 = typeCheckExpr("\\lam a => \\lam x => a x", Pi(singleParam(null, type), type));
    assertEquals(result2.expression, result1.expression);
  }

  @Test
  public void etaPath() {
    SingleDependentLink x = singleParam("x", Nat());
    DataCallExpression type = DataCall(Prelude.PATH, LevelPair.SET0,
            Lam(singleParam("i", Interval()), Pi(singleParam(null, Nat()), Nat())), Lam(x, Ref(x)), Lam(x, Ref(x)));
    TypecheckingResult result1 = typeCheckExpr("\\lam a => path (\\lam i x => (a @ i) x)", Pi(singleParam(null, type), type));
    TypecheckingResult result2 = typeCheckExpr("\\lam a => a", Pi(singleParam(null, type), type));
    assertEquals(result2.expression, result1.expression);
  }

  @Test
  public void etaTuple() {
    TypecheckingResult result1 = typeCheckExpr("\\lam (p : \\Sigma Nat Nat) => (p.1,p.2)", null);
    TypecheckingResult result2 = typeCheckExpr("\\lam (p : \\Sigma Nat Nat) => p", null);
    assertEquals(result2.expression, result1.expression);
    assertEquals(result1.expression, result2.expression);
  }

  @Test
  public void etaEmptyTuple() {
    TypecheckingResult result1 = typeCheckExpr("\\lam (p : \\Sigma) => p", null);
    TypecheckingResult result2 = typeCheckExpr("\\lam (p : \\Sigma) => ()", null);
    assertEquals(result2.expression, result1.expression);
    assertEquals(result1.expression, result2.expression);
  }

  @Test(timeout = 5000)
  public void compareFunCallsBeforeHeadNormalization() {
    typeCheckModule("\\func idType (A : \\Type) => A\n\\func loop (A : \\Type) => A");
    FunctionDefinition idType = (FunctionDefinition) getDefinition("idType");
    FunctionDefinition loop = (FunctionDefinition) getDefinition("loop");
    DependentLink parameter = loop.getParameters();

    // Normalizing either call diverges, but their arguments are equal after normalization.
    loop.setBody(FunCall(loop, LevelPair.SET0, FunCall(idType, LevelPair.SET0, Ref(parameter))));
    Expression expr1 = FunCall(loop, LevelPair.SET0, FunCall(idType, LevelPair.SET0, Universe(0)));
    Expression expr2 = FunCall(loop, LevelPair.SET0, Universe(0));

    assertTrue(compare(expr1, expr2, null, CMP.EQ));
  }

  @Test
  public void compareFunCallsFallsBackToHeadNormalization() {
    typeCheckModule(
      "\\func constZero (n : Nat) => 0\n" +
      "\\func lhs => constZero 0\n" +
      "\\func rhs => constZero 1");
    Expression expr1 = (Expression) ((FunctionDefinition) getDefinition("lhs")).getBody();
    Expression expr2 = (Expression) ((FunctionDefinition) getDefinition("rhs")).getBody();

    assertTrue(compare(expr1, expr2, null, CMP.EQ));
  }

  @Test
  public void compareFunCallsDetectsUnequalCalls() {
    typeCheckModule(
      "\\func idNat (n : Nat) => n\n" +
      "\\func lhs => idNat 0\n" +
      "\\func rhs => idNat 1");
    Expression expr1 = (Expression) ((FunctionDefinition) getDefinition("lhs")).getBody();
    Expression expr2 = (Expression) ((FunctionDefinition) getDefinition("rhs")).getBody();

    assertFalse(compare(expr1, expr2, null, CMP.EQ));
  }

  @Test
  public void compareFunCallsDoesNotSolveInferenceVariable() {
    typeCheckModule("\\func constZero (n : Nat) => 0");
    FunctionDefinition constZero = (FunctionDefinition) getDefinition("constZero");
    CheckTypeVisitor typechecker = new CheckTypeVisitor(new ListErrorReporter(), null, null);
    Equations equations = typechecker.getEquations();
    ExpressionInferenceVariable variable = new ExpressionInferenceVariable(Nat(), null, Collections.emptySet(), true);
    Expression inferenceReference = InferenceReferenceExpression.make(variable, equations);

    Expression expr1 = FunCall(constZero, Levels.EMPTY, inferenceReference);
    Expression expr2 = FunCall(constZero, Levels.EMPTY, Suc(Zero()));

    assertTrue(CompareVisitor.compare(equations, CMP.EQ, expr1, expr2, null, null));
    assertFalse(variable.isSolved());
  }

  @Test
  public void failedFunCallComparisonDoesNotLeakInferenceSolution() {
    typeCheckModule("\\func constant2 (m n : Nat) => 0");
    FunctionDefinition constant2 = (FunctionDefinition) getDefinition("constant2");
    CheckTypeVisitor typechecker = new CheckTypeVisitor(new ListErrorReporter(), null, null);
    Equations equations = typechecker.getEquations();
    ExpressionInferenceVariable variable = new ExpressionInferenceVariable(Nat(), null, Collections.emptySet(), true);
    Expression inferenceReference = InferenceReferenceExpression.make(variable, equations);

    Expression expr1 = FunCall(constant2, Levels.EMPTY, inferenceReference, Zero());
    Expression expr2 = FunCall(constant2, Levels.EMPTY, Suc(Zero()), Suc(Zero()));

    assertTrue(CompareVisitor.compare(equations, CMP.EQ, expr1, expr2, null, null));
    assertFalse(variable.isSolved());
  }

  @Test
  public void compareFunCallsDoesNotAddLevelEquation() {
    typeCheckModule("\\func constNat (A : \\Type) => 0");
    FunctionDefinition constNat = (FunctionDefinition) getDefinition("constNat");
    CheckTypeVisitor typechecker = new CheckTypeVisitor(new ListErrorReporter(), null, null);
    int[] numberOfLevelEquations = new int[1];
    TwoStageEquations equations = new TwoStageEquations(typechecker) {
      @Override
      public boolean addEquation(Level level1, Level level2, CMP cmp, org.arend.term.concrete.Concrete.SourceNode sourceNode) {
        numberOfLevelEquations[0]++;
        return super.addEquation(level1, level2, cmp, sourceNode);
      }
    };
    Levels inferredLevels = constNat.generateInferVars(equations, null);

    Expression expr1 = FunCall(constNat, inferredLevels, Universe(0));
    Expression expr2 = FunCall(constNat, constNat.makeMinLevels(), Universe(0));

    assertTrue(CompareVisitor.compare(equations, CMP.EQ, expr1, expr2, null, null));
    assertEquals(0, numberOfLevelEquations[0]);
  }
}
