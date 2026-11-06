### Algebra.Solver.Field.Normal

The field solver model with cross-products compared in the normal form of the commutative ring solver.

It is `FieldSolverModel` in which the equivalence of two fractions is equality of the ring-solver normal forms of their cross-products, so that `idp` proves the cleared equality whenever the two cross-products agree as polynomials. It is a separate module because `simplify` depends on `Algebra.Solver.Field`, while `CRingSolverModel` depends on definitions that use `simplify`.

#### Solver model

- **`FieldNormalSolverModel`**: a `GuardedSolverModel R` for a `CRing R`, with the terms, normal forms, hypotheses and guard of `FieldSolverModel`, and `NFEquiv` stating equality of the `RingSolverModel` normal forms of the cross-products.
