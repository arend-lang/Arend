package org.arend.typechecking;

import org.arend.core.context.binding.Binding;
import org.arend.core.definition.Definition;
import org.arend.core.expr.Expression;
import org.arend.core.expr.type.Type;
import org.arend.frontend.PositionComparator;
import org.arend.naming.reference.*;
import org.arend.naming.NameResolverTestCase;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteExpressionFactory;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.doubleChecker.CoreDefinitionChecker;
import org.arend.typechecking.doubleChecker.CoreException;
import org.arend.typechecking.doubleChecker.CoreExpressionChecker;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.error.local.LocalErrorReporter;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.order.listener.TypecheckingOrderingListener;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.typechecking.visitor.ArendCheckerFactory;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.arend.typechecking.visitor.DesugarVisitor;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

public class TypeCheckingTestCase extends NameResolverTestCase {
  protected final LocalErrorReporter localErrorReporter = new TestLocalErrorReporter(errorReporter);

  TypecheckingResult typeCheckExpr(Map<Referable, Binding> context, Concrete.Expression expression, Expression expectedType, int errors) {
    CheckTypeVisitor visitor = new CheckTypeVisitor(localErrorReporter, null, null);
    visitor.addBindings(context);
    Concrete.Expression desugar = DesugarVisitor.desugar(expression, localErrorReporter);
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
        localErrorReporter.report(e.error);
        ok = false;
      }
    }

    assertThat(errorList, containsErrors(errors));
    assertTrue(ok);
    return result;
  }

  TypecheckingResult typeCheckExpr(Concrete.Expression expression, Expression expectedType, int errors) {
    return typeCheckExpr(Collections.emptyMap(), expression, expectedType, errors);
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

  protected TypecheckingResult typeCheckExpr(String text, Expression expectedType, int errors) {
    return typeCheckExpr(resolveNamesExpr(text), expectedType, errors);
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
    new TypecheckingOrderingListener(ArendCheckerFactory.DEFAULT, InstanceScopeProvider.EMPTY /* TODO[server2] */, ConcreteProvider.EMPTY /* TODO[server2] */, errorReporter, PositionComparator.INSTANCE, ref -> null).typecheckDefinitions(Collections.singletonList((Concrete.ResolvableDefinition) getDefinition(reference)), null);
    Definition definition = reference.getTypechecked();
    boolean ok = errors != 0 || new CoreDefinitionChecker(errorReporter).check(definition);
    assertThat(errorList, containsErrors(errors));
    assertTrue(ok);
    return definition;
  }

  protected Definition typeCheckDef(String text, int errors) {
    return typeCheckDef((TCDefReferable) resolveNamesDef(text).referable(), errors);
  }

  protected Definition typeCheckDef(String text) {
    return typeCheckDef(text, 0);
  }


  private void typeCheckModule(ConcreteGroup group, int errors) {
    assertTrue(new TypecheckingOrderingListener(ArendCheckerFactory.DEFAULT, InstanceScopeProvider.EMPTY /* TODO[server2] */, ConcreteProvider.EMPTY /* TODO[server2] */, localErrorReporter, PositionComparator.INSTANCE, ref -> null).typecheckModules(Collections.singletonList(group), null));
    boolean ok = errors != 0 || !errorList.isEmpty() || new CoreModuleChecker(errorReporter).checkGroup(group);
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

  protected void typeCheckModule(ConcreteGroup group) {
    lastGroup = group;
    typeCheckModule(group, 0);
  }

  protected ConcreteGroup typeCheckModule(String text, int errors) {
    resolveNamesModule(text);
    typeCheckModule(lastGroup, errors);
    return lastGroup;
  }

  protected ConcreteGroup typeCheckModule(String text) {
    return typeCheckModule(text, 0);
  }

  protected ConcreteGroup typeCheckClass(String instance, String global, int errors) {
    resolveNamesDefGroup("\\class Test {\n" + instance + (global.isEmpty() ? "" : "\n} \\where {\n" + global) + "\n}");
    typeCheckModule(lastGroup, errors);
    return lastGroup;
  }

  protected ConcreteGroup typeCheckClass(String instance, String global) {
    return typeCheckClass(instance, global, 0);
  }
}
