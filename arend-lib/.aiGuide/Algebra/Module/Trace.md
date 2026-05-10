### Algebra.Module.Trace

The trace of a square matrix as a linear map, with its core algebraic properties.

This module defines the matrix trace as a composition of two linear maps: extracting the diagonal of an `n × n` matrix into an array, then summing the array via the ring's big sum. Packaging trace as a `LinearMap` between module structures (`MatrixModule R n n` to `RingLModule R`) makes its additivity and scalar-compatibility part of its type, so they can be reused without re-proving. The remaining lemmas establish the standard trace identities — invariance under transposition, cyclicity on products, conjugation invariance, behavior on diagonal matrices, and the value on the identity matrix.

#### Trace as a Linear Map

- **`Trace`**: The trace `LinearMap (MatrixModule R n n) (RingLModule R)`, sending a matrix `M` to `BigSum (diag M)`.
- **`Trace.diag`**: The diagonal-extraction `LinearMap (MatrixModule R n n) (ArrayLModule n (RingLModule R))`, mapping `M` to the array `i ↦ M i i`.
- **`Trace.BigSumHom`**: The summation `LinearMap (ArrayLModule n (RingLModule R)) (RingLModule R)`, given by `R.BigSum`.

#### Transpose and Cyclicity

- **`Trace-transpose`**: `Trace M = Trace (transpose M)` — the trace is invariant under transposition.
- **`Trace-prod`**: For a commutative ring `R`, `Trace (A product B) = Trace (B product A)` for `A : Matrix R n m` and `B : Matrix R m n` — the cyclic property of the trace.
- **`Trace-prod.Trace-prod-unfold`**: Unfolds `Trace (X product Y)` to the double sum `∑ᵢ ∑ⱼ X i j * Y j i`.
- **`Trace-prod.help`**: The double-sum swap `∑ⱼ ∑ₖ A k j * B j k = ∑ᵢ ∑ⱼ B i j * A j i`, the core combinatorial step behind cyclicity (uses commutativity of `R`).
- **`Trace-conjugation`**: `Trace (B⁻¹ product A product B) = Trace A` — conjugation invariance, derived from cyclicity.

#### Trace on Distinguished Matrices

- **`Trace-diagonal`**: `Trace (diagonal l) = R.BigSum l` — trace of a diagonal matrix is the sum of its diagonal entries.
- **`Trace-diagonal.diag-diagonal`**: Auxiliary identity `Trace.diag (diagonal l) = l`.
- **`Trace-ide`**: `Trace (Ring.ide {MatrixRing R n}) = R.natCoef n` — the trace of the `n × n` identity matrix is the natural-number coefficient `n` in `R`.
