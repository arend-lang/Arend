package org.arend.typechecking.doubleChecker;

import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.core.context.param.TypedDependentLink;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.elimtree.IntervalElim;
import org.arend.core.expr.ExpressionFactory;
import org.arend.core.expr.SigmaExpression;
import org.arend.core.expr.UniverseExpression;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.ext.core.context.BindingVariance;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ProgressReporter;
import org.arend.typechecking.TypeCheckingTestCase;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CoreDefinitionCheckerTest extends TypeCheckingTestCase {
  @Before
  public void checkPrelude() {
    server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION)).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  @Test
  public void mixedVarianceSigmaRejected() {
    DependentLink second = new TypedDependentLink(true, "y", ExpressionFactory.Nat(), false, BindingVariance.INVARIANT, EmptyDependentLink.getInstance());
    DependentLink first = new TypedDependentLink(true, "x", ExpressionFactory.Nat(), false, BindingVariance.COVARIANT, second);
    SigmaExpression sigma = new SigmaExpression(first);

    CoreExpressionChecker checker = new CoreExpressionChecker(new HashSet<>(), DummyEquations.getInstance(), null);
    try {
      sigma.accept(checker, UniverseExpression.OMEGA);
      fail("A \\Sigma type with mixed parameter variance must be rejected");
    } catch (CoreException e) {
      // expected
    }
  }

  @Test
  public void uniformVarianceSigmaAccepted() {
    DependentLink second = new TypedDependentLink(true, "y", ExpressionFactory.Nat(), false, BindingVariance.COVARIANT, EmptyDependentLink.getInstance());
    DependentLink first = new TypedDependentLink(true, "x", ExpressionFactory.Nat(), false, BindingVariance.COVARIANT, second);
    SigmaExpression sigma = new SigmaExpression(first);
    assertEquals(BindingVariance.COVARIANT, sigma.getVariance());

    CoreExpressionChecker checker = new CoreExpressionChecker(new HashSet<>(), DummyEquations.getInstance(), null);
    sigma.accept(checker, UniverseExpression.OMEGA);
  }

  @Test
  public void covariantDIIntervalElimIsIncomplete() {
    DependentLink param = new TypedDependentLink(true, "i", ExpressionFactory.DI(), false, BindingVariance.COVARIANT, EmptyDependentLink.getInstance());
    FunctionDefinition definition = new FunctionDefinition((TCDefReferable) null);
    definition.setParameters(param);
    definition.setResultType(ExpressionFactory.Nat());
    definition.setBody(new IntervalElim(1, Collections.singletonList(new IntervalElim.CasePair(ExpressionFactory.Zero(), ExpressionFactory.Zero(), true)), null));

    List<GeneralError> errors = new ArrayList<>();
    boolean ok = new CoreDefinitionChecker(new ListErrorReporter(errors)).check(definition);

    assertFalse("A covariant DI IntervalElim with no otherwise clause must be rejected as incomplete", ok);
    assertFalse(errors.isEmpty());
  }

  @Test
  public void invariantDIIntervalElimIsComplete() {
    DependentLink param = new TypedDependentLink(true, "i", ExpressionFactory.DI(), false, BindingVariance.INVARIANT, EmptyDependentLink.getInstance());
    FunctionDefinition definition = new FunctionDefinition((TCDefReferable) null);
    definition.setParameters(param);
    definition.setResultType(ExpressionFactory.Nat());
    definition.setBody(new IntervalElim(1, Collections.singletonList(new IntervalElim.CasePair(ExpressionFactory.Zero(), ExpressionFactory.Zero(), true)), null));

    List<GeneralError> errors = new ArrayList<>();
    boolean ok = new CoreDefinitionChecker(new ListErrorReporter(errors)).check(definition);

    assertTrue("Unexpected errors: " + errors, errors.isEmpty());
    assertTrue("An invariant, fully-covered DI IntervalElim with no otherwise clause should be accepted as complete", ok);
  }

  @Test
  public void directedFlagMismatchIsRejected() {
    // The parameter is really I-typed, but the case pair claims to be directed (DI) -- the
    // double-checker must independently catch this rather than trusting the embedded flag.
    DependentLink param = new TypedDependentLink(true, "i", ExpressionFactory.Interval(), false, BindingVariance.INVARIANT, EmptyDependentLink.getInstance());
    FunctionDefinition definition = new FunctionDefinition((TCDefReferable) null);
    definition.setParameters(param);
    definition.setResultType(ExpressionFactory.Nat());
    definition.setBody(new IntervalElim(1, Collections.singletonList(new IntervalElim.CasePair(ExpressionFactory.Zero(), ExpressionFactory.Zero(), true)), null));

    List<GeneralError> errors = new ArrayList<>();
    boolean ok = new CoreDefinitionChecker(new ListErrorReporter(errors)).check(definition);

    assertFalse("A case pair whose directed flag disagrees with its parameter's actual type must be rejected", ok);
    assertFalse(errors.isEmpty());
  }

  @Test
  public void caseOnCovariantParameterRejected() {
    typeCheckModule("""
      \\func f (x :+ Nat) : Nat => \\case x \\with {
        | 0 => 0
        | suc n => 1
      }
      """, 1);
  }
}
