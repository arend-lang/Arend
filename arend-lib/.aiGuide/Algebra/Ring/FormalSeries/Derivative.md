### Algebra.Ring.FormalSeries.Derivative

The formal derivative of power series, with the Leibniz rule, the power rule and the chain rule.

`deriv` sends a series `f` to the series with coefficients `(n+1) * f_{n+1}`. The module proves that it sends the unit to zero, satisfies the Leibniz rule, differentiates powers, and obeys the chain rule for composition `fcompose` (from `Algebra.Ring.FormalSeries.Composition`) when the inner series has zero constant term.

#### Derivative

- **`deriv`**: the formal derivative of `a : FSeries A`.
- **`deriv_ide`**: the derivative of the unit series `1` is `0`.
- **`deriv-leibniz`**: Leibniz rule `D(a * b) = D a * b + a * D b`.

#### Powers and composition

- **`deriv-pow-conv-suc`**: in a commutative ring, power rule `D(b^(n+1)) = (n+1) *c (b^n * D b)`.
- **`deriv-compose`**: chain rule `D(a o b) = (D a o b) * D b` coefficientwise, for `b` with `b 0 = 0`.
