package org.arend.lib.meta.equationNew.monoid;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.error.equation.EquationFindError;
import org.arend.lib.meta.equationNew.BaseEquationMeta;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.util.Lazy;
import org.arend.lib.util.Utils;
import org.arend.lib.util.Values;
import org.arend.lib.util.algorithms.ComMonoidWP;
import org.arend.lib.util.algorithms.groebner.Buchberger;
import org.arend.lib.util.algorithms.idealmem.GroebnerIM;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCMonoidAlgoSolverMeta extends BaseCommutativeMonoidEquationMeta {

  @Override
  protected @Nullable MyHint<List<Integer>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    var axiom = BaseEquationMeta.parseHint(hint, hintType, operations, values, typechecker, this);
    return axiom == null ? null : new MyHint<>(true, false, 1, axiom.typed, axiom.left, axiom.right, normalize(axiom.left), normalize(axiom.right), axiom.originalExpression);
  }

  private MyHint<List<Integer>> invertAxiom(MyHint<List<Integer>> axiom, ConcreteFactory factory, ExpressionTypechecker typechecker) {
    var invAx = factory.app(factory.ref(inv), true, axiom.originalExpression);
    TypedExpression typed = Utils.typecheckWithAdditionalArguments(invAx, typechecker, 0, false);
    if (typed == null) {
      return null;
    }
    return new MyHint<>(true, false, 1, typed, axiom.right, axiom.left, axiom.rightNF, axiom.leftNF, invAx);
  }

  @Override
  protected @Nullable Pair<HintResult<List<Integer>>, HintResult<List<Integer>>> applyHints(@NotNull List<Hint<List<Integer>>> hints, @NotNull List<Integer> left, @NotNull List<Integer> right, @NotNull Lazy<ArendRef> solverRef, @NotNull Lazy<ArendRef> envRef, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker, @NotNull ConcreteFactory factory) {
    var axioms = hints.stream().map(h -> new Pair<>(h.leftNF, h.rightNF)).toList();
    var wpAlgorithm = new ComMonoidWP(new GroebnerIM(new Buchberger()));
    var axiomsToApply = wpAlgorithm.solve(left, right, axioms);

    if (axiomsToApply == null) return null;

    List<Integer> curWord = new ArrayList<>(left);
    ConcreteExpression proofLeft = null;
    for (var axiomInd : axiomsToApply) {
      int[] position = new int[] { 0 };
      var axiom = (MyHint<List<Integer>>)hints.get(axiomInd.proj1);
      var isDirectApp = axiomInd.proj2;
      if (!isDirectApp) {
        axiom = invertAxiom(axiom, factory, typechecker);
        if (axiom == null) return null;
      }
      var result = applyHint(axiom, curWord, position, solverRef, envRef, factory);
      if (result == null) {
        return null;
      }
      proofLeft = proofLeft == null ? result.proof() : factory.app(factory.ref(concat), true, proofLeft, result.proof());
      curWord = result.newNF();
    }

    return new Pair<>(new HintResult<>(proofLeft == null ? factory.ref(typechecker.getPrelude().getIdpRef()) : proofLeft, curWord),
      new HintResult<>(factory.ref(typechecker.getPrelude().getIdpRef()), right));
  }
} /**/
