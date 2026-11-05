package org.arend.naming;

import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Building a module from the tree the parser recovered after a syntax error. The error is already
 * reported; what is left must not throw -- an exception there took the whole module down, and the
 * CLI with it. Each case is a construct that did, written with a universe that no longer exists
 * ({@code \hType}) the way old sources still have it.
 */
public class ParserErrorRecoveryTest extends ParserTestCase {
  private static Concrete.GeneralDefinition onlyDefinition(ConcreteGroup module) {
    assertNotNull(module);
    for (ConcreteStatement statement : module.statements()) {
      if (statement.group() != null) return statement.group().definition();
    }
    throw new AssertionError("no definition was built");
  }

  @Test
  public void brokenResultType() {
    Concrete.GeneralDefinition def = onlyDefinition(parseModuleWithSyntaxErrors("\\func code (n : Nat) : \\hType => n"));
    assertTrue(def instanceof Concrete.FunctionDefinition);
  }

  @Test
  public void brokenParameterOfARecord() {
    assertNotNull(parseModuleWithSyntaxErrors("\\record R (D : Nat (\\suc \\lp)) (x : Nat)"));
  }

  @Test
  public void brokenFieldType() {
    assertNotNull(parseModuleWithSyntaxErrors("\\class C {\n  | field : \\hType\n}"));
  }

  @Test
  public void brokenLetClause() {
    assertNotNull(parseModuleWithSyntaxErrors("\\func f => \\let p : Nat = {\\hType} 0 \\in p"));
  }
}
