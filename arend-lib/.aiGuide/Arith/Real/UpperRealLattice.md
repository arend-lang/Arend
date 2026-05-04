### Arith.Real.UpperRealLattice

This module provides the complete lattice structure on `ExUpperReal`.

#### ExUpperRealLattice Instance

- **`ExUpperRealLattice`**: Instance of `CompleteLattice` for `ExUpperReal`, with `Meet` (pointwise infimum) and `Join` (pointwise supremum with rounding).
  - **`*n_Join`**: `n *n Join g = Join (λ j => n *n g j)` when `n ≠ 0`.
  - **`*n_Join'`**: Variant of `*n_Join` requiring `∃ J` instead of `n ≠ 0`.
  - **`+_Join`**: `Join g + Join g = Join (λ j => g j + g j)`.
  - **`*n_join`**: `n *n (x ∨ y) = n *n x ∨ n *n y`.
  - **`+_join`**: `(x ∨ y) + (x ∨ y) = (x + x) ∨ (y + y)`.
  - **`Join-square`**: `Join g * Join g = Join (λ j => g j * g j)` when `J` is inhabited.
  - **`join-square`**: `(x ∨ y) * (x ∨ y) = x * x ∨ y * y`.
