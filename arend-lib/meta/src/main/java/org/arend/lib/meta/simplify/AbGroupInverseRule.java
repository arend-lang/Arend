package org.arend.lib.meta.simplify;

import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.meta.rewrite.RewriteEquationMeta;
import org.arend.lib.meta.equation.term.CompiledTerm;
import org.arend.lib.meta.equation.term.CompositeTerm;
import org.arend.lib.meta.equation.term.VarTerm;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class AbGroupInverseRule extends GroupRuleBase {
  public AbGroupInverseRule(TypedExpression instance, CoreClassCallExpression classCall, SimplifyMeta meta, ConcreteReferenceExpression refExpr, ExpressionTypechecker typechecker, boolean isAdditive) {
    super(instance, classCall, meta, refExpr, typechecker, isAdditive, true);
  }



  private void countVarOccurNums(CompiledTerm term, Map<Integer, Pair<Integer, Integer>> indToVarOccurNums, boolean curSign) {
    if (term instanceof VarTerm varTerm) {
      var occurNums = indToVarOccurNums.get(varTerm.index);
      if (occurNums == null) {
        indToVarOccurNums.put(varTerm.index, curSign ? new Pair<>(0, 1) : new Pair<>(1, 0));
        return;
      }
      indToVarOccurNums.put(varTerm.index, curSign ? new Pair<>(occurNums.proj1, occurNums.proj2 + 1) : new Pair<>(occurNums.proj1 + 1, occurNums.proj2));
    } else if (term instanceof CompositeTerm compTerm) {
      if (compTerm.matcher == invMatcher) {
        countVarOccurNums(compTerm.subterms.getFirst(), indToVarOccurNums, !curSign);
      } else if (compTerm.matcher == mulMatcher) {
        countVarOccurNums(compTerm.subterms.get(0), indToVarOccurNums, curSign);
        countVarOccurNums(compTerm.subterms.get(1), indToVarOccurNums, curSign);
      }
    }
  }

  private Map<Integer, Integer> varsToRemove(CompiledTerm term) {
    var indToVarOccurNums = new TreeMap<Integer, Pair<Integer, Integer>>();
    countVarOccurNums(term, indToVarOccurNums, false);
    var result = new TreeMap<Integer, Integer>();
    for (var e : indToVarOccurNums.entrySet()) {
      int numToRemove = Math.min(e.getValue().proj1, e.getValue().proj2);
      if (numToRemove > 0) {
        result.put(e.getKey(), numToRemove);
      }
    }
    return result;
  }

  private CompiledTerm removeVars(CompiledTerm term, Map<Integer, Pair<Integer, Integer>> numVarsToRemove, boolean curSign) {
    if (term instanceof VarTerm varTerm) {
      var numsToRemove = numVarsToRemove.get(varTerm.index);
      if (numsToRemove != null) {
        if (curSign && numsToRemove.proj2 > 0) {
          numVarsToRemove.put(varTerm.index, new Pair<>(numsToRemove.proj1, numsToRemove.proj2 - 1));
          return new CompositeTerm(ideMatcher);
        }
        if (!curSign && numsToRemove.proj1 > 0) {
          numVarsToRemove.put(varTerm.index, new Pair<>(numsToRemove.proj1 - 1, numsToRemove.proj2));
          return new CompositeTerm(ideMatcher);
        }
      }
    } else if (term instanceof CompositeTerm compositeTerm) {
      if (compositeTerm.matcher == invMatcher) {
        var invTerm = new CompositeTerm(invMatcher);
        invTerm.subterms.add(removeVars(compositeTerm.subterms.getFirst(), numVarsToRemove, !curSign));
        return invTerm;
      } else if (compositeTerm.matcher == mulMatcher) {
        var mulTerm = new CompositeTerm(mulMatcher);
        var newLeft = removeVars(compositeTerm.subterms.get(0), numVarsToRemove, curSign);
        var newRight = removeVars(compositeTerm.subterms.get(1), numVarsToRemove, curSign);
        mulTerm.subterms.add(newLeft);
        mulTerm.subterms.add(newRight);
        return mulTerm;
      }
    }
    return term;
  }

  @Override
  public RewriteEquationMeta.EqProofConcrete apply(TypedExpression expression) {
    var term = CompiledTerm.compile(expression.getExpression(), Arrays.asList(ideMatcher, mulMatcher, invMatcher), values);
    var concreteTerm = CompiledTerm.termToConcrete(term, x -> {
      if (x == mulMatcher) {
        return factory.ref(meta.mulGTerm);
      }
      if (x == invMatcher) {
        return factory.ref(meta.invGTerm);
      }
      return factory.ref(meta.ideGTerm);
    }, ind -> factory.appBuilder(factory.ref(meta.varGTerm)).app(factory.number(ind)).build(), factory);
    if(concreteTerm == null) return null;
    var numVarsToRemove = new TreeMap<Integer, Pair<Integer, Integer>>();
    varsToRemove(term).forEach((key, value) -> numVarsToRemove.put(key, new Pair<>(value, value)));
    if (numVarsToRemove.isEmpty()) return null;
    var newTerm = removeVars(term, numVarsToRemove, false);
    var simplifyProof = factory.appBuilder(factory.ref(meta.simplifyCorrectAbInv))
            .app(factory.ref(dataRef), false)
            .app(concreteTerm).build();
    var left = factory.core(expression);
    var right = CompiledTerm.termToConcrete(newTerm, x -> {
      if (x == mulMatcher) {
        return factory.ref((isAdditive ? meta.plus : meta.mul).getRef());
      }
      if (x == invMatcher) {
        return factory.ref((isAdditive ? meta.negative : meta.inverse).getRef());
      }
      return factory.ref((isAdditive ? meta.zro : meta.ide).getRef());
    }, ind -> factory.core(values.getValue(ind).computeTyped()), factory);
    if (right == null) return null;
    return new RewriteEquationMeta.EqProofConcrete(simplifyProof, left, right);/**/
  }
}
