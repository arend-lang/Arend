package org.arend.term.prettyprint;

import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.naming.reference.LocalReferable;
import org.arend.naming.reference.Referable;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NameAnnotatorTest extends TypeCheckingTestCase {
  // Placeholders are self-delimiting: adjacent ones are decoded separately, and the text around them is kept
  @Test
  public void decodePutsNamesBack() {
    NameAnnotator annotator = new NameAnnotator();
    Referable x = new LocalReferable("x");
    Referable foo = new LocalReferable("foo-bar");
    StringBuilder text = new StringBuilder("(").append(annotator.declaration(x, "x")).append(" : ").append(annotator.reference(foo, "foo-bar"))
      .append(annotator.reference(x, "x")).append(")");
    assertEquals(text.length(), "(x : foo-barx)".length());
    assertEquals('-', annotator.lastChar(new StringBuilder(annotator.reference(foo, "foo-"))));

    List<NameAnnotator.Annotation> annotations = annotator.decode(text, 0);
    assertEquals("(x : foo-barx)", text.toString());
    assertEquals(List.of(
      new NameAnnotator.Annotation(1, 2, NameAnnotator.Kind.DECLARATION, x),
      new NameAnnotator.Annotation(5, 12, NameAnnotator.Kind.REFERENCE, foo),
      new NameAnnotator.Annotation(12, 13, NameAnnotator.Kind.REFERENCE, x)), annotations);
  }

  // Names of one character have room for 4096 placeholders until the next decode; the others are printed as they are
  @Test
  public void shortNamesRunOut() {
    NameAnnotator annotator = new NameAnnotator();
    Referable x = new LocalReferable("x");
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 4097; i++) {
      text.append(annotator.reference(x, "x"));
    }
    assertEquals('x', text.charAt(4096));
    assertEquals(4096, annotator.decode(text, 0).size());
    assertEquals("x".repeat(4097), text.toString());
    assertNotEquals("x", annotator.reference(x, "x"));
  }

  // The layout does not change: placeholders have the length of their names
  @Test
  public void layoutDoesNotChange() {
    typeCheckModule("""
      \\func long-function-name-to-break-lines (first-argument second-argument : Nat) : Nat => first-argument
      \\func test (a n : Nat) : Nat \\elim n
        | 0 => long-function-name-to-break-lines (long-function-name-to-break-lines a a) (long-function-name-to-break-lines a a)
        | suc m => \\let k => long-function-name-to-break-lines m m \\in long-function-name-to-break-lines (long-function-name-to-break-lines k k) k
      """);
    Concrete.ResolvableDefinition definition = (Concrete.ResolvableDefinition) ToAbstractVisitor.convert(getDefinition("test"), PrettyPrinterConfig.DEFAULT);
    StringBuilder plain = new StringBuilder();
    definition.accept(new PrettyPrintVisitor(plain, 0), null);

    StringBuilder annotated = new StringBuilder();
    PrettyPrintVisitor visitor = new PrettyPrintVisitor(annotated, 0);
    NameAnnotator annotator = new NameAnnotator();
    visitor.setNameAnnotator(annotator);
    definition.accept(visitor, null);
    List<NameAnnotator.Annotation> annotations = annotator.decode(annotated, 0);

    assertTrue(plain.toString(), plain.toString().contains("\n"));
    assertEquals(plain.toString(), annotated.toString());
    for (NameAnnotator.Annotation annotation : annotations) {
      assertEquals(annotation.referable().textRepresentation(), annotated.substring(annotation.start(), annotation.end()));
    }
    assertTrue(annotations.stream().anyMatch(a -> a.kind() == NameAnnotator.Kind.DECLARATION && a.referable().textRepresentation().equals("k")));
    assertTrue(annotations.stream().anyMatch(a -> a.kind() == NameAnnotator.Kind.REFERENCE && a.referable().textRepresentation().equals("long-function-name-to-break-lines")));
  }
}
