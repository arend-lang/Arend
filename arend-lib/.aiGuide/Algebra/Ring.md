### Algebra.Ring

Defines the hierarchy of (pseudo-)rings and commutative rings, extending semirings with additive group structure and providing variants with apartness and decidable equality.

#### Pseudo-Rings

- **`PseudoRing`**: Class extending `PseudoSemiring` and `AbGroup`. A ring without a multiplicative unit; automatically derives `zro_*-left` and `zro_*-right` (absorption of zero on either side) using additive cancellation and distributivity.
- **`PseudoCRing`**: Class extending `PseudoRing` and `PseudoCSemiring`. The commutative version of `PseudoRing`.

#### Rings

- **`Ring`**: Class extending `PseudoRing` and `Semiring`. A ring with a multiplicative unit `ide`.
- **`NonZeroRing`**: Class extending `Ring` and `NonZeroSemiring`. A ring in which `ide /= zro`.
- **`CRing`**: Class extending `Ring`, `PseudoCRing`, and `CSemiring`. A commutative ring with unit.
- **`NonZeroCRing`**: Class extending `CRing` and `NonZeroRing`. A nontrivial commutative ring.

#### Rings with Apartness

- **`Ring.With#`**: Class extending `Ring` and `AddGroup.With#`. A ring equipped with a tight apartness `#` compatible with multiplication, requiring:
  - **`#0-*-left`**: `#0 (x * y) -> #0 x` — apartness of a product implies apartness of the left factor.
  - **`#0-*-right`**: `#0 (x * y) -> #0 y` — apartness of a product implies apartness of the right factor.
  - **`#0-negative`**: Derived; apartness is preserved under negation, via `negative_* *> ide-right`.
- **`CRing.With#`**: Class extending `CRing` and `Ring.With#`. The commutative case; `#0-*-right` is derived from `#0-*-left` using `*-comm`.

#### Rings with Decidable Equality

- **`Ring.Dec`**: Class extending `AddGroup.Dec` and `Ring.With#`. A ring with decidable equality; derives the apartness laws `#0-*-left` and `#0-*-right` by case analysis on whether each factor equals `zro`, using `nonZeroApart` for the nonzero case.
- **`CRing.Dec`**: Class extending `Ring.Dec` and `CRing.With#`. A commutative ring with decidable equality.
