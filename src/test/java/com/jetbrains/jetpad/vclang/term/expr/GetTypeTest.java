package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.module.Namespace;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.term.definition.*;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.LeafElimTreeNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.typeCheckClass;
import static com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.typeCheckDef;
import static org.junit.Assert.assertEquals;

public class GetTypeTest {
  @Test
  public void constructorTest() {
    ClassDefinition def = typeCheckClass("\\static \\data List (A : \\Type0) | nil | cons A (List A) \\static \\function test => cons 0 nil");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    assertEquals(Apps(namespace.getDefinition("List").getDefCall(), Nat()), namespace.getDefinition("test").getType());
    assertEquals(Apps(namespace.getDefinition("List").getDefCall(), Nat()), ((LeafElimTreeNode) ((FunctionDefinition) namespace.getDefinition("test")).getElimTree()).getExpression().getType(new ArrayList<Binding>(0)));
  }

  @Test
  public void nilConstructorTest() {
    ClassDefinition def = typeCheckClass("\\static \\data List (A : \\Type0) | nil | cons A (List A) \\static \\function test => (List Nat).nil");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    assertEquals(Apps(namespace.getDefinition("List").getDefCall(), Nat()), namespace.getDefinition("test").getType());
    assertEquals(Apps(namespace.getDefinition("List").getDefCall(), Nat()), ((LeafElimTreeNode) ((FunctionDefinition) namespace.getDefinition("test")).getElimTree()).getExpression().getType(new ArrayList<Binding>(0)));
  }

  @Test
  public void classExtTest() {
    ClassDefinition def = typeCheckClass("\\static \\class Test { \\abstract A : \\Type0 \\abstract a : A } \\static \\function test => Test { A => Nat }");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    assertEquals(Universe(1), namespace.getDefinition("Test").getType());
    assertEquals(Universe(0, Universe.Type.SET), namespace.getDefinition("test").getType());
    assertEquals(Universe(0, Universe.Type.SET), ((LeafElimTreeNode) ((FunctionDefinition) namespace.getDefinition("test")).getElimTree()).getExpression().getType(new ArrayList<Binding>(0)));
  }

  @Test
  public void lambdaTest() {
    Definition def = typeCheckDef("\\function test => \\lam (f : Nat -> Nat) => f 0");
    assertEquals(Pi(Pi(Nat(), Nat()), Nat()), def.getType());
    assertEquals(Pi(Pi(Nat(), Nat()), Nat()), ((LeafElimTreeNode) ((FunctionDefinition) def).getElimTree()).getExpression().getType(new ArrayList<Binding>(1)));
  }

  @Test
  public void lambdaTest2() {
    Definition def = typeCheckDef("\\function test => \\lam (A : \\Type0) (x : A) => x");
    assertEquals(Pi(typeArgs(Tele(vars("A"), Universe(0)), Tele(vars("x"), Index(0))), Index(1)), def.getType());
    assertEquals(Pi(typeArgs(Tele(vars("A"), Universe(0)), Tele(vars("x"), Index(0))), Index(1)), ((LeafElimTreeNode) ((FunctionDefinition) def).getElimTree()).getExpression().getType(new ArrayList<Binding>(1)));
  }

  @Test
  public void fieldAccTest() {
    ClassDefinition def = typeCheckClass("\\static \\class C { \\abstract x : Nat \\function f (p : 0 = x) => p } \\static \\function test (p : Nat -> C) => (p 0).f");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    Expression type = Apps(Apps(FunCall(Prelude.PATH_INFIX), new ArgumentExpression(Nat(), false, true)), Zero(), Apps(FieldCall(((ClassDefinition) namespace.getDefinition("C")).getField("x")), Apps(Index(0), Zero())));
    List<Binding> context = new ArrayList<>(1);
    context.add(new TypedBinding("p", Pi(Nat(), namespace.getDefinition("C").getDefCall())));
    assertEquals(Pi(typeArgs(Tele(vars("p"), context.get(0).getType())), Pi(type, type)), namespace.getDefinition("test").getType());
    assertEquals(Pi(type, type), ((LeafElimTreeNode) ((FunctionDefinition) namespace.getDefinition("test")).getElimTree()).getExpression().getType(context));
  }

