### Algebra.Monoid.Prime

Irreducible and prime elements in commutative monoids.

This module formalizes the classical distinction between irreducibility (cannot be factored non-trivially) and primality (divides a product implies divides a factor) in the setting of commutative monoids. `Irr` packages an element together with proofs that it is non-invertible and that any factorization splits into a unit, while `Prime` strengthens this with the divisibility-based prime condition. The key structural result is that in GCD monoids these notions coincide, recovering the familiar equivalence from elementary number theory; a specialization to `NatSemiring` shows that divisors of an irreducible natural number are trivial.

#### Irreducibles

- **`Irr`**: Class for an irreducible element `e : M` in a `CMonoid`, carrying a proof `notInv` that `e` is not invertible, an `isIrr` field that any factorization `e = x * y` forces one factor to be invertible, and a left-cancellation property `isCancelable-left` for multiplication by `e`.
- **`Irr.decide`**: Decision procedure: given `e = x * y`, returns a constructive `Or` of `Inv x` and `Inv y` (rather than the propositional truncation `||`).
- **`Irr.notIdemp`**: An irreducible element is not idempotent: `e = e * e` is impossible.
- **`Irr.cancelative`**: Constructor for `Irr` over a `CancelCMonoid`, where left-cancellation comes for free from the cancellative structure.

#### Primes

- **`Prime`**: Class extending `Irr` with `isPrime`: whenever `e` divides a product `x * y`, it divides one of the factors. The `isIrr` field is automatically derived from `isPrime` plus cancellation.

#### Irreducible ⇔ Prime in GCD Monoids

- **`irr-isPrime`**: In a `GCDMonoid`, every irreducible element is prime — the classical theorem that irreducibility and primality coincide in the presence of GCDs.
- **`irr-isPrime.irr-cmp`**: Helper showing that for any `a : M`, an irreducible `p` either divides `a` or is coprime with `a`.

#### Natural Numbers

- **`nat_irr`**: Divisors of an irreducible natural number are trivial: if `n` divides an irreducible `p` in `NatSemiring`, then `n = 1` or `n = p`.
