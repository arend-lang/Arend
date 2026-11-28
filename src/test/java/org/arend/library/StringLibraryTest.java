package org.arend.library;

import org.arend.ArendTestCase;
import org.arend.core.definition.Definition;
import org.arend.core.expr.visitor.SizeExpressionVisitor;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.ArendExtension;
import org.arend.ext.LiteralTypechecker;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettifier.ExpressionPrettifier;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.naming.scope.EmptyScope;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.arend.Matchers.missingClauses;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// Kernel/API tests for string literals: the kernel only provides `checkByteArray` and delegates the
// elaboration of literals to an extension's LiteralTypechecker. These tests use a probe extension and
// a minimal `String` record instead of arend-lib; arend-lib's own String behavior is covered in
// arend-lib/test/Data/StringTest.ard.
public class StringLibraryTest extends ArendTestCase {
  private static final String STRINGS_LIBRARY = "strings";
  private static final String PRETTIFIED_STRING = "<probe-string>";

  /**
   * Elaborates a string literal into {@code \new String { | bytes => ... }} for the {@code String}
   * record in scope, and prettifies values of that record as {@link #PRETTIFIED_STRING}.
   */
  public static class ProbeStringExtension implements ArendExtension {
    private ConcreteFactory factory;

    @Override
    public void setConcreteFactory(@NotNull ConcreteFactory factory) {
      this.factory = factory;
    }

    @Override
    public @Nullable LiteralTypechecker getLiteralTypechecker() {
      return new LiteralTypechecker() {
        @Override
        public @Nullable ConcreteExpression resolveString(@NotNull String unescapedString, @NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
          ArendRef ref = resolver.resolveName("String");
          return ref == null ? null : contextData.getFactory().ref(ref);
        }

        @Override
        public @Nullable TypedExpression typecheckString(@NotNull String unescapedString, @Nullable ConcreteExpression resolved, @NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
          if (!(resolved instanceof ConcreteReferenceExpression refExpr && typechecker.getCoreDefinition(refExpr.getReferent()) instanceof CoreClassDefinition stringClass)) {
            return null;
          }
          CoreClassField bytesField = stringClass.findField("bytes");
          if (bytesField == null) return null;
          TypedExpression array = typechecker.checkByteArray(unescapedString.getBytes(StandardCharsets.UTF_8));
          ConcreteFactory factory = contextData.getFactory();
          return typechecker.typecheck(factory.newExpr(factory.classExt(factory.ref(refExpr.getReferent()), factory.implementation(bytesField.getRef(), factory.core(array)))), contextData.getExpectedType());
        }
      };
    }

    @Override
    public @Nullable ExpressionPrettifier getExpressionPrettifier() {
      return (expression, defaultPrettifier) -> {
        CoreExpression normalized = expression.normalize(NormalizationMode.WHNF);
        CoreExpression type = normalized instanceof CoreClassCallExpression ? normalized : normalized.computeType().normalize(NormalizationMode.WHNF);
        return type instanceof CoreClassCallExpression classCall && classCall.getDefinition().getRef().getRefName().equals("String")
          ? factory.string(PRETTIFIED_STRING)
          : null;
      };
    }
  }

  // Snippet sources go into a rule-managed folder so they are deleted after each test.
  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  private LibraryManager libraryManager;

  @Override
  protected ArendServerRequester getRequester() {
    libraryManager = new LibraryManager(DummyErrorReporter.INSTANCE);
    return new CliServerRequester(libraryManager);
  }

  // The `strings` library declares the String record and carries the probe extension. Its `ext`
  // directory must exist for a class-loader delegate to be created; the extension class itself is
  // then found on the test classpath by the parent class loader.
  @Before
  public void loadStringsLibrary() throws IOException {
    Path root = tempFolder.newFolder(STRINGS_LIBRARY).toPath();
    Files.createDirectories(root.resolve("src"));
    Files.createDirectories(root.resolve("ext"));
    Files.writeString(root.resolve("src").resolve("Strings.ard"), "\\record String (bytes : Array (Fin 256))\n");
    Files.writeString(root.resolve("arend.yaml"),
        "sourcesDir: src\nextensionsDir: ext\nextensionMainClass: " + ProbeStringExtension.class.getName() + "\n");
    FileSourceLibrary library = FileSourceLibrary.fromConfigFile(root.resolve("arend.yaml"), false, DummyErrorReporter.INSTANCE);
    assertNotNull("Could not load the strings library", library);
    libraryManager.updateLibrary(library, server);
  }

