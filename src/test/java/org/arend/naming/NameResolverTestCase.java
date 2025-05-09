package org.arend.naming;

import org.arend.core.context.binding.Binding;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.TypedReferable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.prelude.Prelude;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.TestLocalErrorReporter;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.visitor.DesugarVisitor;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public abstract class NameResolverTestCase extends ParserTestCase {
  private long myModificationStamp = 0;

  protected void incModification() {
    myModificationStamp++;
  }

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
    for (DefinitionData defData : server.getResolvedDefinitions(MODULE)) {
      Concrete.ResolvableDefinition definition = defData.definition();
      if (definition.getData().getRefLongName().toString().equals(path)) {
        return definition;
      }

      if (definition instanceof Concrete.ClassDefinition classDef) {
        for (Concrete.ClassElement element : classDef.getElements()) {
          if (element instanceof Concrete.ClassField field) {
            if (field.getData().getRefLongName().toString().equals(path)) {
              return field;
            }
          }
        }
      } else if (definition instanceof Concrete.DataDefinition dataDef) {
        for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
          for (Concrete.Constructor constructor : clause.getConstructors()) {
            if (constructor.getData().getRefLongName().toString().equals(path)) {
              return constructor;
            }
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

  @SafeVarargs
  private Concrete.Expression resolveNamesExpr(Scope parentScope, List<Referable> context, String text, int errors, Matcher<? super GeneralError>... matchers) {
    Concrete.Expression expression = parseExpr(text);
    assertThat(expression, is(notNullValue()));

    List<TypedReferable> typedContext = new ArrayList<>(context.size());
    for (Referable referable : context) {
      typedContext.add(new TypedReferable(referable, null));
    }

    ListErrorReporter errorReporter = new ListErrorReporter();
    expression = SyntacticDesugarVisitor.desugar(expression.accept(new ExpressionResolveNameVisitor(parentScope, typedContext, TypingInfo.EMPTY, new TestLocalErrorReporter(errorReporter), null), null), errorReporter);
    assertThat(errorReporter.getErrorList(), containsErrors(errors));
    if (matchers.length > 0) {
      assertThat(errorReporter.getErrorList(), Matchers.contains(matchers));
    }
    return expression;
  }

  protected Concrete.Expression resolveNamesExpr(Scope parentScope, String text, int errors) {
    return resolveNamesExpr(parentScope, new ArrayList<>(), text, errors);
  }

  @SafeVarargs
  protected final Concrete.Expression resolveNamesExpr(String text, @SuppressWarnings("SameParameterValue") int errors, Matcher<? super GeneralError>... matchers) {
    return resolveNamesExpr(server.getModuleScopeProvider(null, false).forModule(Prelude.MODULE_PATH), new ArrayList<>(), text, errors, matchers);
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


  protected Concrete.ResolvableDefinition resolveNamesDef(String text, int errors) {
    ConcreteGroup group = parseDef(text);
    server.updateModule(myModificationStamp, MODULE, () -> group);
    server.getCheckerFor(Collections.singletonList(MODULE)).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    assertThat(getAllErrors(), containsErrors(errors));
    return server.getResolvedDefinitions(MODULE).iterator().next().definition();
  }

  protected void resolveNamesDefGroup(String text) {
    resolveNamesDef(text, 0);
  }

  protected Concrete.ResolvableDefinition resolveNamesDef(String text) {
    return resolveNamesDef(text, 0);
  }

  protected Concrete.ResolvableDefinition getDefinition(TCDefReferable ref) {
    return Objects.requireNonNull(server.getResolvedDefinition(ref)).definition();
  }

  protected void resolveNamesModule(ConcreteGroup group, int errors) {
    server.updateModule(myModificationStamp, MODULE, () -> group);
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
