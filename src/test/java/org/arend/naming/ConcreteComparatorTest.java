package org.arend.naming;

import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteCompareVisitor;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ConcreteComparatorTest extends NameResolverTestCase {
  @Test
  public void testLamPatterns() {
    resolveNamesModule(
      "\\func foo => \\lam n (path f) m => f\n" +
      "\\func bar => \\lam n (path f) m => f");
    assertTrue(((Concrete.TermFunctionBody) ((Concrete.FunctionDefinition) getConcrete("foo")).getBody()).getTerm().accept(new ConcreteCompareVisitor(), ((Concrete.TermFunctionBody) ((Concrete.FunctionDefinition) getConcrete("bar")).getBody()).getTerm()));
  }

  @Test
  public void testLamPatternsCopy() {
    Concrete.ResolvableDefinition def = resolveNamesDef("\\func foo => \\lam (path f) n (path g) => f");
    Assert.assertNotNull(def);
    assertTrue(def.accept(new ConcreteCompareVisitor(), def.accept(new ReplaceDataVisitor(), null)));
  }

  @Test
  public void testSubstitution() {
    Concrete.ResolvableDefinition def = resolveNamesDef("\\func foo => \\lam n (path f) m (path g) => f");
    ConcreteCompareVisitor visitor = new ConcreteCompareVisitor();
    Assert.assertNotNull(def);
    def.accept(visitor, (Concrete.Definition) def.accept(new ReplaceDataVisitor(), null));
    assertTrue(visitor.getSubstitution().isEmpty());
  }

  @Test
  public void testLetPatterns() {
    Concrete.ResolvableDefinition def = resolveNamesDef("\\func foo => \\let (x,y) => (0,1) \\in x");
    ConcreteCompareVisitor visitor = new ConcreteCompareVisitor();
    Assert.assertNotNull(def);
    def.accept(visitor, def.accept(new ReplaceDataVisitor(), null));
    assertTrue(visitor.getSubstitution().isEmpty());
  }
}
