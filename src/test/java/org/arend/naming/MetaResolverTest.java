package org.arend.naming;

import org.arend.Matchers;
import org.arend.ext.concrete.definition.ConcreteMetaDefinition;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.ext.typechecking.meta.TrivialMetaTypechecker;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.MetaTypechecker;
import org.arend.library.MemoryLibrary;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.*;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.error.local.NotEqualExpressionsError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MetaResolverTest extends TypeCheckingTestCase {
  private static class BaseMetaDefinition implements MetaDefinition, MetaResolver {
    int numberOfInvocations = 0;

    @Override
    public @Nullable ConcreteExpression resolvePrefix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
      fail();
      return null;
    }

    @Override
    public @Nullable ConcreteExpression resolveInfix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData, @Nullable ConcreteExpression leftArg, @Nullable ConcreteExpression rightArg) {
      fail();
      return null;
    }

    @Override
    public @Nullable ConcreteExpression resolvePostfix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData, @Nullable ConcreteExpression leftArg) {
      fail();
      return null;
    }
  }

  private static class PrefixMetaDefinition extends BaseMetaDefinition {
    private final int numberOfArguments;

    private PrefixMetaDefinition(int numberOfArguments) {
      this.numberOfArguments = numberOfArguments;
    }

    @Override
    public @Nullable ConcreteExpression resolvePrefix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
      numberOfInvocations++;
      assertEquals(numberOfArguments, contextData.getArguments().size());
      return new Concrete.TupleExpression(null, Collections.emptyList());
    }
  }

  protected void addMeta(String name, Precedence prec, MetaResolver resolver, MetaTypechecker typechecker, List<ModulePath> importedModules, Concrete.Expression body) {
    ModuleLocation module = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.GENERATED, new ModulePath("Meta"));
    MetaReferable metaRef = new MetaReferable(AccessModifier.PUBLIC, prec, name, typechecker, resolver, MODULE_REF);
    List<ConcreteStatement> statements = new ArrayList<>(importedModules.size() + 1);
    for (ModulePath modulePath : importedModules) {
      statements.add(new ConcreteStatement(null, new ConcreteNamespaceCommand(null, true, new LongUnresolvedReference(null, null, modulePath.toList()), true, Collections.emptyList(), Collections.emptyList()), null, null));
    }
    statements.add(new ConcreteStatement(new ConcreteGroup(DocFactory.nullDoc(), metaRef, body == null ? null : new Concrete.MetaDefinition(metaRef, null, null, Collections.emptyList(), body), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()), null, null, null));
    server.addReadOnlyModule(module, new ConcreteGroup(DocFactory.nullDoc(), new FullModuleReferable(module), null, statements, Collections.emptyList(), Collections.emptyList()));
  }

  protected void addMeta(String name, Precedence prec, MetaDefinition meta) {
    addMeta(name, prec, meta instanceof MetaResolver ? (MetaResolver) meta : null, new TrivialMetaTypechecker(meta), Collections.emptyList(), null);
  }

  @Test
  public void prefixTest() {
    BaseMetaDefinition meta = new PrefixMetaDefinition(2);
    addMeta("meta", Precedence.DEFAULT, meta);
    resolveNamesModule("""
      \\import Meta
      \\func foo => meta 0 1
      """);
    assertEquals(1, meta.numberOfInvocations);
  }

  @Test
  public void prefixTest2() {
    BaseMetaDefinition meta = new PrefixMetaDefinition(2);
    addMeta("meta", Precedence.DEFAULT, meta);
    resolveNamesModule("""
      \\import Meta
      \\func foo => meta 0 1 Nat.+ 0
      """);
    assertEquals(1, meta.numberOfInvocations);
  }

  @Test
  public void prefixTest3() {
    BaseMetaDefinition meta = new PrefixMetaDefinition(2);
    addMeta("meta", Precedence.DEFAULT, meta);
    resolveNamesModule("""
      \\import Meta
      \\func foo => 1 Nat.+ meta 0 1 Nat.+ 2
      """);
    assertEquals(1, meta.numberOfInvocations);
  }

  @Test
  public void prefixTest4() {
    BaseMetaDefinition meta = new PrefixMetaDefinition(2);
    addMeta("meta", Precedence.DEFAULT, meta);
    resolveNamesModule("""
      \\import Meta
      \\func foo => meta (meta 0 1) 1
      """);
    assertEquals(1, meta.numberOfInvocations);
  }

  @Test
  public void prefixTest5() {
    BaseMetaDefinition meta = new PrefixMetaDefinition(2);
    addMeta("meta", Precedence.DEFAULT, meta);
    resolveNamesModule("""
      \\import Meta
      \\func foo => meta 0 1 Nat.+ meta 2 3
      """);
    assertEquals(2, meta.numberOfInvocations);
  }

  protected void updateModule(String moduleName, String defName, Concrete.Expression expr, long modStamp) {
    ModuleLocation module = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath(moduleName));
    FullModuleReferable moduleRef = new FullModuleReferable(module);
    LocatedReferableImpl referable = new LocatedReferableImpl(null, AccessModifier.PUBLIC, Precedence.DEFAULT, defName, Precedence.DEFAULT, null, moduleRef, GlobalReferable.Kind.FUNCTION);
    server.updateModule(modStamp, module, () -> new ConcreteGroup(DocFactory.nullDoc(), moduleRef, null, Collections.singletonList(new ConcreteStatement(new ConcreteGroup(DocFactory.nullDoc(), referable, new Concrete.FunctionDefinition(FunctionKind.FUNC, referable, Collections.emptyList(), null, null, new Concrete.TermFunctionBody(null, expr)), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()), null, null, null)), Collections.emptyList(), Collections.emptyList()));
  }

  @Test
  public void updateTest() {
    String aModule = "A";
    String barFunc = "bar";
    updateModule(aModule, barFunc, new Concrete.NumericLiteral(null, BigInteger.ONE), 1);
    addMeta("meta", Precedence.DEFAULT, null, new MetaTypechecker() {
      @Override
      public @NotNull MetaDefinition typecheck(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteMetaDefinition definition) {
        ConcreteExpression body = Objects.requireNonNull(definition.getBody());
        return new MetaDefinition() {
          @Override
          public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
            return typechecker.typecheck(body, null);
          }
        };
      }
    }, Collections.singletonList(new ModulePath(aModule)), new Concrete.ReferenceExpression(null, new NamedUnresolvedReference(null, barFunc)));
    typeCheckModule("""
      \\import Meta
      \\func foo => meta
      \\func test : foo = 1 => idp
      """);
    updateModule(aModule, barFunc, new Concrete.NumericLiteral(null, BigInteger.TWO), 2);
    typeCheckModule(1);
    assertThatErrorsAre(Matchers.typecheckingError(NotEqualExpressionsError.class));
  }
}
