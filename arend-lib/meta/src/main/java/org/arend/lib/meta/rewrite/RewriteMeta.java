package org.arend.lib.meta.rewrite;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.StdExtension;
import org.arend.lib.error.SubexprError;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RewriteMeta extends BaseMetaDefinition {
  private final StdExtension ext;
  private final boolean isInverse;
  @Dependency private ArendRef transport;
  @Dependency private ArendRef transportInv;

  public RewriteMeta(StdExtension ext, boolean isInverse) {
    this.ext = ext;
    this.isInverse = isInverse;
  }

  @Override
  public boolean withoutLevels() {
    return false;
  }

  @Override
  public boolean[] argumentExplicitness() {
    return new boolean[] { false, true, true };
  }

  private void getNumber(ConcreteExpression expression, List<Integer> result, ErrorReporter errorReporter) {
    int n = Utils.getNumber(expression, errorReporter);
    if (n >= 0) {
      result.add(n);
    }
  }

  @Override
  public @Nullable ConcreteExpression getConcreteRepresentation(@NotNull List<? extends ConcreteArgument> arguments) {
    return ext.factory.appBuilder(ext.factory.ref(transportInv)).app(ext.factory.hole()).app(arguments.subList(arguments.getFirst().isExplicit() ? 0 : 1, arguments.size())).build();
  }

  @Override
  public TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    List<? extends ConcreteArgument> args = contextData.getArguments();
    int currentArg = 0;
    ConcreteExpression occurrencesArg = args.getFirst().isExplicit() ? null : args.get(currentArg++).getExpression();
    List<? extends ConcreteExpression> args0 = new ArrayList<>(Utils.getArgumentList(args.get(currentArg++).getExpression()));
    if (args0.isEmpty()) {
      return typechecker.typecheck(args.get(currentArg).getExpression(), contextData.getExpectedType());
    }

    CoreExpression expectedType = contextData.getExpectedType() == null ? null : contextData.getExpectedType().getUnderlyingExpression();
    boolean reverse = expectedType == null || args.size() > currentArg + 2;
    boolean isForward = reverse;

    ErrorReporter errorReporter = typechecker.getErrorReporter();
    ConcreteReferenceExpression refExpr = contextData.getReferenceExpression();
    ConcreteFactory factory = ext.factory.withData(refExpr.getData());
    if (args0.size() > 1) {
      if (isForward) {
        Collections.reverse(args0);
      }
      ConcreteExpression result = args.get(currentArg++).getExpression();
      for (int i = args0.size() - 1; i >= 0; i--) {
        ConcreteAppBuilder builder = factory.appBuilder(refExpr);
        if (occurrencesArg != null) builder.app(occurrencesArg, false);
        result = builder.app(args0.get(i)).app(result).build();
      }
      return typechecker.typecheck(factory.app(result, args.subList(currentArg, args.size())), contextData.getExpectedType());
    }
    ConcreteExpression arg0 = args0.getFirst();

    // Collect occurrences
    List<Integer> occurrences;
    if (occurrencesArg != null) {
      occurrences = new ArrayList<>();
      for (ConcreteExpression expr : Utils.getArgumentList(occurrencesArg)) {
        getNumber(expr, occurrences, errorReporter);
      }
    } else {
      occurrences = null;
    }

    //noinspection SimplifiableConditionalExpression
    boolean isInverse = reverse ? !this.isInverse : this.isInverse;

    // Add inference holes to functions and type-check the path argument
    TypedExpression path = Utils.typecheckWithAdditionalArguments(arg0, typechecker, ext, 0, false);
    if (path == null) {
      return null;
    }

    // Check that the first argument is a path
    CoreFunCallExpression eq = Utils.toEquality(path.getType(), errorReporter, arg0);
    if (eq == null) {
      return null;
    }

    ConcreteExpression transportExpr = factory.ref(isInverse ? transportInv : transport, refExpr.getPLevels(), refExpr.getHLevels());
    CoreExpression value = eq.getDefCallArguments().get(isInverse == isForward ? 2 : 1);

    // This case won't happen often, but sill possible
    if (!isForward && expectedType instanceof CoreInferenceReferenceExpression) {
      CoreExpression var = value.getUnderlyingExpression();
      if (var instanceof CoreInferenceReferenceExpression && ((CoreInferenceReferenceExpression) var).getVariable() == ((CoreInferenceReferenceExpression) expectedType).getVariable()) {
        if (!(occurrences == null || occurrences.isEmpty() || occurrences.size() == 1 && occurrences.contains(1))) {
          occurrences.remove(1);
          errorReporter.report(new SubexprError(typechecker.getExpressionPrettifier(), occurrences, var, null, expectedType, refExpr));
          return null;
        }
        ArendRef ref = factory.local("T");
        return typechecker.typecheck(factory.app(transportExpr, true, Arrays.asList(
            factory.lam(Collections.singletonList(factory.param(ref)), factory.ref(ref)),
            factory.core("transport (\\lam T => T) {!} _", path),
            args.get(currentArg).getExpression())), null);
      }
      isForward = true;
    }

    TypedExpression lastArg;
    CoreExpression type;
    if (isForward) {
      lastArg = typechecker.typecheck(args.get(currentArg++).getExpression(), null);
      if (lastArg == null) {
        return null;
      }
      type = lastArg.getType();
    } else {
      lastArg = null;
      type = expectedType;
    }
    CoreExpression normType = type.normalize(NormalizationMode.RNF);

    ArendRef ref = factory.local("x");
    return typechecker.typecheck(factory.appBuilder(transportExpr)
      .app(factory.lam(Collections.singletonList(factory.param(Collections.singletonList(ref), factory.core(eq.getDefCallArguments().getFirst().computeTyped()))), factory.meta("transport (\\lam x => {!}) _ _", new MetaDefinition() {
        @Override
        public TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
          TypedExpression var = typechecker.typecheck(factory.ref(ref), null);
          assert var != null;
          final int[] num = { 0 };
          CoreExpression valueType = value.computeType();
          UncheckedExpression absExpr = typechecker.withCurrentState(tc -> normType.replaceSubexpressions(expression -> {
            boolean ok;
            if (value instanceof CoreFunCallExpression && expression instanceof CoreFunCallExpression && ((CoreFunCallExpression) value).getDefinition() == ((CoreFunCallExpression) expression).getDefinition()) {
              ok = true;
              List<? extends CoreExpression> args1 = ((CoreFunCallExpression) value).getDefCallArguments();
              if (args1.isEmpty()) {
                return null;
              }
              List<? extends CoreExpression> args2 = ((CoreFunCallExpression) expression).getDefCallArguments();
              for (int i = 0; i < args1.size(); i++) {
                if (!tc.compare(args1.get(i), args2.get(i), CMP.EQ, refExpr, false, true, true)) {
                  ok = false;
                  break;
                }
              }
            } else {
              ok = tc.compare(valueType, expression.computeType(), CMP.LE, refExpr, false, true, false) && tc.compare(expression, value, CMP.EQ, refExpr, false, true, true);
            }
            if (ok) {
              num[0]++;
              if (occurrences == null || occurrences.contains(num[0])) {
                tc.updateSavedState();
                return var.getExpression();
              }
            }
            tc.loadSavedState();
            return null;
          }, false));
          if (absExpr == null) {
            errorReporter.report(new TypecheckingError("Cannot substitute expression", refExpr));
            return null;
          }
          if (occurrences != null && num[0] > 0) {
            occurrences.removeIf(i -> i <= num[0]);
          }
          if (num[0] == 0 || occurrences != null && !occurrences.isEmpty()) {
            errorReporter.report(new SubexprError(typechecker.getExpressionPrettifier(), occurrences, value, null, normType, refExpr));
            return null;
          }
          return typechecker.check(absExpr, refExpr);
        }
      })))
      .app(factory.core("transport _ {!} _", path))
      .app(lastArg == null ? args.get(currentArg++).getExpression() : factory.core("transport _ _ {!}", lastArg))
      .app(args.subList(currentArg, args.size()))
      .build(), null);
  }
}
