### Algebra.Solver.CRing

Solver model for commutative rings, providing normalization and decision procedures for ring equalities via sorted monomials.

#### Solver Model

- **`CRingSolverModel`**: Constructs a `SolverModel` instance for any commutative ring `R : CRing`. Uses `Term Int` as the term language (with integer coefficients), `NF Int` as the normal form, and interprets normalized terms by sorting monomials before evaluation. Establishes consistency between the syntactic normalization and semantic interpretation through composition of `ringInterpretNF-correct`, `interpretNF-correct`, monomial sorting, and `normalize-consistent`.

#### Equality Lemmas

- **`terms-equality`**: Soundness of the solver — if the normal form of `t - s` interprets to `0`, then `interpret env t = interpret env s`. Used to discharge ring equalities by reducing them to a normal-form check.
- **`terms-equality-conv`**: Converse of `terms-equality` — a semantic equality `interpret env t = interpret env s` implies the normalized difference evaluates to `0`. Useful when feeding known ring equalities into the solver.
- **`diffNF`**: Given two normal forms `left` and `right`, if interpreting `left ++ (-right)` yields `0`, then `left` and `right` have equal interpretations. Reduces equality of normal forms to a single zero-check.

#### Axiom Application

- **`apply-axioms`**: Applies a list of known ring equalities (provided as `(nf, t, s, proof)` tuples) as rewrite axioms during normalization. For an additional normal form `add`, shows that the combined sorted interpretation equals `ringInterpretNF' add`. Enables the solver to close goals modulo a supplied set of equational hypotheses.
