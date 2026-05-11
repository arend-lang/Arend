### Algebra.Solver.CGroup

Reflective decision procedure for equalities in commutative groups and abelian groups.

This module specializes the generic group solver to the commutative case, where the normal form of a term collapses from a free word (used for general groups) to an integer-coefficient vector indexed by variables — i.e., an element of `Z^n`. Normalization simply tallies signed exponents per variable, so equality of two terms reduces to coefficient-wise equality of their difference with zero. The `AbGroupSolverModel` is obtained by reusing `CGroupSolverModel` through the `AbGroup.toCGroup` coercion, and a separate `apply-axioms` interface lets the solver discharge goals using user-supplied equational hypotheses scaled by integer coefficients.

#### Solver Models

- **`CGroupSolverModel`**: Instance of `SolverModel` for a commutative group `G`. Uses `Term` (from `GroupSolverModel`) for syntax, `Array Int n` as the normal form (one integer exponent per variable), and provides `normalize`, `interpret`, and `interpretNF` together with the consistency proof.
- **`AbGroupSolverModel`**: Instance of `SolverModel` for an abelian group `A`, defined by composing `CGroupSolverModel` with `AbGroup.toCGroup`.

#### Normal Form Interpretation

- **`toArray`**: Expands a coefficient vector `cs : Array Int` against an environment into the explicit list of group elements `[g_i^{c_i}]`, recursing on the magnitude of each integer and emitting `inverse` for negative coefficients.
- **`sBigProd`**: Right-folded product of an array of group elements, returning `ide` on the empty list (a non-padded variant of `BigProd`).
- **`sBigProd_::`**: `sBigProd (a :: l) = a * sBigProd l`, the cons-step rewrite for `sBigProd`.
- **`interpretNF`**: Interprets a normal form `l : Array Int n` in environment `env` as `sBigProd (toArray l env)`.
- **`interpretNF'`**: Alternative interpretation via `BigProd (\lam j => ipow (env j) (l j))`, used for algebraic manipulation.
- **`interpretNF-correct`**: Bridges the two interpretations: `sBigProd (toArray cs env) = interpretNF' cs env`.

#### Normalization

- **`normalize`**: Recursively converts a `Term n` into its `Array Int n` exponent vector — `var v` becomes a unit basis vector at `v`, `:ide` becomes the zero vector, `:inverse t` negates entrywise, and `t :* s` adds entrywise.
- **`interpretNF-consistent`**: `interpretNF env (normalize t) = interpret env t`, the soundness of the normal form via `interpretNF`.
- **`interpretNF-consistent'`**: Same statement via the auxiliary `interpretNF'`.

#### Equality Discharge

- **`terms-equality`**: Forward direction: if the normal form of `t :* :inverse s` is the identity, then `interpret env t = interpret env s`. This is the lemma the solver tactic uses to close a goal.
- **`terms-equality-conv`**: Converse: an equality of interpretations yields the identity normal form for `t :* :inverse s`.

#### Axiom Application

- **`apply-axioms'`**: Given a list of hypotheses `(c_i, t_i, s_i, p_i : ⟦t_i⟧ = ⟦s_i⟧)`, proves that the integer-linear combination `Σ_i c_i · (normalize t_i − normalize s_i)` interprets (via `interpretNF'`) to `ide`. Lets the solver scale and combine user-provided equations.
- **`apply-axioms'.interpretNF_+`**: `interpretNF'` distributes addition of coefficient vectors into the group product.
- **`apply-axioms'.interpretNF_negative`**: `interpretNF'` takes coefficient negation to group inversion.
- **`apply-axioms'.interpretNF-coef`**: `interpretNF'` takes scalar multiplication of coefficients by `c` to integer power `ipow _ c`.
- **`apply-axioms'.interpretNF_BigSum`**: `interpretNF'` of a column-wise integer sum equals the group `BigProd` of the row interpretations.
- **`apply-axioms`**: User-facing variant: shows that adding the axiom-derived combination to a `right` vector leaves `interpretNF env right` unchanged, allowing axiom-based rewriting of one side of a goal.

#### Abelian Group Wrappers

- **`AbGroupSolverModel.terms-equality`**: Forward axiom-discharge lemma reformulated for abelian groups via `AbGroup.toCGroup`.
- **`AbGroupSolverModel.terms-equality-conv`**: Converse direction for the abelian wrapper.
- **`AbGroupSolverModel.apply-axioms`**: `apply-axioms` lifted to abelian groups, used by the abelian-group solver tactic.
