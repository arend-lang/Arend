### Algebra.Ring.Noetherian.HilbertBasisTheorem

Hilbert's basis theorem: the polynomial ring over a Noetherian ring is Noetherian.

The theorem is stated for `IsNoetherian R` (the bar-inductive formulation of Noetherianity from `Algebra.Ring.Noetherian`), with conclusion `IsNoetherian (PolyAlgebra R)`. The proof follows Larchey-Wendling's bar-inductive treatment of constructive algebra. It uses an auxiliary relation `Tailored` on arrays and an induction principle for it, and works with polynomials presented together with a degree bound.

#### Hilbert's basis theorem

- **`HilbertBasisTheorem`**: for a commutative ring `R` with `IsNoetherian R`, the polynomial algebra `PolyAlgebra R` is Noetherian.

#### Auxiliary induction

- **`Tailored`**: a proposition-valued relation on arrays relative to a relation `R` on the elements, comparing two arrays by replacing the head with an `R`-smaller element or by passing to the tail.
- **`tailored-induction`**: induction principle over `Tailored`, for a predicate `P` on arrays, assuming accessibility of the head element with respect to `R`.
