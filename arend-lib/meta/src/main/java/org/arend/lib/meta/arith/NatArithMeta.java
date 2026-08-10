package org.arend.lib.meta.arith;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.definition.CoreFunctionDefinition;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.solver.NatOpsPreprocessor;
import org.arend.lib.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class NatArithMeta extends ArithMeta {
  @Dependency(name = "-'")            CoreFunctionDefinition truncMinus;
  @Dependency(name = "-'")            ArendRef truncMinusRef;
  @Dependency(name = "-'<=id")        ArendRef truncMinusLEId;
  @Dependency(name = "-'_<=+")        ArendRef truncMinusLEPlus;
  @Dependency(name = "-'=0")          ArendRef truncMinusEqZero;
  @Dependency(name = "mod<right")     ArendRef modLessRight;
  @Dependency(name = "suc/=0")        ArendRef sucNeqZero;
  @Dependency(name = "<=_exists")     ArendRef leqExists;
  @Dependency(name = "div_mod")       ArendRef modZeroFromLDiv;
  @Dependency(name = "ldiv*div=id")   ArendRef ldivDivEq;
  @Dependency(name = "suc_<_<=")      ArendRef sucLleq;

  @Override
  protected List<CoreBinding> collectSyntheticBindings(
      ExpressionTypechecker typechecker, ContextData contextData, List<CoreBinding> bindings) {
    ConcreteFactory factory = typechecker.getFactory().withData(contextData.getMarker());
    CoreExpression expectedType = contextData.getExpectedType();

    List<CoreExpression> toScan = new ArrayList<>();
    toScan.add(expectedType.normalize(NormalizationMode.WHNF));
    for (CoreBinding b : bindings) {
      toScan.add(b.getType().normalize(NormalizationMode.WHNF));
    }

    NatOpsPreprocessor.Refs refs = new NatOpsPreprocessor.Refs(
        truncMinus, truncMinusRef,
        truncMinusLEId, truncMinusLEPlus, truncMinusEqZero,
        modLessRight, sucNeqZero, leqExists,
        modZeroFromLDiv, ldivDivEq,
        null);
    NatOpsPreprocessor preprocessor = new NatOpsPreprocessor(typechecker, contextData.getMarker(), refs);
    List<NatOpsPreprocessor.SyntheticHypothesis> synths = preprocessor.collect(toScan, bindings);

    List<CoreBinding> synthBindings = new ArrayList<>();
    for (NatOpsPreprocessor.SyntheticHypothesis sh : synths) {
      TypedExpression typed = Utils.tryTypecheck(typechecker, tc -> tc.typecheck(sh.proof, null));
      if (typed != null) {
        synthBindings.add(typed.makeEvaluatingBinding(null));
      }
    }

    tryStrengthen(typechecker, factory, sucLleq, bindings, synthBindings);
    return synthBindings;
  }
}
