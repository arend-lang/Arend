### Arith.Trig.ArcTan

The arctangent on (-1, 1) as a power series, with the identities tan(arctan x) = x and the double- and sum-angle formulas.

The module defines the Taylor coefficients of arctan (`arctan-coef-Rat`, `arctan-coef`), the series sum `arctan-gen` in an arbitrary real Banach algebra, and the real `arctan x` for `|x| < 1`. Through the identity log(1 + ix) - log(1 - ix) = 2i·arctan x in the complex numbers, it proves `tan-arctan` (sin(arctan x) = x·cos(arctan x)), from which come the squared forms `cos-arctan-sq` and `sin-arctan-sq`, the double-angle formulas `cos-double-arctan` and `sin-double-arctan`, and the addition formula `arctan-addition`. Trigonometric functions come from `Arith.Trig.Real`; the arctan values are used to define π in `Arith.Real.Pi`.

#### Coefficients

- **`arctan-coef-Rat`**: The rational Taylor coefficients of arctan: `0` at even indices (`arctan-coef-Rat.even`) and `(-1)^k / (2k+1)` at index `2k+1` (`arctan-coef-Rat.odd`).
- **`arctan-coef`**: The same coefficients as real numbers.

#### The arctangent

- **`arctan-gen`**: The arctangent of `x` with `norm x < 1` in a `RealBanachAlgebra`, defined as the sum of the power series with coefficients `arctan-coef-Rat`. Its `\where` block contains `arctan-coef-abs-<=1` (`|c_n| <= 1`) and the convergence witness.
- **`arctan`**: The real arctangent `arctan x |x|<1` for `abs x < 1`, a specialization of `arctan-gen`.
- **`arctan-coef-powerSeries-IsSeriesSum`**: The Taylor series `Σ c_n x^n` with coefficients `arctan-coef` sums to `arctan x` for `abs x < 1`.

#### Trigonometric identities

- **`tan-arctan`**: `sin (arctan x) = x * cos (arctan x)` for `abs x < 1`, i.e. tan(arctan x) = x.
- **`cos-arctan-sq`**: `(1 + x²) * cos²(arctan x) = 1`.
- **`sin-arctan-sq`**: `(1 + x²) * sin²(arctan x) = x²`.
- **`cos-double-arctan`**: `(1 + x²) * cos (2 * arctan x) = 1 - x²`, the product form of cos(2 arctan x) = (1 - x²)/(1 + x²).
- **`sin-double-arctan`**: `(1 + x²) * sin (2 * arctan x) = 2x`, the product form of sin(2 arctan x) = 2x/(1 + x²).
- **`arctan-addition`**: Division-free arctan addition formula: `sin (arctan a + arctan b) * (1 - a*b) = (a + b) * cos (arctan a + arctan b)` for `abs a < 1` and `abs b < 1`.
