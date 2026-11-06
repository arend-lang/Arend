### Logic.FirstOrder.Algebraic

First-order logic with equality over algebraic (multi-sorted) signatures, including syntactic theories, free models via quotient terms, and semantic structures.

This module builds first-order logic on top of the term language from `Logic.FirstOrder.Term`, extending signatures with predicate symbols and adding atomic formulas (equalities and predicate applications). A `Theory` packages a signature with axioms given as sequents, and `IsTheorem` is a directly-defined provability predicate using assumptions, equational rewriting and axiom instantiation. The semantic side (`Structure`, `Model`) interprets terms and formulas in a multi-sorted carrier and proves soundness via `theoremIsTrue`. The free model construction (`QTerm`) lives in `Logic.FirstOrder.Algebraic.FreeModel`.

#### Signatures and Formulas

- **`Signature`**: Class extending `TermSig` with predicate symbols `PredSymb` and their arity `predDomain : PredSymb -> Array Sort`.
- **`Formula`**: First-order atomic formulas over a signature, with two constructors — `equality` between terms of the same sort, and `predicate` applying `P : PredSymb` to a tuple of terms with matching sorts.
- **`substF`**: Capture-free substitution of terms for variables in a formula, lifting `subst` from terms.
- **`substF1`**: Substitutes a single term `t : Term U s` for the extra variable of sort `s` in a formula over `U` extended by one variable (`Or (U s') (s = s')`).
- **`Sequent`**: A sequent `(V, vf, Γ, φ)` consisting of a finite variable context, a list of hypothesis formulas `Γ`, and a conclusion formula `φ`.

#### Theories and Provability

- **`Theory`**: Class extending `Signature` with `axioms : Sequent -> \Prop` selecting which sequents are axioms.
- **`Theory.IsTheorem`**: Provability predicate `IsTheorem phi psi` (a `\Prop`): `psi` follows from the list of hypotheses `phi` by reflexivity of equality, assumption, rewriting along a provable equality (`equalityElim`), or instantiating an axiom of the theory (`axiom`) with provable hypotheses.
- **`congruence`**: From pointwise provable equalities of arguments, derive `apply f l = apply f l'`.
- **`symmetry`**: Symmetry of provable equality.
- **`transitivity`**: Transitivity of provable equality.
- **`Theory.congruenceT`**: Substitution respects provable equality: if two substitutions `rho` and `rho'` agree up to provable equality on every variable, then `subst t rho` and `subst t rho'` are provably equal.
- **`Theory.congruenceP`**: Predicates respect provable equality of arguments: from pointwise provable equalities `l j = l' j` and `predicate P l`, derive `predicate P l'`.
- **`Theory.substPres`**: If two substitutions agree up to provable equality and `substF psi rho` is provable, then so is `substF psi rho'`.
- **`Theory.IsPartialTheorem`**: Provability predicate for partial structures (a `\Prop`): like `IsTheorem` but equalities are only derivable when both sides are known to be defined (`varDef`, `funcDef`, `predDef`), with symmetry, assumption-projection, rewriting and axiom instantiation requiring definedness of the substituted terms.

#### Structures and Models

- **`Structure`**: Class with carrier `E : Sort -> \Set`, an `operation` for each function symbol, and a `relation` for each predicate symbol.
- **`Structure.Env`**: Environments — sort-respecting assignments of variables to elements.
- **`Structure.interpret`**: Recursive interpretation of a term in an environment.
- **`Structure.subst_interpret`**: Substitution lemma: interpreting a substituted term equals interpreting under the composed environment.
- **`Structure.IsFormulaTrue`**: Truth of an atomic formula in an environment (equality of interpretations or holding of the relation).
- **`Structure.subst_IsFormulaTrue`**: Substitution lemma for formulas: `IsFormulaTrue rho (substF phi tau)` equals truth of `phi` in the environment `\lam u => interpret rho (tau u)`.
- **`Structure.IsSequentTrue`**: Validity of a sequent — for every environment, hypotheses imply conclusion.
- **`Model`**: Class extending `Structure` over a `Theory`, with `isModel` requiring every axiom to hold.
- **`Model.theoremIsTrue`**: Soundness — any `IsTheorem` derivation transports to truth in any model under any environment satisfying the hypotheses.
