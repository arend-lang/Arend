package org.arend.typechecking.visitor;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.DataDefinition;
import org.arend.core.pattern.BindingPattern;
import org.arend.core.pattern.ExpressionPattern;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.covariance.ParametersCovarianceChecker;
import org.arend.typechecking.error.local.CertainTypecheckingError;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseDefinitionTypechecker {
  protected ErrorReporter errorReporter;

  protected BaseDefinitionTypechecker(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  protected void checkFunctionLevel(Concrete.BaseFunctionDefinition def, FunctionKind kind) {
    if (def.getResultTypeLevel() != null && !(kind == FunctionKind.LEMMA || kind.isCoclause() || def.getBody() instanceof Concrete.ElimFunctionBody || def.getBody().getTerm() instanceof Concrete.CaseExpression)) {
      errorReporter.report(new CertainTypecheckingError(CertainTypecheckingError.Kind.LEVEL_IGNORED, def.getResultTypeLevel()));
      def.setResultTypeLevel(null);
    }
  }

  public static int checkNumberInPattern(int n, ErrorReporter errorReporter, Concrete.SourceNode sourceNode) {
    if (n < 0) {
      n = -n;
    }
    if (n > Concrete.NumberPattern.MAX_VALUE) {
      n = Concrete.NumberPattern.MAX_VALUE;
    }
    if (n == Concrete.NumberPattern.MAX_VALUE) {
      errorReporter.report(new TypecheckingError("Value too big", sourceNode));
    }
    return n;
  }

  protected void getCovariantParameters(DataDefinition dataDefinition, Set<DependentLink> parameters) {
    if (parameters.isEmpty()) {
      return;
    }

    for (Constructor constructor : dataDefinition.getConstructors()) {
      if (!constructor.status().headerIsOK()) {
        continue;
      }

      List<ExpressionPattern> patterns = constructor.getPatterns();
      Set<DependentLink> localParameters = new HashSet<>();
      Map<DependentLink, DependentLink> localToOriginal = new HashMap<>();
      int index = 0;
      for (DependentLink link = dataDefinition.getParameters(); link.hasNext(); link = link.getNext(), index++) {
        if (!parameters.contains(link)) {
          continue;
        }
        DependentLink localLink = link;
        if (patterns != null && index < patterns.size()) {
          ExpressionPattern pattern = patterns.get(index);
          if (pattern instanceof BindingPattern) {
            localLink = ((BindingPattern) pattern).getBinding();
          } else {
            parameters.remove(link);
            continue;
          }
        }
        localParameters.add(localLink);
        localToOriginal.put(localLink, link);
      }

      if (localParameters.isEmpty()) {
        continue;
      }

      ParametersCovarianceChecker checker = new ParametersCovarianceChecker(localParameters);
      for (DependentLink link1 = constructor.getParameters(); link1.hasNext(); link1 = link1.getNext()) {
        link1 = link1.getNextTyped(null);
        checker.check(link1.getType());
        if (localParameters.isEmpty()) {
          break;
        }
      }
      if (!localParameters.isEmpty()) {
        checker.checkNonCovariant(constructor.getBody());
      }

      for (Map.Entry<DependentLink, DependentLink> entry : localToOriginal.entrySet()) {
        if (!localParameters.contains(entry.getKey())) {
          parameters.remove(entry.getValue());
        }
      }
      if (parameters.isEmpty()) {
        return;
      }
    }
  }
}
