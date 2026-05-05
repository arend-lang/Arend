### Arith.Real.Field

This module defines the ordered field structure on `Real` and real multiplication.

#### RealField Instance

- **`RealField`**: Instance of `OrderedFieldAlgebra RatField Real`, providing multiplication, field inverse, and all field axioms.
  - **`unique1`** / **`unique2`** / **`unique3`**: Uniqueness lemmas: two `CoverMap`s from `RealNormed` (or products) to a `SeparatedCoverSpace` that agree on rationals agree everywhere.
  - **`*-rat-locally-uniform`**: Rational multiplication is a `LocallyUniformMap`.
  - **`*-cover-def`**: `CoverMap (RealNormed ⨯ RealNormed) RealNormed` lifting rational multiplication.
  - **`*`**: Real multiplication `x * y`, defined via `*-cover-def`.
  - **`*-rat`**: `x * y = x * y` for rational `x`, `y`.
  - **`pow-rat`**: `pow x n = pow x n` for rational `x`.
  - **`*_>=0-char`**: For `x >= 0`, `y >= 0`: `(x * y).U d` iff `∃ (a : x.U) (b : y.U) (a * b <= d)`.
    - **`*_>0-char`**: Variant when `y > 0`.
    - **`*_>0-char2`**: Variant when `x >= 0` and `y > 0`.
  - **`*-upper`**: `x * y = x ExUpperReal.* y` when `x >= 0`, `y >= 0`.
  - **`pow-upper`**: `pow x n = ExUpperRealSemigroup.pow x n` when `x >= 0`.
  - **`*-cover`**: `CoverMap` for multiplication on `RealNormed ⨯ RealNormed`.
  - **`*_positive-char`**: Open rational interval characterization of `x * y` when `x > 0`, `y > 0`.
  - **`*_positive-L`**: Lower cut characterization of `x * y` when `x > 0`, `y > 0`.
  - **`*_positive-U`**: Upper cut characterization of `x * y` when `x > 0`, `y > 0`.
  - **`real-pos-inv`**: Field inverse for positive reals, constructed as a `Real` with explicit `L` and `U` cuts.
  - **`real-pos-inv>0`**: The positive inverse is positive.
  - **`pos-inv_rat`**: `pinv x x>0 = fromRat (finv x)` for rational `x`.
  - **`finv-left`** / **`finv-right`**: `finv x * x = 1` and `x * finv x = 1` for `x /= 0`.
  - **`half`**: `half x = x * (1/2)`.
