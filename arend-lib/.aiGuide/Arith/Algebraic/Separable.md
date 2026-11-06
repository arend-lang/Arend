### Arith.Algebraic.Separable

A separable complex polynomial has its derivative bounded below wherever the polynomial itself is small.

Separability is expressed by a Bezout identity `u*p + v*p' = 1`; evaluating it at a point turns smallness of `p` into a lower bound for `p'`, with no root-separation or resultant theory. The disc-uniform form supplies the invertible derivative from which Newton's method (`Arith.Algebraic.Newton`) starts.

#### Lower bound on the derivative

- **`separable-deriv-lower`**: For a separable `p : Poly ComplexField` (`IsSeparable p`) and a radius `R >= 0`, there are `delta > 0` and `D > 0` such that for every `z` with `|z| <= R` and `|p(z)| <= delta` we have `1/2 <= D * |p'(z)|`.
