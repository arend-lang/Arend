### Algebra.Ring.Poly.Derivative

The formal derivative of a polynomial over a ring, with its algebraic properties and a second-order Taylor formula.

`polyDeriv` is defined by recursion on the `padd` presentation of `Poly R`. The module proves that it is additive and `R`-linear, satisfies the Leibniz rule, lowers degree bounds, and commutes with `polyMap`. It also relates the derivative to the deflation `deflate p z` (the quotient by `X - z`): evaluating the deflation at `z` gives `p'(z)`, which yields a second-order Taylor expansion with exact remainder. In characteristic zero over a domain, a polynomial of positive degree has a non-zero derivative.

#### Definition and coefficients

- **`polyDeriv`**: the formal derivative of `p : Poly R`.
- **`polyCoef_polyDeriv`**: the `n`-th coefficient of `polyDeriv p` is `natCoef (suc n) * polyCoef p (suc n)`.

#### Algebraic laws

- **`polyDeriv_+`**: the derivative is additive.
- **`polyDeriv_*c`**: the derivative commutes with scalar multiplication by `r : R`.
- **`polyDeriv_*`**: the Leibniz rule `(p * q)' = p' * q + p * q'`.
- **`degree<_polyDeriv`**: if `degree< p (suc n)` then `degree< (polyDeriv p) n`.
- **`polyDeriv_polyMap`**: the derivative commutes with coefficientwise application of a ring homomorphism, `polyMap`.

#### Evaluation and Taylor expansion

- **`polyEval_deflate-diag`**: for a commutative ring, evaluating `deflate p z` at `z` equals evaluating `polyDeriv p` at `z`.
- **`polyTaylor2`**: second-order Taylor formula `p(y) - p(z) - p'(z) * (y - z) = q(y) * (y - z)^2`, where `q` is `deflate (deflate p z) z`.

#### Characteristic zero

- **`polyDeriv/=0`**: over a domain `K` of characteristic zero, if `p` has a nonzero coefficient in position `suc n`, then `polyDeriv p /= 0`.
