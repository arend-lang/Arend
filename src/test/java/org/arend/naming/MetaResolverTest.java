package org.arend.naming;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.library.MemoryLibrary;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.MetaReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MetaResolverTest extends NameResolverTestCase {
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

  protected void addMeta(String name, Precedence prec, MetaDefinition meta) {
    ModuleLocation module = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.GENERATED, new ModulePath("Meta"));
    MetaReferable metaRef = new MetaReferable(AccessModifier.PUBLIC, prec, name, meta, meta instanceof MetaResolver ? (MetaResolver) meta : null, MODULE_REF);
    server.addReadOnlyModule(module, new ConcreteGroup(DocFactory.nullDoc(), new FullModuleReferable(module), null, Collections.singletonList(new ConcreteStatement(new ConcreteGroup(DocFactory.nullDoc(), metaRef, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()), null, null, null)), Collections.emptyList(), Collections.emptyList()));
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
}
