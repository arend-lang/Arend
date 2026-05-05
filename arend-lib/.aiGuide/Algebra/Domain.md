### Algebra.Domain

Integral domains and related ring structures with apartness or strict (decidable) zero-product properties.

#### Domains with Apartness

- **`Domain`**: Ring with tight apartness `#` where `0 # 1` and apartness from zero is multiplicatively closed; extends `Ring.With#`, `ImpotentRing`, `NonZeroRing`. Automatically reduced (no nonzero nilpotents) and impotent (idempotents are `0` or `1`).
  - **`zro#ide`**: `0` is apart from `1`.
  - **`#0-*`**: If `x # 0` and `y # 0` then `x * y # 0`.
- **`Domain.Dec`**: Domain with decidable equality; extends `Domain`, `Ring.Dec`, `StrictDomain`. Apartness coincides with `≠`, and `zeroProduct` follows from `#0-*`.

#### Commutative Integral Domains

- **`IntegralDomain`**: Commutative domain; extends `Domain`, `CRing.With#`, `ImpotentCRing`, `NonZeroCRing`.
- **`IntegralDomain.Dec`**: Decidable integral domain; extends `IntegralDomain`, `Domain.Dec`, `CRing.Dec`, `StrictIntegralDomain`, `PPRing`. Provides `isPPRing` by case analysis on whether an element is zero.

#### Strict (Zero-Product) Domains

- **`StrictDomain`**: Ring satisfying the classical zero-product property: `x * y = 0 → x = 0 ∨ y = 0`; extends `Ring`, `NonZeroSemiring`, `ImpotentRing`. Used when apartness is unavailable but case analysis on equality with zero is acceptable. Automatically reduced and impotent.
  - **`zeroProduct`**: The defining axiom.
- **`StrictIntegralDomain`**: Commutative `StrictDomain`; extends `StrictDomain`, `ImpotentCRing`.
