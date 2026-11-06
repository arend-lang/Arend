### Algebra.Ring.Poly.Separable

Separable polynomials, defined by a Bezout identity with the derivative.

A polynomial `p` is separable when `u * p + v * polyDeriv p = 1` for some polynomials `u` and `v`. The module shows that separability is preserved by ring homomorphisms and that over a discrete field of characteristic zero every nonconstant polynomial has a nonconstant separable divisor.

#### Separability

- **`IsSeparable`**: the proposition that there exist `u v` with `u * p + v * polyDeriv p = 1`.
- **`IsSeparable_polyMap`**: if `p` is separable, then `polyMap f p` is separable for any ring homomorphism `f`.
- **`separable-factor`**: over a `DiscreteField` `K` of characteristic zero, a nonzero polynomial `p` with `degree p = suc n` has a divisor `q` of positive degree that is separable.
