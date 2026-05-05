### Algebra.Domain.Valuation

Valuation rings: integral domains in which divisibility is total, presented as both strict Bézout domains and local commutative rings.

#### Classes

- **`ValuationRing`**: Extends `StrictBezoutDomain` and `LocalCRing`. A commutative domain where, for any two elements `a b`, either `a` divides `b` or `b` divides `a` (`isValuationRing`). The class derives:
  - **`locality`**: Local ring property — for any `a`, either `a` or `a + 1` is invertible, obtained from totality of divisibility on `a` and `a + 1`.
  - **`isStrictBezout`**: Strict Bézout property — derived directly from total divisibility by picking trivial coefficients.
  - **`isValuationRing`** (alternate derivation): Reconstructs total divisibility from the Bézout/GCD data, using `sum1Array` to decompose unity and the local helper `lem` (which upgrades a divisibility witness `g | a` to `a | g` whenever a unit relates them).
