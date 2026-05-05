### Algebra.Domain.GCD

GCD domains: integral domains in which every pair of nonzero elements has a greatest common divisor.

#### Main Class

- **`GCDDomain`**: Extends `IntegralDomain` with the axiom `isGCDDomain` asserting that any two apart-from-zero elements `x, y` admit a (truncated) `GCD`.

#### Decidable GCD Domains

- **`GCDDomain.Dec`**: A decidable GCD domain extending `GCDDomain`, `GCDMonoid`, `IntegralDomain.Dec`, and `IntegrallyClosedDomain`. Provides:
  - **`isGCD`**: Builds the monoid-level GCD from `isGCDDomain` via the `nonZeroApart` witness.
  - **`gcd-ldistr`**: Left-distributivity of GCD over multiplication, splitting on whether the multiplier is zero (trivial GCD with `0`) or nonzero (using `GCDMonoid.gcd-ldistr_cancel`).
  - **`isIntegrallyClosedDomain`**: Proves that a decidable GCD domain is integrally closed by representing each element of the field of fractions in coprime form and using `loc_poly` together with `gcd_pow_div` to extract a divisibility witness.

#### Helper Lemmas

- **`gcd`**: Promotes `isGCDDomain` to a total GCD-producing function on a decidable set, given a way `d` to convert `x /= 0` into `M.#0 x`.
- **`coprime-repr-aux`**: Every element of the localization `LocRing D.subMonoid` is represented by a coprime pair `(a, b)` with `b` apart from zero and `GCD a b 1`.
- **`loc_poly`**: If a monic polynomial `p` over a commutative ring `R` evaluates to `0` at a fraction `a/b` in a localization, then `b` divides some power `a^n` of `a` (existence of `n` and a divisibility witness).
