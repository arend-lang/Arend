package org.arend.typechecking.subexpr;

import org.arend.term.concrete.Concrete;
import org.arend.typechecking.TypeCheckingTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * For GitHub issues
 */
public class SubExprBugsTest extends TypeCheckingTestCase {
  @Test
  public void issue168() {
    resolveNamesModule("""
      \\func test => f 114 \\where {
        \\func F => \\Pi Nat -> Nat
        \\func f : F => \\lam i => i Nat.+ 514
      }
      """);
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) getConcrete("test");
    assertNotNull(concreteDef);
    var concrete = (Concrete.AppExpression) concreteDef.getBody().getTerm();
    assertNotNull(concrete);
    Concrete.Expression subExpr = concrete.getArguments().getFirst().getExpression();
    var accept = concreteDef.accept(new CorrespondedSubDefVisitor(subExpr), typeCheckDef(concreteDef.getData()));
    assertNotNull(accept);
    assertEquals("114", accept.proj1.toString());
    assertEquals("114", accept.proj2.toString());
  }

  @Test
  public void issue180() {
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) resolveNamesDef("\\func test => \\Pi (A : \\Set) -> A -> A");
    assertNotNull(concreteDef);
    var concrete = (Concrete.PiExpression) concreteDef.getBody().getTerm();
    assertNotNull(concrete);
    Concrete.@NotNull Expression subExpr = ((Concrete.PiExpression) concrete.getCodomain()).getParameters().getFirst().getType();
    var accept = concreteDef.accept(new CorrespondedSubDefVisitor(subExpr), typeCheckDef(concreteDef.getData()));
    assertNotNull(accept);
    assertEquals("A", accept.proj1.toString());
    assertEquals("A", accept.proj2.toString());
  }

  @Test
  public void issue195() {
    resolveNamesModule("""
      \\record Kibou \\extends No
        | hana => 114514 \\where {
          \\record No | hana : Nat
        }
      """);
    Concrete.ClassDefinition concreteDef = (Concrete.ClassDefinition) getConcrete("Kibou");
    assertNotNull(concreteDef);
    var classField = (Concrete.ClassFieldImpl) concreteDef.getElements().getFirst();
    assertNotNull(classField);
    Concrete.Expression implementation = classField.implementation;
    var accept = concreteDef.accept(new CorrespondedSubDefVisitor(implementation), typeCheckDef(concreteDef.getData()));
    assertNotNull(accept);
    assertEquals("114514", accept.proj1.toString());
    // It's actually \lam {this : Kibou} => 114514
    // assertEquals("114514", accept.proj2.toString());
  }

  @Test
  public void issue196() {
    resolveNamesModule("""
      \\func Dorothy : Alice \\cowith
       | rbq {
         | level => 114514
       } \\where {
          \\record Rbq | level : Nat
          \\record Alice (rbq : Rbq)
        }
      """);
    Concrete.FunctionDefinition concreteDef = (Concrete.FunctionDefinition) getConcrete("Dorothy");
    assertNotNull(concreteDef);
    var classField = (Concrete.ClassFieldImpl) concreteDef.getBody().getCoClauseElements().getFirst();
    assertNotNull(classField);
    Concrete.ClassFieldImpl implementation = classField.getSubCoclauseList().getFirst();
    var accept = concreteDef.accept(new CorrespondedSubDefVisitor(implementation.implementation), typeCheckDef(concreteDef.getData()));
    assertNotNull(accept);
    assertEquals("114514", accept.proj1.toString());
    assertEquals("114514", accept.proj2.toString());
  }

  @Test
  public void issue252() {
    typeCheckModule(
      "\\record Tony\n" +
        "  | beta (lam : \\Set0) (b : \\Prop) (d : lam) (a : b) : b");
    var concreteDef = (Concrete.ClassDefinition) getConcreteDesugarized("Tony");
    var def = getDefinition("Tony");
    assertTrue(concreteDef.isRecord());
    var field = (Concrete.ClassField) concreteDef.getElements().getFirst();
    var parameters = field.getParameters();
    {
      var parameter = parameters.get(1);
      var accept = concreteDef.accept(new CorrespondedSubDefVisitor(parameter.type), def);
      assertNotNull(accept);
      assertEquals("\\Set0", accept.proj1.toString());
      assertEquals("\\Set0", accept.proj2.toString());
    }
    {
      var parameter = parameters.get(2);
      var accept = concreteDef.accept(new CorrespondedSubDefVisitor(parameter.type), def);
      assertNotNull(accept);
      assertEquals("\\Prop", accept.proj1.toString());
      assertEquals("\\Prop", accept.proj2.toString());
    }
    {
      var parameter = parameters.get(3);
      var accept = concreteDef.accept(new CorrespondedSubDefVisitor(parameter.type), def);
      assertNotNull(accept);
      assertEquals("lam", accept.proj1.toString());
      assertEquals("lam", accept.proj2.toString());
    }
    {
      var parameter = parameters.get(4);
      var accept = concreteDef.accept(new CorrespondedSubDefVisitor(parameter.type), def);
      assertNotNull(accept);
      assertEquals("b", accept.proj1.toString());
      assertEquals("b", accept.proj2.toString());
    }
  }
}
