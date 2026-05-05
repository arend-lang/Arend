### Algebra.Ring.Reduced

Reduced rings (no nonzero nilpotents) and their refinements: pp-rings and impotent rings.

#### Reduced Rings

- **`ReducedRing`**: Ring extension where `a * a = 0` implies `a = 0` (no nonzero nilpotents of order 2, hence no nilpotents at all).
  - **`isReduced`**: The defining axiom: `a * a = 0 -> a = 0`.
- **`ReducedCRing`**: Commutative reduced ring, extending `ReducedRing` and `CRing`.

#### PP-Rings

- **`PPRing`**: Commutative reduced ring in which every element has a "principal projector" — extends `ReducedCRing`.
  - **`isPPRing`**: For each `a`, there exists `u` with `a = u * a` and such that `a * x = 0 -> u * x = 0` (so `u` is an idempotent annihilator-equivalent to `a`).
  - The `isReduced` axiom is derived automatically from `isPPRing`.

#### Impotent Rings

- **`ImpotentRing`**: Nonzero reduced ring in which the only idempotents are `0` and `1`, extending `ReducedRing` and `NonZeroRing`.
  - **`isImpotent`**: `a * a = a -> (a = 0) || (a = 1)` (connectedness of the spectrum).
- **`ImpotentRing.subring`**: Transfers an `ImpotentRing` structure along an injective ring homomorphism `f : R -> E` into an `ImpotentRing`, making `R` impotent.
- **`ImpotentCRing`**: Commutative version, extending `ImpotentRing`, `ReducedCRing`, and `NonZeroCRing`.
- **`ImpotentCRing.subring`**: Transfers an `ImpotentCRing` structure along an injective ring homomorphism from a commutative ring into an `ImpotentRing`.
