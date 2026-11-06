### Algebra.Solver.CSemigroup

Reflective solver model for commutative semigroups.

Terms are those of `SemigroupSolverModel`. The normal form of a term is the non-empty multiset of its variables, represented by a sorted list, whose interpretation is invariant under permutation thanks to commutativity and associativity. No identity element is needed.

#### Solver model

- **`CSemigroupSolverModel`**: a `SolverModel` for a `CSemigroup S`, whose normal forms are the non-empty sorted lists of variables.
