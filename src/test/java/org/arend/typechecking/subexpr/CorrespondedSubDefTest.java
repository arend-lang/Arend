package org.arend.typechecking.subexpr;

import org.arend.core.definition.Definition;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.*;

public class CorrespondedSubDefTest extends TypeCheckingTestCase {
  @Test
  public void funTermBody() {
    ConcreteGroup group = resolveNamesDef("\\func f => 0");
    var def = (Concrete.FunctionDefinition) group.definition();
    assertNotNull(def);
    Concrete.Expression term = def.getBody().getTerm();
    assertNotNull(term);
    var accept = def.accept(new CorrespondedSubDefVisitor(term), typeCheckDef((TCDefReferable) group.referable()));
    assertNotNull(accept);
    assertEquals("0", accept.proj1.toString());
    assertEquals("0", accept.proj2.toString());
  }

  @Test
  public void errorReport() {
    ConcreteGroup group = resolveNamesDef("\\func f => 0");
    var def = (Concrete.FunctionDefinition) group.definition();
    assertNotNull(def);
    Concrete.Expression term = def.getBody().getTerm();
    assertNotNull(term);
    CorrespondedSubDefVisitor visitor = new CorrespondedSubDefVisitor(term);
    def.accept(visitor, typeCheckDef((TCDefReferable) group.referable()));
    // When matching the telescope of `f`, there's an error
    assertEquals(1, visitor.getExprError().size());
    assertEquals(SubExprError.Kind.Telescope, visitor.getExprError().getFirst().getKind());
  }

