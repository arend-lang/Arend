### Algebra.Monoid.Prime

Irreducible and prime elements in commutative monoids, with the standard equivalence over GCD monoids.

#### Irreducible Elements

- **`Irr`**: Class of irreducible elements in a `CMonoid`. Carries an element `e` that is not invertible (`notInv`), satisfies the irreducibility property `isIrr` (any factorization `e = x * y` forces one factor to be invertible), and is left-cancelable (`isCancelable-left`).
- **`Irr.cancelative`**: Constructs an `Irr` instance over a `CancelCMonoid` from just the non-invertibility and irreducibility conditions, deriving cancellation automatically.

#### Prime Elements

- **`Prime`**: Class extending `Irr`, adding `isPrime`: if `e` divides `x * y`, then `e` divides `x` or `e` divides `y`. The `isIrr` field is derived automatically from `isPrime` via the standard argument.

#### Irreducible Implies Prime in GCD Monoids

- **`irr-isPrime`**: In a `GCDMonoid`, every irreducible element is prime.
- **`irr-isPrime.irr-cmp`**: Auxiliary lemma: for any `a`, an irreducible `p` either divides `a` or is coprime to `a`.

#### Natural Number Specialization

- **`nat_irr`**: For an irreducible `p : Nat` and any divisor `n` of `p`, either `n = 1` or `n = p` (the standard characterization of irreducibles among naturals).
