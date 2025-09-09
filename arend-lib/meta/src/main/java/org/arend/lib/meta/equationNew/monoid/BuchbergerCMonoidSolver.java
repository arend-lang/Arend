package org.arend.lib.meta.equationNew.monoid;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.util.Pair;
import org.arend.lib.meta.equation.MonoidSolver;
import org.arend.lib.meta.equationNew.term.EquationTerm;
import org.arend.lib.util.algorithms.ComMonoidWP;
import org.arend.lib.util.algorithms.groebner.Buchberger;
import org.arend.lib.util.algorithms.idealmem.GroebnerIM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;

public class BuchbergerCMonoidSolver {}
  /*
  extends BaseCommutativeMonoidEquationMeta {

  public ConcreteExpression solve(EquationTerm term1, EquationTerm term2, List<Hint<List<Integer>>> axioms) {
    var term1NF = normalize(term1); var term2NF = normalize(term2);
    int alphabetSize = term1NF.isEmpty() ? 0 : Collections.max(term1NF) + 1;
    alphabetSize = term2NF.isEmpty() ? alphabetSize : Integer.max(alphabetSize, Collections.max(term2NF) + 1);
    for (var axiom : axioms) {
      if (!axiom.leftNF.isEmpty()) {
        alphabetSize = Integer.max(alphabetSize, Collections.max(axiom.leftNF) + 1);
      }
      if (!axiom.rightNF.isEmpty()) {
        alphabetSize = Integer.max(alphabetSize, Collections.max(axiom.rightNF) + 1);
      }
    }

    var word1Pow = ComMonoidWP.elemsSeqToPowersSeq(term1.nf, alphabetSize);
    var word2Pow = ComMonoidWP.elemsSeqToPowersSeq(term2.nf, alphabetSize);
    List<Pair<List<Integer>, List<Integer>>> axiomsPow = new ArrayList<>();

    for (MonoidSolver.Equality axiom : axioms) {
      axiomsPow.add(new Pair<>(ComMonoidWP.elemsSeqToPowersSeq(axiom.lhsNF, alphabetSize), ComMonoidWP.elemsSeqToPowersSeq(axiom.rhsNF, alphabetSize)));
    }

    var wpAlgorithm = new ComMonoidWP(new GroebnerIM(new Buchberger()));
    var axiomsToApply = wpAlgorithm.solve(word1Pow, word2Pow, axiomsPow);

    List<Integer> curWord = new ArrayList<>(term1.nf);

    if (axiomsToApply == null) return null;

    ConcreteExpression proofTerm = null;

    for (Pair<Integer, Boolean> axiom : axiomsToApply) {
      var equalityToApply = axioms.get(axiom.proj1);
      var isDirect = axiom.proj2;
      var powsToRemove = isDirect ? axiomsPow.get(axiom.proj1).proj1 : axiomsPow.get(axiom.proj1).proj2;
      var rhsNF = isDirect ? equalityToApply.rhsNF : equalityToApply.lhsNF;
      var lhsTerm = isDirect ? equalityToApply.lhsTerm : equalityToApply.rhsTerm;
      var rhsTerm = isDirect ? equalityToApply.rhsTerm : equalityToApply.lhsTerm;
      ConcreteExpression rhsTermNF = computeNFTerm(rhsNF);
      ConcreteExpression nfProofTerm = equalityToApply.binding; // factory.ref(equalityToApply.binding);

      if (!isDirect) {
        nfProofTerm = factory.app(factory.ref(meta.inv), true, singletonList(nfProofTerm));
      }

      //if (!isNF(equalityToApply.lhsTerm) || !isNF(equalityToApply.rhsTerm)) {
      nfProofTerm = factory.appBuilder(factory.ref(meta.commTermsEqConv))
        .app(factory.ref(dataRef), false)
        .app(lhsTerm)
        .app(rhsTerm)
        .app(nfProofTerm)
        .build();
      //}

      var indexesToReplace = ComMonoidWP.findIndexesToRemove(curWord, powsToRemove);
      var subwordToReplace = new ArrayList<Integer>();
      var newWord = new ArrayList<>(curWord);

      for (Integer integer : indexesToReplace) {
        subwordToReplace.add(newWord.get(integer));
        newWord.remove(integer.intValue());
      }

      int prefix = 0;
      for (int i = 0; i < indexesToReplace.size(); ++i) {
        int ind = indexesToReplace.get(i);
        indexesToReplace.set(i, ind + i - prefix);
        prefix = ind + i + 1;
      }

      for (int i = rhsNF.size() - 1; i >= 0; --i) {
        newWord.addFirst(rhsNF.get(i));
      }

      if (subwordToReplace.size() > 1) {
        ConcreteExpression sortProofLeft = factory.appBuilder(factory.ref(meta.sortDef)).app(computeNFTerm(subwordToReplace)).build();
        nfProofTerm = factory.app(factory.ref(meta.concat), true, Arrays.asList(sortProofLeft, nfProofTerm));
      }
      ConcreteExpression sortProofRight = factory.appBuilder(factory.ref(meta.sortDef)).app(rhsTermNF).build();
      sortProofRight = factory.app(factory.ref(meta.inv), true, singletonList(sortProofRight));
      nfProofTerm = factory.app(factory.ref(meta.concat), true, Arrays.asList(nfProofTerm, sortProofRight));

      ConcreteExpression stepProofTerm = factory.appBuilder(factory.ref(meta.commReplaceDef))
        .app(factory.ref(dataRef), false)
        .app(computeNFTerm(curWord))
        .app(computeNFTerm(indexesToReplace))
        .app(rhsTermNF)
        .app(nfProofTerm)
        .build();
      if (proofTerm == null) {
        proofTerm = stepProofTerm;
      } else {
        proofTerm = factory.app(factory.ref(meta.concat), true, Arrays.asList(proofTerm, stepProofTerm));
      }

      curWord = newWord;
    }

    if (proofTerm == null) {
      proofTerm = factory.ref(typechecker.getPrelude().getIdpRef());
    } else {
      ConcreteExpression sortProof = factory.appBuilder(factory.ref(meta.sortDef)).app(computeNFTerm(curWord)).build();
      proofTerm = factory.app(factory.ref(meta.concat), true, Arrays.asList(proofTerm, sortProof));
    }

    return factory.appBuilder(factory.ref(meta.commTermsEq))
      .app(factory.ref(dataRef), false)
      .app(term1.concrete)
      .app(term2.concrete)
      .app(proofTerm)
      .build();
  }
} /**/