  @Test
  public void funResultType() {
    ConcreteGroup group = resolveNamesDef("\\func f : Nat => 0");
    var def = (Concrete.FunctionDefinition) group.definition();
    assertNotNull(def);
    assertNotNull(def.getResultType());
    var accept = def.accept(new CorrespondedSubDefVisitor(def.getResultType()), typeCheckDef((TCDefReferable) group.referable()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
  }

  @Test
  public void funParamType() {
    ConcreteGroup group = resolveNamesDef("\\func f (a : Nat) => a");
    var def = (Concrete.FunctionDefinition) group.definition();
    assertNotNull(def);
    assertFalse(def.getParameters().isEmpty());
    Concrete.Expression type = def.getParameters().getFirst().getType();
    assertNotNull(type);
    var accept = def.accept(new CorrespondedSubDefVisitor(type), typeCheckDef((TCDefReferable) group.referable()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }

  @Test
  public void coelimFun() {
    ConcreteGroup group = resolveNamesDef(
      """
        \\instance t : T
          | A => 114
          | B => 514
          | p => idp
          \\where {
            \\class T {
              | A : Nat
              | B : Nat
              | p : A = A
            }
          }
        """);
    var def = (Concrete.FunctionDefinition) group.definition();
    Definition coreDef = typeCheckDef((TCDefReferable) group.referable());
    assertNotNull(def);
    Concrete.ClassFieldImpl clause = (Concrete.ClassFieldImpl) def.getBody().getCoClauseElements().get(1);
    var accept = def.accept(new CorrespondedSubDefVisitor(clause.implementation), coreDef);
    assertNotNull(accept);
    assertEquals("514", accept.proj1.toString());
    assertEquals("514", accept.proj2.toString());
  }

  @Test
  public void classes() {
    ConcreteGroup group = resolveNamesDef(
      """
        \\class T {
          | A : Nat
          | B : Int
        }
        """);
    var def = (Concrete.ClassDefinition) group.definition();
    {
      assertNotNull(def);
      var clause = (Concrete.ClassField) def.getElements().getFirst();
      var accept = def.accept(new CorrespondedSubDefVisitor(clause.getResultType()), typeCheckDef((TCDefReferable) group.referable()));
      assertNotNull(accept);
      assertEquals("Nat", accept.proj1.toString());
      assertEquals("Nat", accept.proj2.toString());
    }
    {
      var clause = (Concrete.ClassField) def.getElements().get(1);
      var accept = def.accept(new CorrespondedSubDefVisitor(clause.getResultType()), typeCheckDef((TCDefReferable) group.referable()));
      assertNotNull(accept);
      assertEquals("Int", accept.proj1.toString());
      assertEquals("Int", accept.proj2.toString());
    }
  }

  @Test
  public void classParam() {
    ConcreteGroup group = resolveNamesDef("\\record T (A B : Nat) {}");
    var def = (Concrete.ClassDefinition) group.definition();
    assertNotNull(def);
    var clause = (Concrete.ClassField) def.getElements().get(1);
    var accept = def.accept(new CorrespondedSubDefVisitor(clause.getResultType()), typeCheckDef((TCDefReferable) group.referable()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }

  @Test
  public void fieldParam() {
    ConcreteGroup group = resolveNamesDef(
      """
        \\record T {
          | A : Nat -> Int
          | B (a : \\Sigma) : Nat
        }
        """);
    var def = (Concrete.ClassDefinition) group.definition();
    {
      assertNotNull(def);
      var clauseTy = (Concrete.PiExpression) ((Concrete.ClassField) def.getElements().getFirst()).getResultType();
      var accept = def.accept(new CorrespondedSubDefVisitor(clauseTy.getCodomain()), typeCheckDef((TCDefReferable) group.referable()));
      assertNotNull(accept);
      assertEquals("Int", accept.proj1.toString());
      assertEquals("Int", accept.proj2.toString());
    }
    {
      Concrete.TypeParameter typeParam = ((Concrete.ClassField) def.getElements().get(1)).getParameters().getFirst();
      var accept = def.accept(new CorrespondedSubDefVisitor(typeParam.getType()), typeCheckDef((TCDefReferable) group.referable()));
      assertNotNull(accept);
      assertEquals("\\Sigma", accept.proj1.toString());
      assertEquals("\\Sigma", accept.proj2.toString());
    }
  }

  @Test
  public void cowithFun() {
    ConcreteGroup group = resolveNamesDef(
      """
        \\func t : R \\cowith
          | pre  => 114
          | post => 514
          \\where {
            \\record R {
              | pre  : Nat
              | post : Nat
            }
          }
        """);
    var def = (Concrete.FunctionDefinition) group.definition();
    Definition coreDef = typeCheckDef((TCDefReferable) group.referable());
    assertNotNull(def);
    var clause = (Concrete.ClassFieldImpl) def.getBody().getCoClauseElements().get(1);
    var accept = def.accept(new CorrespondedSubDefVisitor(clause.implementation), coreDef);
    assertNotNull(accept);
    assertEquals("514", accept.proj1.toString());
    assertEquals("514", accept.proj2.toString());
  }

  @Test
  public void elimFun() {
    ConcreteGroup group = resolveNamesDef(
      """
        \\func f (a b c : Nat): Nat \\elim b
          | zero => a
          | suc b => c
        """);
    var def = (Concrete.FunctionDefinition) group.definition();
    Definition coreDef = typeCheckDef((TCDefReferable) group.referable());
    assertNotNull(def);
    var clauses = def.getBody().getClauses();
    assertFalse(clauses.isEmpty());
    {
      Concrete.Expression expression = clauses.getFirst().getExpression();
      assertNotNull(expression);
      var accept = def.accept(new CorrespondedSubDefVisitor(expression), coreDef);
      assertNotNull(accept);
      assertEquals("a", accept.proj1.toString());
      assertEquals("a", accept.proj2.toString());
    }
    {
      Concrete.Expression expression = clauses.get(1).getExpression();
      assertNotNull(expression);
      var accept = def.accept(new CorrespondedSubDefVisitor(expression), coreDef);
      assertNotNull(accept);
      assertEquals("c", accept.proj1.toString());
      assertEquals("c", accept.proj2.toString());
    }
  }

  @Test
  public void dataParam() {
    ConcreteGroup group = resolveNamesDef("\\data D (a : Nat)");
    var def = (Concrete.DataDefinition) group.definition();
    assertNotNull(def);
    assertFalse(def.getParameters().isEmpty());
    var accept = def.accept(new CorrespondedSubDefVisitor(def.getParameters().getFirst().getType()), typeCheckDef((TCDefReferable) group.referable()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }

  @Test
  public void cons() {
    ConcreteGroup group = resolveNamesDef("\\data D Int | c Nat");
    var def = (Concrete.DataDefinition) group.definition();
    assertNotNull(def);
    assertFalse(def.getConstructorClauses().isEmpty());
    var accept = def.accept(new CorrespondedSubDefVisitor(def.getConstructorClauses().getFirst().getConstructors().getFirst().getParameters().getFirst().getType()), typeCheckDef((TCDefReferable) group.referable()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }
}