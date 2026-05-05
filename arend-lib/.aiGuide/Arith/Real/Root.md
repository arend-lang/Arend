### Arith.Real.Root

This module provides square root and n-th root constructions for real and upper-real numbers.

#### Square Root (Upper Real)

- **`upperReal-sqrt`**: Square root for `ExUpperReal`, returning an `ExUpperReal` with `U q = (q >= 0, x.U (q * q))`.
  - **`sqrt_U`**: Characterization of `(sqrt x).U q`.
  - **`sqrt-rat`**: `sqrt (fromRat x) = fromRat (sqrt x)` for rational `x >= 0`.
  - **`sqrt_<=`**: Monotonicity: `x <= y` implies `sqrt x <= sqrt y`.
  - **`sqrt_<`**: Strict monotonicity.
  - **`sqrt_>=0`**: `0 <= sqrt x`.
  - **`sqrt-square`**: `sqrt (x * x) = x` when `x >= 0`.
  - **`square-sqrt`**: `(sqrt x) * (sqrt x) = x`.
  - **`sqrt-unique`**: If `y * y = x` and `y >= 0`, then `y = sqrt x`.
  - **`sqrt-bounded`**: `sqrt x` is bounded when `x` is bounded.

#### N-th Root (Upper Real)

- **`upperReal-root`**: N-th root for `ExUpperReal`, returning `ExUpperReal`.
  - **`root_U`**: Characterization of `(root n x).U q`.
  - **`root-rat`**: `root n (fromRat x) = fromRat (root n x)` for rational `x >= 0`.
  - **`root_<=`**: Monotonicity.
  - **`root_>=0`**: `0 <= root n x`.
  - **`root-pow`**: `root n (pow x n) = x` when `x >= 0`.
  - **`pow-root`**: `pow (root n x) n = x`.
  - **`root-bounded`**: `root n x` is bounded when `x` is bounded.

#### Real Square Root

- **`real-sqrt`**: Square root for `Real` (non-negative), returning a `Real`.
  - **`real-sqrt>=0`**: `sqrt x >= 0`.
  - **`real-sqrt-square`**: `sqrt (x * x) = abs x`.
  - **`real-square-sqrt`**: `(sqrt x) * (sqrt x) = x` when `x >= 0`.
  - **`real-sqrt-unique`**: If `y * y = x` and `y >= 0`, then `y = sqrt x`.
  - **`real-sqrt-rat`**: `sqrt (fromRat x) = fromRat (sqrt x)` for rational `x >= 0`.

#### Real N-th Root

- **`real-root`**: N-th root for `Real` (non-negative), returning a `Real`.
  - **`real-root>=0`**: `root n x >= 0`.
  - **`real-root-pow`**: `root n (pow x n) = abs x` (for odd `n`) or `= x` (when `x >= 0`).
  - **`real-pow-root`**: `pow (root n x) n = x` when `x >= 0`.
  - **`real-root-rat`**: `root n (fromRat x) = fromRat (root n x)` for rational `x >= 0`.
