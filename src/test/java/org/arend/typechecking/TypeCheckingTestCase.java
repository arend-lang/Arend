package org.arend.typechecking;

import org.arend.core.context.binding.Binding;
import org.arend.core.definition.Definition;
import org.arend.core.expr.Expression;
import org.arend.core.expr.type.Type;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.naming.reference.*;
import org.arend.naming.NameResolverTestCase;
import org.arend.server.ProgressReporter;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteExpressionFactory;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.doubleChecker.CoreException;
import org.arend.typechecking.doubleChecker.CoreExpressionChecker;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.arend.typechecking.visitor.DesugarVisitor;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

public class TypeCheckingTestCase extends NameResolverTestCase {
  @SafeVarargs
  final TypecheckingResult typeCheckExpr(Map<Referable, Binding> context, Concrete.Expression expression, Expression expectedType, int errors, Matcher<? super GeneralError>... matchers) {
    ListErrorReporter errorReporter = new ListErrorReporter();
    CheckTypeVisitor visitor = new CheckTypeVisitor(errorReporter, null, null);
    visitor.addBindings(context);
    Concrete.Expression desugar = DesugarVisitor.desugar(expression, errorReporter);
    TypecheckingResult result = visitor.finalCheckExpr(desugar, expectedType);
    if (errors == 0) {
      assertThat(result, is(notNullValue()));
    }

    boolean ok = true;
    if (result != null && errors == 0) {
      CoreExpressionChecker checker = new CoreExpressionChecker(new HashSet<>(context.values()), DummyEquations.getInstance(), expression);
      try {
        result.type.accept(checker, Type.OMEGA);
        checker.check(expectedType, result.expression.accept(checker, result.type), result.expression);
      } catch (CoreException e) {
        errorReporter.report(e.error);
        ok = false;
      }
    }

    assertThat(errorReporter.getErrorList(), containsErrors(errors));
    if (matchers.length > 0) {
      assertThat(errorReporter.getErrorList(), Matchers.contains(matchers));
    }
    assertTrue(ok);
    return result;
  }

  @SafeVarargs
  final TypecheckingResult typeCheckExpr(Concrete.Expression expression, Expression expectedType, int errors, Matcher<? super GeneralError>... matchers) {
    return typeCheckExpr(Collections.emptyMap(), expression, expectedType, errors, matchers);
  }

  protected TypecheckingResult typeCheckExpr(Map<Referable, Binding> context, Concrete.Expression expression, Expression expectedType) {
    return typeCheckExpr(context, expression, expectedType, 0);
  }

  protected TypecheckingResult typeCheckExpr(Concrete.Expression expression, Expression expectedType) {
    return typeCheckExpr(Collections.emptyMap(), expression, expectedType, 0);
  }


  protected TypecheckingResult typeCheckExpr(List<Binding> context, String text, Expression expectedType, int errors) {
    Map<Referable, Binding> mapContext = new LinkedHashMap<>();
    for (Binding binding : context) {
      mapContext.put(ConcreteExpressionFactory.ref(binding.getName()), binding);
    }
    return typeCheckExpr(mapContext, resolveNamesExpr(mapContext, text), expectedType, errors);
  }

  @SafeVarargs
  protected final TypecheckingResult typeCheckExpr(String text, Expression expectedType, int errors, Matcher<? super GeneralError>... matchers) {
    return typeCheckExpr(resolveNamesExpr(text), expectedType, errors, matchers);
  }

  protected TypecheckingResult typeCheckExpr(List<Binding> context, String text, Expression expectedType) {
    return typeCheckExpr(context, text, expectedType, 0);
  }

  protected TypecheckingResult typeCheckExpr(String text, Expression expectedType) {
    return typeCheckExpr(resolveNamesExpr(text), expectedType, 0);
  }

  protected Definition typeCheckDef(TCDefReferable reference) {
    return typeCheckDef(reference, 0);
  }

  protected Definition typeCheckDef(TCDefReferable reference, int errors) {
    typeCheckModule(errors);
    return reference.getTypechecked();
  }

  protected Definition typeCheckDef(String text, int errors) {
    return typeCheckDef(resolveNamesDef(text).getData(), errors);
  }

  protected Definition typeCheckDef(String text) {
    return typeCheckDef(text, 0);
  }


  private void typeCheckModule(int errors) {
    server.getCheckerFor(Collections.singletonList(MODULE)).typecheck(null, DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    List<GeneralError> errorList = getAllErrors();
    boolean ok = errors != 0 || !errorList.isEmpty() || new CoreModuleChecker(new ListErrorReporter(errorList)).checkGroup(getGroup());
    assertThat(errorList, containsErrors(errors));
    assertTrue(ok);
  }


  public Definition getDefinition(ConcreteGroup group, String path) {
    TCDefReferable ref = getDef(group, path);
    return ref.getTypechecked();
  }

  public Definition getDefinition(String path) {
    TCDefReferable ref = get(path);
    return ref.getTypechecked();
  }

  protected void typeCheckModule() {
    typeCheckModule(0);
  }

  protected void typeCheckModule(String text, int errors) {
    resolveNamesModule(text);
    typeCheckModule(errors);
  }

  protected void typeCheckModule(String text) {
    typeCheckModule(text, 0);
  }

  protected void typeCheckClass(String instance, String global, int errors) {
    resolveNamesDefGroup("\\class Test {\n" + instance + (global.isEmpty() ? "" : "\n} \\where {\n" + global) + "\n}");
    typeCheckModule(errors);
  }

  protected void typeCheckClass(String instance, String global) {
    typeCheckClass(instance, global, 0);
  }
}