  @Test
  public void tupleTest() {
    Definition def = typeCheckDef("\\function test : \\Sigma (x y : Nat) (x = y) => (0, 0, path (\\lam _ => 0))");
    assertEquals(Sigma(typeArgs(Tele(vars("x", "y"), Nat()), TypeArg(Apps(FunCall(Prelude.PATH_INFIX), Nat(), Index(1), Index(0))))), ((LeafElimTreeNode)((FunctionDefinition) def).getElimTree()).getExpression().getType(new ArrayList<Binding>(0)));
  }

  @Test
  public void letTest() {
    Definition def = typeCheckDef("\\function test => \\lam (F : Nat -> \\Type0) (f : \\Pi (x : Nat) -> F x) => \\let | x => 0 \\in f x");
    assertEquals(Pi(typeArgs(Tele(vars("F"), Pi(Nat(), Universe())), Tele(vars("f"), Pi(typeArgs(Tele(vars("x"), Nat())), Apps(Index(1), Index(0))))), Apps(Index(1), Zero())),
        ((LeafElimTreeNode) ((FunctionDefinition) def).getElimTree()).getExpression().getType(new ArrayList<Binding>()));
  }

  @Test
  public void patternConstructor1() {
    ClassDefinition def = typeCheckClass(
        "\\static \\data C (n : Nat) | C (zero) => c1 | C (suc n) => c2 Nat");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    assertEquals(Apps(namespace.getMember("C").definition.getDefCall(), Zero()), ((DataDefinition) namespace.getMember("C").definition).getConstructor("c1").getType());
    assertEquals(Pi("n", Nat(), Apps(namespace.getMember("C").definition.getDefCall(), Suc(Index(1)))), ((DataDefinition) namespace.getMember("C").definition).getConstructor("c2").getType());
  }

  @Test
  public void patternConstructor2() {
    ClassDefinition def = typeCheckClass(
        "\\static \\data Vec \\Type0 Nat | Vec A zero => Nil | Vec A (suc n) => Cons A (Vec A n)" +
        "\\static \\data D (n : Nat) (Vec Nat n) | D zero _ => dzero | D (suc n) _ => done");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    DataDefinition vec = (DataDefinition) namespace.getMember("Vec").definition;
    DataDefinition d = (DataDefinition) namespace.getMember("D").definition;
    assertEquals(Apps(DataCall(d), Zero(), Index(0)), d.getConstructor("dzero").getType());
    assertEquals(Apps(DataCall(d), Suc(Index(1)), Index(0)), d.getConstructor("done").getType());
    assertEquals(Pi("x", Index(1), Pi("xs", Apps(DataCall(vec), Index(2), Index(1)), Apps(DataCall(vec), Index(3), Suc(Index(2))))), vec.getConstructor("Cons").getType());
  }

  @Test
  public void patternConstructor3() {
    ClassDefinition def = typeCheckClass(
        "\\static \\data D | d \\Type0\n" +
        "\\static \\data C D | C (d A) => c A");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    DataDefinition d = (DataDefinition) namespace.getMember("D").definition;
    DataDefinition c = (DataDefinition) namespace.getMember("C").definition;
    assertEquals(Pi("x", Index(0), Apps(DataCall(c), Apps(ConCall(d.getConstructor("d")), Index(1)))), c.getConstructor("c").getType());
  }

  @Test
  public void patternConstructorDep() {
    ClassDefinition def = typeCheckClass(
        "\\static \\data Box (n : Nat) | box\n" +
        "\\static \\data D (n : Nat) (Box n) | D (zero) _ => d");
    Namespace namespace = def.getParentNamespace().findChild(def.getName().name);
    DataDefinition d = (DataDefinition) namespace.getMember("D").definition;
    assertEquals(Apps(DataCall(d), Zero(), Index(0)), d.getConstructor("d").getType());
  }
}
