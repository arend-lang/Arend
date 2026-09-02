package org.arend.naming;

import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.SearchConcreteVisitor;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * An implemented field cannot be resolved without a {@code DynamicScopeProvider}, that is, if the result
 * type of a \cowith definition is missing or is not a class, or if the type of an implemented field is
 * not a class. The implementations still must be resolved, so that no raw concrete (an
 * {@link Concrete.UnparsedConstructorPattern}, in particular) is left in a resolved definition.
 */
public class UnresolvedPatternTraversalTest extends NameResolverTestCase {
  private void traverseModule(String text, int errors) {
    resolveNamesModule(text, errors);
    for (var data : server.getResolvedDefinitions(MODULE)) {
      traverse(data.definition());
    }
  }

  private void traverse(Concrete.ResolvableDefinition definition) {
    definition.accept(new SearchConcreteVisitor<Object, Object>() {
      @Override
      protected Object checkSourceNode(Concrete.SourceNode sourceNode, Object params) {
        if (sourceNode instanceof Concrete.UnparsedConstructorPattern) {
          fail("Unparsed pattern in a resolved definition");
        }
        return null;
      }
    }, null);
  }

  @Test
  public void missingResultType() {
    traverse(resolveNamesDef(
      "\\func f \\cowith\n" +
      "  | x => \\lam n => \\case n \\with { | suc m => m | 0 => 0 }", 1));
  }

  @Test
  public void nonClassResultType() {
    traverse(resolveNamesDef(
      "\\func f : Nat \\cowith\n" +
      "  | x => \\lam n => \\case n \\with { | suc m => m | 0 => 0 }", 1));
  }

  @Test
  public void nonClassSubCoclause() {
    traverseModule(
      "\\class C (x : Nat)\n" +
      "\\func f : C \\cowith\n" +
      "  | x { | y => \\case 0 \\with { | suc m => m | 0 => 0 } }", 0);
  }
}