  // Typechecks `source` as a standalone module of a library depending on `strings` that uses the
  // probe extension directly.
  private ModuleLocation typecheckSnippet(String libraryName, String source) throws IOException {
    return typecheckSnippet(libraryName, source, List.of(STRINGS_LIBRARY), ProbeStringExtension.class.getName());
  }

  private ModuleLocation typecheckSnippet(String libraryName, String source, List<String> dependencies, @Nullable String extensionMainClass) throws IOException {
    Path root = tempFolder.newFolder("arend-string-test-" + libraryName).toPath();
    Path srcDir = Files.createDirectories(root.resolve("src"));
    Files.writeString(srcDir.resolve("Snippet.ard"), source);
    Path extDir = Files.createDirectories(root.resolve("ext"));

    // args: name, isExternal, modStamp, dependencies, version, langVersion, extensionMainClass,
    // modules, sourceBasePath, binaryBasePath, testBasePath, classLoaderDelegate
    FileSourceLibrary snippetLib = new FileSourceLibrary(libraryName, false, -1,
        dependencies, null, null, extensionMainClass, null,
        srcDir, null, null, new FileClassLoaderDelegate(extDir));
    libraryManager.updateLibrary(snippetLib, server);

    ModuleLocation module = server.findModule(new ModulePath("Snippet"), libraryName, false, true);
    assertNotNull("Snippet module was not found in " + libraryName, module);
    server.getCheckerFor(Collections.singletonList(module)).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    return module;
  }

  private String singleErrorText() {
    assertEquals(1, getAllErrors().size());
    return getAllErrors().getFirst().getDoc(new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE)).toString();
  }

  // Soundness guard: a string literal inhabits String without going through a constructor, so
  // `\case "s" \with {}` must not be accepted as exhaustive -- otherwise one could "prove" Empty.
  // String is a \record, which the coverage checker sees as a single implicit shape (regardless of
  // its fields), so exactly one clause is missing.
  @Test
  public void stringLiteralCaseIsNotExhaustive() throws IOException {
    typecheckSnippet("string-coverage-probe", """
      \\import Strings
      \\data Empty
      \\func f : Empty => \\case "s" \\with {}
      """);
    assertThatErrorsAre(missingClauses(1));
  }

  // Without any literal typechecker, the kernel itself rejects string literals.
  @Test
  public void stringLiteralWithoutLiteralTypecheckerIsRejected() throws IOException {
    typecheckSnippet("string-no-literal-typechecker", "\\func value => \"hello\"\n", List.of(), null);
    String error = singleErrorText();
    assertTrue(error, error.contains("no literal typechecker is available"));
  }

  // If Fin values (String's byte elements) materialized as unary constructor chains instead of
  // staying compact, a 10,000-character literal would produce a term with hundreds of thousands of
  // nodes. The bound is generous -- this is a regression guard against a unary blowup in the term
  // representation.
  @Test
  public void termSizeStaysSmall() throws IOException {
    String literal = "€".repeat(10_000); // '€' UTF-8-encodes to 3 bytes, for 30,000 bytes total
    ModuleLocation module = typecheckSnippet("string-perf-probe",
        "\\import Strings\n" +
        "\\func bigString => \"" + literal + "\"\n");
    assertTrue("expected no errors", getAllErrors().isEmpty());

    Collection<? extends DefinitionData> definitions = server.getResolvedDefinitions(module);
    Definition bigString = definitions.stream()
        .filter(d -> d.definition().getData().getRefName().equals("bigString"))
        .map(d -> d.definition().getData().getTypechecked())
        .filter(java.util.Objects::nonNull)
        .findAny().orElse(null);
    assertNotNull("bigString did not typecheck", bigString);

    int size = SizeExpressionVisitor.getSize(bigString);
    assertTrue("term size " + size + " suggests a unary blowup, not a compact representation", size < 200_000);
  }

  // A library without an extension class gets a DefaultArendExtension, which must forward both the
  // literal typechecker and the expression prettifier of its dependencies.
  @Test
  public void stringPrettifierForwardedToExtensionlessLibrary() throws IOException {
    typecheckSnippet("string-default-extension-prettifier", """
      \\import Strings
      \\func test => ("foo" : String) "bar"
      """, List.of(STRINGS_LIBRARY), null);
    String error = singleErrorText();
    assertTrue(error, error.contains(PRETTIFIED_STRING));
  }
}
