### Logic.FirstOrder.Algebraic

First-order logic with equality over algebraic (multi-sorted) signatures, including syntactic theories, free models via quotient terms, and semantic structures.

This module builds first-order logic on top of the term language from `Logic.FirstOrder.Term`, extending signatures with predicate symbols and adding atomic formulas (equalities and predicate applications). A `Theory` packages a signature with axioms given as sequents, and `isTheorem` is a directly-defined provability predicate using equational congruence and axiom instantiation. The free model of a closed theory is constructed as `QTerm`, an HIT-style quotient of ground terms by provable equality, with `qinj`/`qapply`/`qmerge`/`qquot` constructors making it a term algebra modulo theory. The semantic side (`Structure`, `Model`) interprets terms and formulas in a multi-sorted carrier and proves soundness via `theoremIsTrue`.

#### Signatures and Formulas

- **`Signature`**: Class extending `TermSig` with predicate symbols `PredSymb` and their arity `predDomain : PredSymb -> Array Sort`.
- **`Formula`**: First-order atomic formulas over a signature, with two constructors — `equality` between terms of the same sort, and `predicate` applying `P : PredSymb` to a tuple of terms with matching sorts.
- **`substF`**: Capture-free substitution of terms for variables in a formula, lifting `subst` from terms.
- **`Sequent`**: A sequent `(V, vf, Γ, φ)` consisting of a finite variable context, a list of hypothesis formulas `Γ`, and a conclusion formula `φ`.

#### Theories and Provability

- **`Theory`**: Class extending `Signature` with `axioms : Sequent -> \Prop` selecting which sequents are axioms.
- **`isTheorem`**: Inductive provability of `psi` from hypotheses `phi` in a theory; constructors are `refl` (reflexivity of equality), `assumption` (use a hypothesis), `substPres` (replace subterms by provably-equal ones inside any formula), and `axiom` (instantiate an axiom whose premises are derivable).
- **`congruence`**: From pointwise provable equalities of arguments, derive `apply f l = apply f l'`.
- **`congruenceF`**: Predicate congruence — transport `predicate P l` along pointwise equalities to `predicate P l'`.
- **`symmetry`**: Symmetry of provable equality.
- **`transitivity`**: Transitivity of provable equality.

#### Free Term Model

- **`QTerm`**: Higher inductive set of ground terms quotiented by theoremhood; constructors `qinj` (embed a closed term), `qapply` (apply an operation to `QTerm`s), `qquot` (identify provably-equal injected terms), and `qmerge` (commute `qinj` with `apply`).
- **`qinj-surj`**: Every `QTerm` is propositionally in the image of `qinj`, witnessing that `QTerm` is the quotient of ground terms.
- **`qinj-equality`**: Characterization of equality in `QTerm`: `qinj t = qinj t'` is equivalent to `isTheorem nil (equality t t')`.
- **`isPartialTheorem`**: Variant of `isTheorem` for partial/relevant logic, with extra constructors (`varDef`, `predDef`, `funcDef`, `partAxiom`) tracking definedness obligations on variables, predicates, function applications, and axiom instances.

#### Structures and Models

- **`Structure`**: Class with carrier `E : Sort -> \Set`, an `operation` for each function symbol, and a `relation` for each predicate symbol.
- **`Structure.Env`**: Environments — sort-respecting assignments of variables to elements.
- **`Structure.interpret`**: Recursive interpretation of a term in an environment.
- **`Structure.subst_interpret`**: Substitution lemma: interpreting a substituted term equals interpreting under the composed environment.
- **`Structure.isFormulaTrue`**: Truth of an atomic formula in an environment (equality of interpretations or holding of the relation).
- **`Structure.subst_isFormulaTrue`**: Substitution lemma for formula truth.
- **`Structure.isSequentTrue`**: Validity of a sequent — for every environment, hypotheses imply conclusion.
- **`Model`**: Class extending `Structure` over a `Theory`, with `isModel` requiring every axiom to hold.
- **`Model.theoremIsTrue`**: Soundness — any `isTheorem` derivation transports to truth in any model under any environment satisfying the hypotheses.
- **`Model.qinterpret`**: Interpretation of `QTerm`s in a model, respecting `qquot` (via soundness) and `qmerge` (by definition of `operation`); exhibits `QTerm` as the initial model.
