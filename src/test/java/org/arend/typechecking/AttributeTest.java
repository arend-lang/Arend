package org.arend.typechecking;

import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.*;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.ext.typechecking.meta.DependencyMetaTypechecker;
import org.arend.ext.typechecking.meta.TrivialMetaTypechecker;
import org.arend.extImpl.ConcreteFactoryImpl;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.library.MemoryLibrary;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.MetaReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.Assert.*;

public class AttributeTest extends TypeCheckingTestCase {
  /**
   * {@code count} returns the number of visible definitions marked with {@code \attr count}.
   * It rejects marked definitions whose names start with "bad".
   */
  private static class CountMeta implements MetaDefinition, AttributeChecker {
    MetaReferable ref;
    final List<List<String>> invocations = new ArrayList<>();

    @Override
    public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
      List<String> names = new ArrayList<>();
      for (CoreDefinition def : typechecker.getAttributedDefinitions(ref)) {
        names.add(def.getRef().getRefName());
      }
      invocations.add(names);
      return typechecker.checkNumber(BigInteger.valueOf(names.size()), contextData.getExpectedType(), contextData.getMarker());
    }

    @Override
    public void checkAttributedDefinition(@NotNull CoreDefinition definition, @NotNull ErrorReporter errorReporter) {
      if (definition.getRef().getRefName().startsWith("bad")) {
        errorReporter.report(new TypecheckingError("Bad attributed definition", null));
      }
    }
  }

  /**
   * A meta that consumes the attribute of another meta through a dependency.
   */
  public static class ConsumerMeta implements MetaDefinition {
    static final List<List<String>> invocations = new ArrayList<>();

    @Dependency public ArendRef count;

    @Override
    public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
      List<String> names = new ArrayList<>();
      for (CoreDefinition def : typechecker.getAttributedDefinitions(count)) {
        names.add(def.getRef().getRefName());
      }
      invocations.add(names);
      return typechecker.checkNumber(BigInteger.valueOf(names.size()), contextData.getExpectedType(), contextData.getMarker());
    }
  }

  private CountMeta meta;
  private long modStamp = 1;

  @Before
  public void addMeta() {
    meta = new CountMeta();
    ModuleLocation module = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.GENERATED, new ModulePath("Meta"));
    FullModuleReferable moduleRef = new FullModuleReferable(module);
    meta.ref = new MetaReferable(AccessModifier.PUBLIC, Precedence.DEFAULT, "count", new TrivialMetaTypechecker(meta), null, moduleRef);
    DependencyMetaTypechecker consumerTypechecker = new DependencyMetaTypechecker(ConsumerMeta.class, ConsumerMeta::new);
    MetaReferable consumerRef = new MetaReferable(AccessModifier.PUBLIC, Precedence.DEFAULT, "consumer", consumerTypechecker, null, moduleRef);
    ConsumerMeta.invocations.clear();
    List<ConcreteStatement> statements = List.of(
      new ConcreteStatement(new ConcreteGroup(DocFactory.nullDoc(), meta.ref, new Concrete.MetaDefinition(meta.ref, null, Collections.emptyList(), null), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()), null),
      new ConcreteStatement(new ConcreteGroup(DocFactory.nullDoc(), consumerRef, new Concrete.MetaDefinition(consumerRef, null, Collections.emptyList(), (Concrete.Expression) consumerTypechecker.makeBody(new ConcreteFactoryImpl(null))), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()), null));
    server.addReadOnlyModule(module, () -> new ConcreteGroup(DocFactory.nullDoc(), moduleRef, null, statements, Collections.emptyList(), Collections.emptyList()));
  }

  private void addModule(String name, String text) {
    ModuleLocation module = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath(name));
    ListErrorReporter errorReporter = new ListErrorReporter();
    ConcreteGroup group = new BuildVisitor(module, errorReporter).visitStatements(CommonCliRepl.createParser(text, module, errorReporter).statements());
    assertTrue(errorReporter.getErrorList().isEmpty());
    server.updateModule(modStamp++, module, () -> group);
  }

  @SafeVarargs
  private void assertInvocations(List<String>... expected) {
    assertEquals(Arrays.asList(expected), meta.invocations);
  }

  @Test
  public void sameModuleTest() {
    typeCheckModule("""
      \\import Meta
      \\attr count \\func a => 0
      \\func b => 1
      \\attr count \\lemma c : 0 = 0 => idp
      \\func test : Nat => count
      """);
    assertInvocations(List.of("a", "c"));
  }

  @Test
  public void declaredAfterTest() {
    typeCheckModule("""
      \\import Meta
      \\attr count \\func a => 0
      \\func test : Nat => count
      \\attr count \\func b => count
      """);
    assertInvocations(List.of("a"), List.of("a"));
  }

  @Test
  public void notSelfTest() {
    typeCheckModule("""
      \\import Meta
      \\attr count \\func a : Nat => count
      """);
    assertInvocations(List.of());
  }

  @Test
  public void whereTest() {
    typeCheckModule("""
      \\import Meta
      \\func test : Nat => count
        \\where {
          \\attr count \\func a => 0
          \\func b : Nat => count
        }
      """);
    assertEquals(2, meta.invocations.size());
    assertTrue(meta.invocations.contains(List.of("a")));
  }

  @Test
  public void importTest() {
    addModule("A", """
      \\import Meta
      \\attr count \\func foo => 0
      \\func bar => 0
      """);
    addModule("B", """
      \\import Meta
      \\attr count \\func baz => 0
      """);
    typeCheckModule("""
      \\import Meta
      \\import A
      \\func test : Nat => count
      """);
    // foo is typechecked because test refers to count; baz is not visible since B is not imported
    assertInvocations(List.of("foo"));
  }

  @Test
  public void notTransitiveTest() {
    addModule("A", """
      \\import Meta
      \\attr count \\func a => 0
      """);
    addModule("B", """
      \\import A
      \\func b => a
      """);
    typeCheckModule("""
      \\import Meta
      \\import B
      \\func test : Nat => count
      """);
    assertInvocations(List.of());
  }

  @Test
  public void metaBodyTest() {
    addModule("A", """
      \\import Meta
      \\attr count \\func a => 0
      """);
    typeCheckModule("""
      \\import Meta
      \\import A
      \\meta cnt => count
      \\func test : Nat => cnt
      """);
    assertInvocations(List.of("a"));
  }

  @Test
  public void dependencyTest() {
    addModule("A", """
      \\import Meta
      \\attr count \\func a => 0
      """);
    typeCheckModule("""
      \\import Meta
      \\import A
      \\func test : Nat => consumer
      """);
    assertEquals(List.of(List.of("a")), ConsumerMeta.invocations);
  }

  @Test
  public void checkerTest() {
    typeCheckModule("""
      \\import Meta
      \\attr count \\func bad => 0
      \\attr count \\func good => 0
      """, 1);
  }

  @Test
  public void severalAttributesTest() {
    typeCheckModule("""
      \\import Meta
      \\func tag => 0
      \\attr tag count \\func a => 0
      \\attr tag \\func b => 0
      \\func test : Nat => count
      """);
    assertInvocations(List.of("a"));
    TCDefReferable a = get("a");
    assertEquals(List.of(get("tag"), meta.ref), a.getTypechecked().getAttributes());
  }

  @Test
  public void accessModifierTest() {
    typeCheckModule("""
      \\import Meta
      \\private \\attr count \\func a => 0
      \\func test : Nat => count
      """);
    assertInvocations(List.of("a"));
  }

  @Test
  public void dataAndClassTest() {
    typeCheckModule("""
      \\import Meta
      \\attr count \\data D | con
      \\attr count \\record R (x : Nat)
      \\func test : Nat => count
      """);
    assertInvocations(List.of("D", "R"));
  }

  @Test
  public void classBodyTest() {
    typeCheckModule("""
      \\import Meta
      \\record R (x : Nat) {
        \\attr count \\func a => x
      }
      """);
    assertEquals(List.of(meta.ref), get("R.a").getTypechecked().getAttributes());
  }

  @Test
  public void unresolvedTest() {
    resolveNamesModule("""
      \\attr unknown \\func a => 0
      """, 1);
  }

  @Test
  public void prettyPrintTest() {
    resolveNamesModule("""
      \\import Meta
      \\func tag => 0
      \\attr tag count \\func a => 0
      """);
    StringBuilder builder = new StringBuilder();
    ((Concrete.Definition) getConcrete("a")).accept(new PrettyPrintVisitor(builder, 0), null);
    assertTrue(builder.toString(), builder.toString().startsWith("\\attr tag count \\func a"));
  }
}