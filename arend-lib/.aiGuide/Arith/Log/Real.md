### Arith.Log.Real

The real logarithm on the positive reals, extended from the local series by dyadic scaling.

A local logarithm `ell` (the series log(1 + (a - 1)) on 0 < a < 2) is shown additive on its domain and inverse to `exp` (`ell-add`, `exp-ell`). With `log2` defined as a difference of two values of `ell` and `ell-double` giving ell(2y) = ell(y) + log 2, the total function `log-Real` is defined by scaling x into (0, 2) by a power of 2; the result is independent of the choice of scaling. The module ends with the inverse laws `exp-log-Real` and `log-Real-exp`, proving that `log-Real` and `exp` are mutually inverse.

#### Local logarithm

- **`ell`**: `ell a a>0 a-lt2` is log(1 + (a - 1)), defined for `0 < a < 2` through `log1+`.
- **`ell-add`**: `ell a + ell b = ell (a * b)` whenever `a`, `b` and `a * b` lie in `(0, 2)`.
- **`exp-ell`**: `exp (ell a) = a` for `a` in `(0, 2)`.

#### Logarithm of 2

- **`log2`**: log 2, defined as `ell (3/2) - ell (3/4)`; `log2.exp-log2` proves `exp log2 = 2`.
- **`ell-double`**: `ell (2 * y) = ell y + log2` for `0 < y < 1`.

#### Total logarithm

- **`log-Real`**: The real logarithm `log-Real x x>0` on positive reals, defined as `ell (x / 2^n) + n * log2` for any dyadic shift `n` placing `x / 2^n` in `(0, 2)`; the value is independent of the shift.
- **`exp-log-Real`**: `exp (log-Real x x>0) = x` for every `x > 0`.
- **`log-Real-exp`**: `log-Real (exp y) _ = y` for every real `y`.
