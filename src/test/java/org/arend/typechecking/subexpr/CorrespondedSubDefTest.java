package org.arend.typechecking.subexpr;

import org.arend.core.definition.Definition;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.junit.Assert.*;

public class CorrespondedSubDefTest extends TypeCheckingTestCase {
  @Test
  public void funTermBody() {
    Concrete.FunctionDefinition def = (Concrete.FunctionDefinition) resolveNamesDef("\\func f => 0");
    assertNotNull(def);
    Concrete.Expression term = def.getBody().getTerm();
    assertNotNull(term);
    var accept = def.accept(new CorrespondedSubDefVisitor(term), typeCheckDef(def.getData()));
    assertNotNull(accept);
    assertEquals("0", accept.proj1.toString());
    assertEquals("0", accept.proj2.toString());
  }

  @Test
  public void errorReport() {
    Concrete.FunctionDefinition def = (Concrete.FunctionDefinition) resolveNamesDef("\\func f => 0");
    assertNotNull(def);
    Concrete.Expression term = def.getBody().getTerm();
    assertNotNull(term);
    CorrespondedSubDefVisitor visitor = new CorrespondedSubDefVisitor(term);
    def.accept(visitor, typeCheckDef(def.getData()));
    // When matching the telescope of `f`, there's an error
    assertEquals(1, visitor.getExprError().size());
    assertEquals(SubExprError.Kind.Telescope, visitor.getExprError().getFirst().getKind());
  }

  @Test
  public void funResultType() {
    Concrete.FunctionDefinition def = (Concrete.FunctionDefinition) resolveNamesDef("\\func f : Nat => 0");
    assertNotNull(def);
    assertNotNull(def.getResultType());
    var accept = def.accept(new CorrespondedSubDefVisitor(def.getResultType()), typeCheckDef(def.getData()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
  }

  @Test
  public void funParamType() {
    Concrete.FunctionDefinition def = (Concrete.FunctionDefinition) resolveNamesDef("\\func f (a : Nat) => a");
    assertNotNull(def);
    assertFalse(def.getParameters().isEmpty());
    Concrete.Expression type = def.getParameters().getFirst().getType();
    assertNotNull(type);
    var accept = def.accept(new CorrespondedSubDefVisitor(type), typeCheckDef(def.getData()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }

  @Test
  public void coelimFun() {
    resolveNamesModule("""
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
    Concrete.FunctionDefinition def = (Concrete.FunctionDefinition) getConcrete("t");
    Definition coreDef = typeCheckDef(def.getData());
    assertNotNull(def);
    Concrete.ClassFieldImpl clause = (Concrete.ClassFieldImpl) def.getBody().getCoClauseElements().get(1);
    var accept = def.accept(new CorrespondedSubDefVisitor(clause.implementation), coreDef);
    assertNotNull(accept);
    assertEquals("514", accept.proj1.toString());
    assertEquals("514", accept.proj2.toString());
  }

  @Test
  public void classes() {
    Concrete.ClassDefinition def = (Concrete.ClassDefinition) resolveNamesDef("""
      \\class T {
        | A : Nat
        | B : Int
      }
      """);
    {
      assertNotNull(def);
      var clause = (Concrete.ClassField) def.getElements().getFirst();
      var accept = def.accept(new CorrespondedSubDefVisitor(clause.getResultType()), typeCheckDef(def.getData()));
      assertNotNull(accept);
      assertEquals("Nat", accept.proj1.toString());
      assertEquals("Nat", accept.proj2.toString());
    }
    {
      var clause = (Concrete.ClassField) def.getElements().get(1);
      var accept = def.accept(new CorrespondedSubDefVisitor(clause.getResultType()), typeCheckDef(def.getData()));
      assertNotNull(accept);
      assertEquals("Int", accept.proj1.toString());
      assertEquals("Int", accept.proj2.toString());
    }
  }

  @Test
  public void classParam() {
    Concrete.ClassDefinition def = (Concrete.ClassDefinition) resolveNamesDef("\\record T (A B : Nat) {}");
    assertNotNull(def);
    var clause = (Concrete.ClassField) def.getElements().get(1);
    var accept = def.accept(new CorrespondedSubDefVisitor(clause.getResultType()), typeCheckDef(def.getData()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }

  @Test
  public void fieldParam() {
    Concrete.ClassDefinition def = (Concrete.ClassDefinition) resolveNamesDef("""
      \\record T {
        | A : Nat -> Int
        | B (a : \\Sigma) : Nat
      }
      """);
    {
      assertNotNull(def);
      var clauseTy = (Concrete.PiExpression) ((Concrete.ClassField) def.getElements().getFirst()).getResultType();
      var accept = def.accept(new CorrespondedSubDefVisitor(clauseTy.getCodomain()), typeCheckDef(def.getData()));
      assertNotNull(accept);
      assertEquals("Int", accept.proj1.toString());
      assertEquals("Int", accept.proj2.toString());
    }
    {
      Concrete.TypeParameter typeParam = ((Concrete.ClassField) def.getElements().get(1)).getParameters().getFirst();
      var accept = def.accept(new CorrespondedSubDefVisitor(typeParam.getType()), typeCheckDef(def.getData()));
      assertNotNull(accept);
      assertEquals("\\Sigma", accept.proj1.toString());
      assertEquals("\\Sigma", accept.proj2.toString());
    }
  }

  @Test
  public void cowithFun() {
    resolveNamesModule("""
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
    Concrete.FunctionDefinition def = (Concrete.FunctionDefinition) getConcrete("t");
    Definition coreDef = typeCheckDef(def.getData());
    assertNotNull(def);
    var clause = (Concrete.ClassFieldImpl) def.getBody().getCoClauseElements().get(1);
    var accept = def.accept(new CorrespondedSubDefVisitor(clause.implementation), coreDef);
    assertNotNull(accept);
    assertEquals("514", accept.proj1.toString());
    assertEquals("514", accept.proj2.toString());
  }

  @Test
  public void elimFun() {
    Concrete.FunctionDefinition def = (Concrete.FunctionDefinition) resolveNamesDef("""
      \\func f (a b c : Nat): Nat \\elim b
        | zero => a
        | suc b => c
      """);
    Definition coreDef = typeCheckDef(def.getData());
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
    Concrete.DataDefinition def = (Concrete.DataDefinition) resolveNamesDef("\\data D (a : Nat)");
    assertNotNull(def);
    assertFalse(def.getParameters().isEmpty());
    var accept = def.accept(new CorrespondedSubDefVisitor(def.getParameters().getFirst().getType()), typeCheckDef(def.getData()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }

  @Test
  public void cons() {
    Concrete.DataDefinition def = (Concrete.DataDefinition) resolveNamesDef("\\data D Int | c Nat");
    assertNotNull(def);
    assertFalse(def.getConstructorClauses().isEmpty());
    var accept = def.accept(new CorrespondedSubDefVisitor(def.getConstructorClauses().getFirst().getConstructors().getFirst().getParameters().getFirst().getType()), typeCheckDef(def.getData()));
    assertNotNull(accept);
    assertEquals("Nat", accept.proj1.toString());
    assertEquals("Nat", accept.proj2.toString());
  }
}