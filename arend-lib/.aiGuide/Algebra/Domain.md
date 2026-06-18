### Algebra.Domain

Integral domains: rings without zero divisors, formalized both via tightness apartness and via classical decidability.

This module provides the core hierarchy of domains in two flavors. The constructive `Domain` and `IntegralDomain` are built on `Ring.With#` (rings with a tight apartness relation `#`), where non-zero-ness is expressed as `#0 x` (apartness from zero) and the multiplicative structure restricts to a cancellative monoid on apart-from-zero elements. The classical `StrictDomain` and `StrictIntegralDomain` instead axiomatize the zero-product property directly. The `Dec` subclasses bridge the two by using decidable equality to derive apartness from `/=`. Auxiliary constructions package the non-zero elements as a (cancel) monoid, lift divisibility and GCDs between the full ring and its non-zero submonoid, and connect the formalism to reduced rings, impotent rings, and PP-rings.

#### Domain (apartness-based)

- **`Domain`**: Ring with tight apartness `#` extending `Ring.With#`, `ImpotentRing`, `NonZeroRing`. Axiomatized by `zro#ide` (`0 # 1`) and `#0-*` (apartness from zero is multiplicative). Derives `isReduced`, `isImpotent`, and `zro/=ide`.
- **`Domain.nonZero_*`**: Product of non-zero elements is non-zero (`/=` form).
- **`Domain.nonZero-left`** / **`nonZero-right`**: From `x * y = 0` and one factor non-zero, the other is zero.
- **`Domain.nonZero-cancel-left`** / **`nonZero-cancel-right`**: Cancellation by a non-zero factor on either side.
- **`Domain.nonZeroMonoid`**: The cancellative monoid `\Sigma (x : E) (#0 x)` of apart-from-zero elements with inherited multiplication.
- **`Domain.subMonoid`**: The non-zero part as a `SubMonoid` of the ring's multiplicative monoid.
- **`Domain.ldiv_nonZero`** / **`nonZero_ldiv`**: Transport `LDiv` between the ring and `nonZeroMonoid`.
- **`Domain.inv_nonZero`** / **`nonZero_inv`**: Transport `Inv` between the ring and `nonZeroMonoid`.
- **`Domain.pow_#0`**: Powers preserve apartness from zero.

#### Decidable Domain

- **`Domain.Dec`**: Domain with decidable equality, extending `Domain`, `Ring.Dec`, `StrictDomain`. Implements `zeroProduct` by case analysis, and derives `zro#ide` and `#0-*` from `nonZeroApart`.
- **`Domain.Dec.LDiv_TruncP`**: Truncates a propositionally truncated `LDiv` back to an actual `LDiv`, using cancellation when `a /= 0` and a default witness when `a = 0`.

#### Integral Domain (commutative)

- **`IntegralDomain`**: Commutative `Domain` extending `CRing.With#`, `ImpotentCRing`, `NonZeroCRing`.
- **`IntegralDomain.nonZeroCMonoid`**: Commutative cancellative monoid of non-zero elements, refining `nonZeroMonoid` with `*-comm`.
- **`IntegralDomain.div-inv`**: Mutual divisibility plus non-zeroness implies the divisor's quotient is invertible (i.e., associates).
- **`IntegralDomain.gcd_nonZero`** / **`nonZero_gcd`**: Transport `GCD` between the ring and `nonZeroCMonoid`.
- **`IntegralDomain.divQuotient_LDiv`**: Equivalence between divisibility on `DivQuotient` of the whole ring (via the non-zero embedding) and divisibility on the `DivQuotient` of `nonZeroCMonoid`.
- **`IntegralDomain.toNonZeroDivChain`**: Lifts a `DivChain` on the whole ring's quotient monoid to one on the non-zero quotient monoid.
- **`IntegralDomain.IsRegular_/=0`**: Regular elements coincide exactly with elements `/= 0`.

#### Decidable Integral Domain

- **`IntegralDomain.Dec`**: Decidable integral domain extending `IntegralDomain`, `Domain.Dec`, `CRing.Dec`, `StrictIntegralDomain`, `PPRing`. Implements `isPPRing` via case-split on whether `a = 0`.
- **`IntegralDomain.Dec.fromNonZeroDivChain`**: Converse of `toNonZeroDivChain`: lifts a non-zero `DivChain` back to a full-ring `DivChain`.
- **`IntegralDomain.Dec.oneDimensional-char`**: Characterizes Krull dimension `≤ 1` by the existence, for every `a /= 0` and `u`, of `v` and `n` such that `a` divides `u^n * (1 - u*v)`.

#### Strict Domain (classical)

- **`StrictDomain`**: Ring with the zero-product property `x * y = 0 -> x = 0 || y = 0`, extending `Ring`, `NonZeroSemiring`, `ImpotentRing`. Derives `isReduced` and `isImpotent` from `zeroProduct`.
- **`StrictDomain.zeroBigProd`**: A vanishing finite product `BigProd l = 0` has some index `j` with `l j = 0`.
- **`StrictDomain.zeroOrCancel-left`** / **`zeroOrCancel-right`**: From `x * y = x * z` either `x = 0` or `y = z` (and the symmetric form).
- **`StrictDomain.nonZero-cancel-left`** / **`nonZero-cancel-right`**: Cancellation of a non-zero factor (`/=` formulation).

#### Strict Integral Domain

- **`StrictIntegralDomain`**: Commutative `StrictDomain` extending `ImpotentCRing`.
- **`StrictIntegralDomain.div-associates`**: Mutual divisibility implies (truncated) associatedness.
