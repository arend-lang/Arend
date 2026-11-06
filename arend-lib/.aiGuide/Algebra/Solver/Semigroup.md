### Algebra.Solver.Semigroup

Reflective solver model for (non-commutative) semigroups.

Terms are variables and products. The normal form of a term is the non-empty list of its variables, flattened by associativity. No identity element is involved, so the model works for any `Semigroup`.

#### Solver model

- **`SemigroupSolverModel`**: a `SolverModel` for a `Semigroup S`, whose normal forms are a head variable together with a list of further variables.
