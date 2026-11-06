### Arith.Log

The logarithm log(1 + w) as a power series on the open unit ball of a real Banach algebra, and its inverse relation to exp.

The module defines the rational Taylor coefficients `log1p-coef` of log(1 + w) = Σ (-1)^{n-1}/n · w^n, bounds them by 1, and defines `log1+` on elements of norm below 1. It proves the formal-series identity `compose-coef-expm1-log1p` showing (exp - 1) ∘ log(1 + ·) is the identity over Q (using a differential-equation argument), and lifts it analytically to `exp-log1p`: exp(log(1 + w)) = 1 + w for central `w` of norm below 1. The converse composition is in `Arith.Log.Properties`.

#### Coefficients

- **`log1p-coef`**: The rational Taylor coefficients of log(1 + w): `0` at index `0` and `(-1)^{n-1}/n` at index `n >= 1`.
- **`log1p-coef-abs-<=1`**: Every coefficient satisfies `|log1p-coef n| <= 1`.

#### The logarithm series

- **`log1+`**: `log1+ w |w|<1` is log(1 + w), the sum of the power series with coefficients `log1p-coef`, for `w` in a `RealBanachAlgebra` with `norm w < 1`. Its `\where` block contains convergence lemmas and `asFld`, the series as a formal line diffeomorphism over Q.

#### Inverse of exp

- **`compose-coef-expm1-log1p`**: The formal composition `(exp - 1) ∘ log(1 + ·)` over Q has coefficients of the identity series `w`.
- **`exp-log1p`**: `exp (log1+ w |w|<1) = 1 + w` for a central `w` with norm less than 1 in a real Banach algebra.
