### Algebra.Module.Trace

Defines the trace of a square matrix as a linear map and proves its standard properties (cyclicity, transpose-invariance, and value on diagonal/identity matrices).

#### Trace Definition

- **`Trace`**: Linear map `MatrixModule R n n -> RingLModule R` sending a matrix to the sum of its diagonal entries; preserves addition and scalar multiplication.
- **`Trace.diag`**: Linear map extracting the diagonal of a matrix as an array `i => M i i`.
- **`Trace.BigSumHom`**: Linear map summing an array of ring elements via `R.BigSum`.

#### Trace Properties

- **`Trace-transpose`**: `Trace M = Trace (transpose M)`; trace is invariant under transposition.
- **`Trace-prod`**: Cyclicity over a commutative ring: `Trace (A product B) = Trace (B product A)` for `A : Matrix R n m`, `B : Matrix R m n`.
- **`Trace-prod.help`**: Lemma swapping the order of double sums `BigSum_j BigSum_k (A k j * B j k) = BigSum_i BigSum_j (B i j * A j i)`.
- **`Trace-prod.Trace-prod-unfold`**: Expands `Trace (X product Y)` to the explicit double sum `BigSum_i BigSum_j (X i j * Y j i)`.
- **`Trace-conjugation`**: Conjugation invariance over a commutative ring: `Trace (B⁻¹ * A * B) = Trace A` when `B` is invertible.

#### Trace on Special Matrices

- **`Trace-diagonal`**: `Trace (diagonal l) = R.BigSum l`; the trace of a diagonal matrix is the sum of its entries.
- **`Trace-diagonal.diag-diagonal`**: `Trace.diag (diagonal l) = l`; extracting the diagonal of `diagonal l` recovers `l`.
- **`Trace-ide`**: `Trace (Ring.ide) = R.natCoef n`; the trace of the `n×n` identity matrix equals the natural-number coefficient `n` in `R`.
