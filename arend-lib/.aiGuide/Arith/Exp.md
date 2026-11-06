### Arith.Exp

The exponential function on real Banach algebras, defined as the limit of the standard power series.

This module constructs `exp : A → A` for any real Banach algebra `A` as the sum of the power series `Σ (1/n!) · xⁿ`. The construction uses `funcLimit` together with `powerSeriesConv-funcConv` to upgrade pointwise convergence of the power series into a continuous (cover) map, available as `expMap`. It then proves the basic algebraic properties: `exp 0 = 1`, the exponent law for commuting arguments, and invertibility of `exp x`.

#### Exponential Map

- **`exp`**: The exponential function `exp (a : A) : A` on a `RealBanachAlgebra` `A`. Defined as the sum of the power series with coefficients `1/n!` (lifted from `Rat` to `A` via `A.fromRat`).
- **`expMap`**: `exp` is a cover map `CoverMap A A exp` on any `RealBanachAlgebra`.
- **`exp-1-coef`**: Coefficients `Rat` of the series of `exp x - 1`: `0` at index `0` and the exponential coefficient `1/n!` at positive indices.

#### Properties

- **`exp_zro`**: `exp 0 = 1`.
- **`exp_+-noncomm`**: Exponent law for commuting elements: if `x * y = y * x` then `exp (x + y) = exp x * exp y`.
- **`exp_+`**: Exponent law `exp (x + y) = exp x * exp y` in a commutative `RealBanachCAlgebra`.
- **`exp_negative-left`**, **`exp_negative-right`**: `exp (negative x) * exp x = 1` and `exp x * exp (negative x) = 1`.
- **`exp_Inv`**: `exp x` is invertible (`Monoid.Inv (exp x)`), with inverse `exp (negative x)`.
