package org.arend.lib.meta.arith;

import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;

import java.util.ArrayList;
import java.util.List;

public class IntArithMeta extends ArithMeta {
  @Dependency(name = "isuc_<_<=") ArendRef isucLleq;

  @Override
  protected List<CoreBinding> collectSyntheticBindings(
      ExpressionTypechecker typechecker, ContextData contextData, List<CoreBinding> bindings) {
    List<CoreBinding> synthBindings = new ArrayList<>();
    tryStrengthen(typechecker,
        typechecker.getFactory().withData(contextData.getMarker()),
        isucLleq, bindings, synthBindings);
    return synthBindings;
  }
}
