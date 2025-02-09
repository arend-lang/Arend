package org.arend.naming;

import org.arend.core.context.binding.Binding;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.TypedReferable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.prelude.PreludeLibrary;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ChildGroup;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.TestLocalErrorReporter;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.visitor.DesugarVisitor;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;
import org.arend.util.list.PersistentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public abstract class NameResolverTestCase extends ParserTestCase {
  protected ConcreteGroup lastGroup;
  private final Map<String, MetaReferable> metaDefs = new HashMap<>();
  private final Scope metaScope = new Scope() {
    @Override
    public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
      return context == null || context == ScopeContext.STATIC ? metaDefs.get(name) : null;
    }

    @Override
    public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
      return context == null || context == ScopeContext.STATIC ? metaDefs.values() : Collections.emptyList();
    }
  };

  public TCReferable get(String path) {
    ChildGroup parent = lastGroup.getParentGroup();
    Scope scope = LexicalScope.insideOf(lastGroup, parent == null ? EmptyScope.INSTANCE : LexicalScope.insideOf(lastGroup, parent.getGroupScope(), true), true);
    return get(scope, path);
  }

  public Concrete.ReferableDefinition getConcrete(String path) {
    TCReferable ref = get(path);
    return null; // TODO[server2]: ref instanceof ConcreteLocatedReferable ? ((ConcreteLocatedReferable) ref).getDefinition() : null;
  }

  public Concrete.ResolvableDefinition getConcreteDesugarized(String path) {
    Concrete.ReferableDefinition def = getConcrete(path);
    if (!(def instanceof Concrete.ResolvableDefinition)) {
      throw new IllegalArgumentException();
    }
    Concrete.ResolvableDefinition result = ((Concrete.ResolvableDefinition) def).accept(new ReplaceDataVisitor(), null);
    DesugarVisitor.desugar(result, DummyErrorReporter.INSTANCE);
    return result;
  }

  protected void addMeta(String name, Precedence prec, MetaDefinition meta) {
    metaDefs.put(name, new MetaReferable(AccessModifier.PUBLIC, prec, name, meta, meta instanceof MetaResolver ? (MetaResolver) meta : null, new FullModuleReferable(MODULE_PATH)));
  }

  private Concrete.Expression resolveNamesExpr(Scope parentScope, List<Referable> context, String text, int errors) {
    Concrete.Expression expression = parseExpr(text);
    assertThat(expression, is(notNullValue()));

    expression = SyntacticDesugarVisitor.desugar(expression.accept(new ExpressionResolveNameVisitor(parentScope, context.stream().map(ref -> new TypedReferable(ref, null)).toList(), TypingInfo.EMPTY, new TestLocalErrorReporter(errorReporter), null), null), errorReporter);
    assertThat(errorList, containsErrors(errors));
    return expression;
  }

  protected Concrete.Expression resolveNamesExpr(Scope parentScope, String text, int errors) {
    return resolveNamesExpr(parentScope, new ArrayList<>(), text, errors);
  }

  protected Concrete.Expression resolveNamesExpr(String text, @SuppressWarnings("SameParameterValue") int errors) {
    return resolveNamesExpr(PreludeLibrary.getPreludeScope(), new ArrayList<>(), text, errors);
  }

  protected Concrete.Expression resolveNamesExpr(Scope parentScope, @SuppressWarnings("SameParameterValue") String text) {
    return resolveNamesExpr(parentScope, text, 0);
  }

  protected Concrete.Expression resolveNamesExpr(Map<Referable, Binding> context, String text) {
    List<Referable> names = new ArrayList<>(context.keySet());
    return resolveNamesExpr(PreludeLibrary.getPreludeScope(), names, text, 0);
  }

  protected Concrete.Expression resolveNamesExpr(String text) {
    return resolveNamesExpr(new HashMap<>(), text);
  }


  protected ConcreteGroup resolveNamesDef(String text, int errors) {
    ConcreteGroup group = parseDef(text);
    Scope parentScope = new MergeScope(new SingletonScope(group.getReferable()), metaScope, PreludeLibrary.getPreludeScope());
    new DefinitionResolveNameVisitor(ConcreteProvider.EMPTY /* TODO[server2] */, TypingInfo.EMPTY, errorReporter).resolveGroup(group, parentScope, PersistentList.empty(), null);
    assertThat(errorList, containsErrors(errors));
    return group;
  }

  protected ConcreteGroup resolveNamesDefGroup(String text) {
    lastGroup = resolveNamesDef(text, 0);
    return lastGroup;
  }

  protected ConcreteGroup resolveNamesDef(String text) {
    return resolveNamesDef(text, 0);
  }

  protected Concrete.GeneralDefinition getDefinition(TCDefReferable ref) {
    return typechecking.getConcreteProvider().getConcrete(ref);
  }

  protected void resolveNamesModule(ConcreteGroup group, int errors) {
    Scope scope = CachingScope.make(new MergeScope(ScopeFactory.parentScopeForGroup(group, moduleScopeProvider, true), metaScope));
    new DefinitionResolveNameVisitor(ConcreteProvider.EMPTY /* TODO[server2] */, TypingInfo.EMPTY, errorReporter).resolveGroup(group, scope, PersistentList.empty(), null);
    libraryManager.getInstanceProviderSet().collectInstances(group, CachingScope.make(ScopeFactory.parentScopeForGroup(group, moduleScopeProvider, true)));
    assertThat(errorList, containsErrors(errors));
  }

  protected ConcreteGroup resolveNamesModule(String text, int errors) {
    ConcreteGroup group = parseModule(text);
    resolveNamesModule(group, errors);
    return group;
  }

  protected ConcreteGroup resolveNamesModule(String text) {
    lastGroup = resolveNamesModule(text, 0);
    return lastGroup;
  }
}
