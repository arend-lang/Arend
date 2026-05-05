### Algebra.Linear.Solver

Data classes for solving linear (in)equalities over ordered semirings, rings, and rational algebras.

#### Comparison Operations

- **`Operation`**: Tag datatype distinguishing `Less`, `LessOrEquals`, and `Equals` comparisons used by the linear solver.

#### Data Classes

- **`LinearData`**: Base class for linear arithmetic data, extending `AlgData` and overriding the carrier `R` to a `LinearlyOrderedSemiring`. Provides the foundation for solving (in)equalities in a linearly ordered semiring.
- **`LinearSemiringData`**: Combines `LinearData` with `SemiringData` for solving linear problems in a linearly ordered semiring without requiring negation.
- **`LinearRingData`**: Combines `LinearData` with `RingData`, overriding `R` to an `OrderedRing`. Used for solving linear (in)equalities in ordered rings where subtraction is available.
- **`LinearRatData`**: Combines `LinearData` with `RatData`, overriding `R` to an `OrderedRing`. Supports linear problems with rational coefficients in an ordered ring.
- **`LinearRatAlgebraData`**: Combines `RatAlgebraData` with `LinearData`, overriding `R` to an `OrderedAAlgebra` over `RatField`. Used for solving linear (in)equalities in ordered rational algebras.
