### Algebra.Solver.Field

Denominator-clearing solver model for commutative rings with inverses supported by witnesses.

A commutative ring has no total inverse, so an inverse is meaningful only together with a witness of `Inv x`. The inverse is treated as an ordinary atom, and the fact that it is an inverse of a ring term is recorded in `InverseOf`. Normalization turns a term into a fraction, and an equality of two terms is equivalent to equality of the cross-products of their fractions, provided the inverses are supported by witnesses. The model is a `GuardedSolverModel` and the cross-products are left as ring terms; comparing them in the normal form of the ring solver is done in `Algebra.Solver.Field.Normal`.

#### Solver model

- **`FieldSolverModel`**: a `GuardedSolverModel R` for a `CRing R` whose terms are ring terms, normal forms are fractions, hypotheses are arrays of inverse declarations, the guard is the witnesses, and `NFEquiv` is equality of the interpreted cross-products.
