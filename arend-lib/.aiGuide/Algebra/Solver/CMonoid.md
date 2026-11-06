### Algebra.Solver.CMonoid

Solver model for commutative monoids and abelian monoids, reducing equality of terms to sortable lists of variable indices.

This module specializes the generic `SolverModel` interface to `CMonoid` (and `AbMonoid`) by representing normal forms as lists of variable indices that are sorted before interpretation. Building on `MonoidSolverModel`, it exploits commutativity by canonicalizing each list via red-black sort, so terms that differ only by permutation of factors compare equal. The included permutation and iteration lemmas provide the consistency proofs needed to discharge the `interpretNF-consistent` obligation and to apply user-supplied axioms inside larger normalized expressions.

#### Solver Models

- **`CMonoidSolverModel`**: Constructs a `SolverModel` for any `CMonoid M`. Reuses `Term` and `normalize` from `MonoidSolverModel`, but represents normal forms as `List (Fin n)` and interprets them after `RedBlack.sort`, so commutativity is absorbed by sorting.
- **`AbMonoidSolverModel`**: Solver model for an `AbMonoid A`, obtained by transporting `CMonoidSolverModel` along `AbMonoid.toCMonoid`.

#### Consistency Lemmas

- **`CMonoidSolverModel.sort-consistent`**: Sorting a normal-form list does not change its interpretation: `interpretNF env (RedBlack.sort l) = interpretNF env l`. Justifies replacing a list by its sorted form during normalization.
- **`CMonoidSolverModel.perm-consistent`**: Any two permutation-equivalent lists `l`, `l'` (`Sort.Perm l l'`) interpret to the same monoid element. The core commutativity fact underlying `sort-consistent`.

#### Iteration and Axiom Application

- **`CMonoidSolverModel.interpretNF_iterr`**: Interpreting `iterr (l ++) n v` (i.e., `l` prepended `n` times to `v`) factors as `pow (interpretNF env l) n * interpretNF env v`. Used to reason about repeated occurrences of a normalized subterm.
- **`CMonoidSolverModel.apply-axiom`**: Given an axiom `interpret env t = interpret env s`, replacing `normalize t` by `normalize s` inside `iterr (... ++) n add` and re-sorting yields equal interpretations. Lets the solver use user-provided equalities as rewrite rules within sorted normal forms.
- **`AbMonoidSolverModel.apply-axiom`**: Abelian-monoid analogue of the above, parameterized by an `AbMonoid` and an axiom over `AbMonoid.toCMonoid A`.
