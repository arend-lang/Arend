### Algebra.Domain.GCD

GCD domains: integral domains in which every pair of nonzero elements has a greatest common divisor.

A `GCDDomain` extends `IntegralDomain` with the existence of GCDs for nonzero elements (truncated, since GCDs are unique only up to units). The development connects GCD domains to `CancelGCDMonoid` via the submonoid of nonzero elements, so monoid-level GCD theory is reused. The decidable subclass `Dec` strengthens this to a fully decidable `GCDMonoid` with constructive GCDs, and proves that decidable GCD domains are integrally closed by analyzing coprime representations of localized elements and using monicity of integral dependence polynomials.

#### Main Class

- **`GCDDomain`**: Integral domain where any two `#0`-nonzero elements have a `GCD` (truncated). Extends `IntegralDomain`.
- **`isGCDDomain`**: The defining field — produces `TruncP (GCD x y)` from `#0 x` and `#0 y`.

#### Connection to Monoid GCD Theory

- **`nonZeroGCDMonoid`**: Packages the nonzero elements of the domain as a `CancelGCDMonoid`, transferring GCDs from the domain to its multiplicative monoid of nonzero elements.

#### Basic GCD Constructions

- **`gcd_0`**: `GCD x 0 x` — any element is its own GCD with zero.
- **`gcd_sum`**: Given a `GCD g` of `a` and `b`, the same `g` is a GCD of `a + b * d` and `b`. Used for Euclidean-style reductions.

#### Decidable GCD Domains

- **`Dec`**: Decidable GCD domains. Extends `GCDDomain`, `GCDMonoid`, `IntegralDomain.Dec`, and `IntegrallyClosedDomain`. Provides total GCDs (not just for nonzero pairs) via decidable equality with zero.
- **`isGCD`**: Builds GCDs for arbitrary pairs by handing nonzero pairs to `gcd` and zeros to `gcd_0`.
- **`gcd-ldistr`**: GCDs distribute over multiplication: `c * gcd(x,y)` is a GCD of `c*x` and `c*y`. Splits on `c = 0` and otherwise uses left-cancellation.
- **`isIntegrallyClosedDomain`**: Every element of the field of fractions integral over the domain lies in the domain. Proved via coprime representation and the `loc_poly` lemma about monic polynomials over localizations.

#### Decidable-GCD-Domain Lemmas

- **`coprime-repr`**: Every element of the localization at the nonzero submonoid has a representation `a/b` with `GCD a b 1`.
- **`oneDimensional_Bezout`**: A one-dimensional decidable GCD domain (Krull dimension ≤ 1) is strictly Bezout.
- **`split_*`**: If `a | b * c`, then `a` factors as `a = a1 * a2` with `a1 | b` and `a2 | c`. The key splitting property of GCD domains.
- **`div_unit`**: Decidability of divisibility is equivalent to decidability of being a unit.

#### Auxiliary Lemmas (in `\where`)

- **`gcd`**: Builds a `TruncP (GCD a b)` for arbitrary `a`, `b` in a GCD domain with decidable equality, by reducing to the `#0` case via `nonZeroApart`.
- **`coprime-repr-aux`**: Constructs a coprime numerator/denominator representation of a localization element, using GCDs to cancel common factors.
- **`loc_poly`**: If a polynomial over the localization, with monic preimage, vanishes at `inl~ (a, b, Sb)`, then `b` divides some power of `a`. The technical core of integral closure.
