package org.arend.naming;

import org.arend.core.context.binding.Binding;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.TypedReferable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.prelude.Prelude;
import org.arend.server.ProgressReporter;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.TestLocalErrorReporter;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.visitor.DesugarVisitor;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public abstract class NameResolverTestCase extends ParserTestCase {
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

  @Override
  protected List<GeneralError> getAllErrors() {
    List<GeneralError> result = server.getErrorMap().get(MODULE);
    return result == null ? Collections.emptyList() : result;
  }

  public TCDefReferable get(String path) {
    return getConcrete(path).getData();
  }

  protected ConcreteGroup getGroup() {
    return server.getRawGroup(MODULE);
  }

  public Concrete.GeneralDefinition getConcrete(String path) {
    ConcreteGroup group = getGroup();
    String[] names = path.split("\\.");
    for (int i = 0; i < names.length - 1; i++) {
      group = Objects.requireNonNull(group.findSubgroup(names[i]));
    }

    String lastName = names[names.length - 1];
    ConcreteGroup subgroup = group.findSubgroup(lastName);
    if (subgroup != null) return subgroup.definition();

    if (group.definition() instanceof Concrete.ClassDefinition classDef) {
      for (Concrete.ClassElement element : classDef.getElements()) {
        if (element instanceof Concrete.ClassField field) {
          if (field.getData().getRefName().equals(lastName)) {
            return field;
          }
        }
      }
    } else if (group.definition() instanceof Concrete.DataDefinition dataDef) {
      for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
        for (Concrete.Constructor constructor : clause.getConstructors()) {
          if (constructor.getData().getRefName().equals(lastName)) {
            return constructor;
          }
        }
      }
    }

    throw new IllegalArgumentException();
  }

  public Concrete.ResolvableDefinition getConcreteDesugarized(String path) {
    Concrete.ResolvableDefinition def = (Concrete.ResolvableDefinition) getConcrete(path);
    Concrete.ResolvableDefinition result = def.accept(new ReplaceDataVisitor(), null);
    DesugarVisitor.desugar(result, DummyErrorReporter.INSTANCE);
    return result;
  }

  protected void addMeta(String name, Precedence prec, MetaDefinition meta) {
    metaDefs.put(name, new MetaReferable(AccessModifier.PUBLIC, prec, name, meta, meta instanceof MetaResolver ? (MetaResolver) meta : null, MODULE_REF));
  }

  private Concrete.Expression resolveNamesExpr(Scope parentScope, List<Referable> context, String text, int errors) {
    Concrete.Expression expression = parseExpr(text);
    assertThat(expression, is(notNullValue()));

    List<TypedReferable> typedContext = new ArrayList<>(context.size());
    for (Referable referable : context) {
      typedContext.add(new TypedReferable(referable, null));
    }

    ListErrorReporter errorReporter = new ListErrorReporter();
    expression = SyntacticDesugarVisitor.desugar(expression.accept(new ExpressionResolveNameVisitor(parentScope, typedContext, TypingInfo.EMPTY, new TestLocalErrorReporter(errorReporter), null), null), errorReporter);
    assertThat(errorReporter.getErrorList(), containsErrors(errors));
    return expression;
  }

  protected Concrete.Expression resolveNamesExpr(Scope parentScope, String text, int errors) {
    return resolveNamesExpr(parentScope, new ArrayList<>(), text, errors);
  }

  protected Concrete.Expression resolveNamesExpr(String text, @SuppressWarnings("SameParameterValue") int errors) {
    return resolveNamesExpr(server.getModuleScopeProvider(null, false).forModule(Prelude.MODULE_PATH), new ArrayList<>(), text, errors);
  }

  protected Concrete.Expression resolveNamesExpr(Scope parentScope, @SuppressWarnings("SameParameterValue") String text) {
    return resolveNamesExpr(parentScope, text, 0);
  }

  protected Concrete.Expression resolveNamesExpr(Map<Referable, Binding> context, String text) {
    List<Referable> names = new ArrayList<>(context.keySet());
    return resolveNamesExpr(server.getModuleScopeProvider(null, false).forModule(Prelude.MODULE_PATH), names, text, 0);
  }

  protected Concrete.Expression resolveNamesExpr(String text) {
    return resolveNamesExpr(new HashMap<>(), text);
  }


  protected ConcreteGroup resolveNamesDef(String text, int errors) {
    ConcreteGroup group = parseDef(text);
    server.updateModule(0, MODULE, () -> group);
    server.getCheckerFor(Collections.singletonList(MODULE)).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertThat(getAllErrors(), containsErrors(errors));
    return group;
  }

  protected void resolveNamesDefGroup(String text) {
    resolveNamesDef(text, 0);
  }

  protected ConcreteGroup resolveNamesDef(String text) {
    return resolveNamesDef(text, 0);
  }

  protected Concrete.ResolvableDefinition getDefinition(TCDefReferable ref) {
    return Objects.requireNonNull(server.getResolvedDefinition(ref)).definition();
  }

  protected void resolveNamesModule(ConcreteGroup group, int errors) {
    server.updateModule(0, MODULE, () -> group);
    server.getCheckerFor(Collections.singletonList(MODULE)).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertThat(getAllErrors(), containsErrors(errors));
  }

  protected ConcreteGroup resolveNamesModule(String text, int errors) {
    ConcreteGroup group = parseModule(text);
    resolveNamesModule(group, errors);
    return group;
  }

  protected void resolveNamesModule(String text) {
    resolveNamesModule(text, 0);
  }
}
