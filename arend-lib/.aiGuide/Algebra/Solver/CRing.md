### Algebra.Solver.CRing

Solver model for commutative rings, extending the ring solver with monomial sorting to exploit commutativity.

This module instantiates `SolverModel` for any commutative ring `R`, reusing the term and normal-form representations from the non-commutative ring solver but composing the interpretation of normal forms with `sortMonomials`. Sorting monomials canonicalizes products under commutativity, so syntactically distinct but mathematically equal terms (e.g., `x * y` and `y * x`) collapse to the same normal form. The supporting lemmas handle the equivalence between term equality and vanishing of the normalized difference, and the `apply-axioms` lemma supports rewriting via a list of known equalities, scaled by arbitrary monomials.

#### Solver Model

- **`CRingSolverModel`**: `SolverModel R` instance for a commutative ring `R`. Uses `Term Int` and `NF Int` from the ring/semiring solver infrastructure, normalizes via the ring `normalize`, and interprets normal forms by first applying `sortMonomials` to canonicalize monomials under commutativity before evaluating with `ringInterpretNF`.

#### Equality Lemmas

- **`terms-equality`**: Reflects normal-form vanishing back to term equality: if the sorted normalized form of `t :+ :negative s` interprets to `0`, then `interpret env t = interpret env s`. This is the core soundness lemma used to discharge equalities by computation.
- **`terms-equality-conv`**: Converse direction — if `interpret env t = interpret env s`, then the sorted normalized difference interprets to `0`. Allows transporting hypotheses into the solver's normal-form world.
- **`diffNF`**: If the interpretation of the concatenated list `left ++ negate(right)` (as monomials) vanishes, then `left` and `right` have equal interpretations under `RingData env`. Provides the bridge between subtraction-in-normal-form and equality of normal forms.

#### Axiom Application

- **`apply-axioms`**: Supports solving with auxiliary axioms. Given a list of known term equalities `t_i = s_i` paired with multiplier monomials, this lemma equates the sorted normal form of the sum `add + Σ multiplier_i * (t_i - s_i)` with `ringInterpretNF' add`. Used by the solver to apply user-provided equational rewrites scaled by arbitrary monomial coefficients.
