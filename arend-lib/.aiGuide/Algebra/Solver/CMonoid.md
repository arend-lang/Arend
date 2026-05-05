### Algebra.Solver.CMonoid

Reflection-based solver model for commutative monoids, normalizing terms by sorting variable lists.

#### Solver Models

- **`CMonoidSolverModel`**: `SolverModel` instance for a `CMonoid M`. Reuses `MonoidSolverModel`'s `Term` and `normalize`, but interprets normal forms (lists of variable indices) after sorting them via red-black sort, exploiting commutativity to canonicalize.
- **`AbMonoidSolverModel`**: `SolverModel` instance for an `AbMonoid A`, obtained by delegating to `CMonoidSolverModel` on the underlying commutative monoid `AbMonoid.toCMonoid A`.

#### Consistency Lemmas

- **`CMonoidSolverModel.sort-consistent`**: Sorting a variable list preserves its interpretation: `interpretNF env (RedBlack.sort l) = interpretNF env l`. The key step justifying canonicalization by sorting in a commutative setting.
- **`CMonoidSolverModel.perm-consistent`**: Any permutation `p : Perm l l'` yields equal interpretations: `interpretNF env l = interpretNF env l'`. Underlies `sort-consistent`.

#### Power and Axiom Application

- **`CMonoidSolverModel.interpretNF_iterr`**: Interprets the iterated concatenation `iterr (l ++) n v` as `M.pow (interpretNF env l) n * interpretNF env v`. Used to handle repeated applications of an axiom term.
- **`CMonoidSolverModel.apply-axiom`**: Given an axiom `p : interpret env t = interpret env s`, shows that prepending `n` copies of `normalize t` (resp. `normalize s`) to `add` and sorting yields equal interpretations. Lets the solver use user-supplied equational axioms during normalization.
- **`AbMonoidSolverModel.apply-axiom`**: The analogous axiom-application lemma for the abelian monoid solver, transported through `AbMonoid.toCMonoid`.
