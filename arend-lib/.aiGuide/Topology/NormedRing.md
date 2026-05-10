### Topology.NormedRing

Normed and valued ring structures combining ring operations with norms valued in extended upper reals.

This module layers ring structure onto normed abelian groups, producing a hierarchy from submultiplicative norms (`norm (x*y) <= norm x * norm y`) up to true valuations (`norm (x*y) = norm x * norm y`). The `Ex`-prefixed classes use extended upper reals (allowing infinite norms) for pseudo-rings, while their non-`Ex` counterparts assume bounded norms valued in `Real`. Continuity of multiplication is derived automatically from the submultiplicative bound via bilinear local uniformity, and the design culminates in concrete instances `RatValuedRing` and `RealValuedRing` that witness `Rat` and `Real` as complete valued (commutative) rings.

#### Submultiplicative Pseudo-Rings

- **`ExPseudoNormedPseudoRing`**: Extends `BoundedExPseudoNormedAbGroup`, `TopSemigroup`, and `PseudoRing`. Submultiplicative norm `norm (x*y) <= norm x * norm y` with multiplication continuity derived from bilinear local uniformity.
- **`*-locally-uniform`**: Multiplication on `X ⨯ X` is a locally uniform map.
- **`PseudoNormedPseudoRing`**: Bounded variant extending `ExPseudoNormedPseudoRing` and `PseudoNormedAbGroup` (real-valued norm).
- **`lnorm_*_<=`**: Submultiplicativity for the real-valued local norm `lnorm`.

#### Normed Rings (with Identity)

- **`ExPseudoNormedRing`**: Extends `ExPseudoNormedPseudoRing` and `TopRing`, adding `norm 1 <= 1`.
- **`norm_<=_pow`**: `norm (pow x n) <= (norm x)^n`.
- **`pow-cover`**: `pow __ n : X -> X` is a cover map.
- **`CompleteExNormedRing`**: Extends `ExPseudoNormedRing` and `CompleteExNormedAbGroup`.
- **`PseudoNormedRing`**: Bounded variant extending `ExPseudoNormedRing` and `PseudoNormedPseudoRing`.
- **`lnorm_ide_<=`**: `norm 1 <= 1` in the real-valued setting.

#### Norm of Identity Dichotomy

- **`norm_ide=0`**: If `norm 1 = 0`, then every bounded norm `norm x = 0`.
- **`norm_ide<1`**: If `norm 1 < 1` (witnessed by an upper-bound on rationals), then `norm 1 = 0`.
  - **`aux`**: Helper showing `(norm 1).U (q^n)` for `0 < q < 1`, used to drive `norm 1` below any positive rational.
- **`norm_ide/=0`**: If `norm 1 /= 0`, then `norm 1 = 1`.
- **`norm_ide-left`**, **`norm_ide-right`**: `norm 1 * norm x = norm x` and `norm x * norm 1 = norm x` (uses the dichotomy).
- **`norm_zro_ide`**: In a `PseudoNormedRing`, `norm 1 = 0` or `norm 1 = 1`.
- **`lnorm_<=_pow`**: `lnorm (pow x n) <= (lnorm x)^n`.

#### Valued Pseudo-Rings (Multiplicative Norm)

- **`ExPseudoValuedPseudoRing`**: Extends `ExPseudoNormedPseudoRing`, strengthening submultiplicativity to equality `norm (x*y) = norm x * norm y`.
- **`lnorm_*`**: Multiplicativity of `lnorm` in a `PseudoValuedRing`.

#### Valued Rings

- **`ExPseudoValuedRing`**: Extends `ExPseudoValuedPseudoRing`, `ExPseudoNormedRing`, and `Ring`, with `norm 1 = 1`.
  - **`norm_Inv-cancel-left`**, **`norm_Inv-cancel-right`**: Cancellation of `norm x` in extended upper-real products when `x` is invertible.
- **`norm_pow`**: `norm (pow x n) = (norm x)^n` in a valued ring.
- **`PseudoValuedRing`**: Extends `PseudoNormedRing` and `ExPseudoValuedRing` (bounded valued ring).
- **`lnorm_ide`**: `norm 1 = 1` for the real-valued local norm.
- **`lnorm_pow`**: `lnorm (pow x n) = (lnorm x)^n`.

#### Complete Valued Rings

- **`CompleteValuedRing`**: Extends `PseudoValuedRing` and `CompleteNormedAbGroup` (via `CompleteExNormedRing`).
- **`CompleteValuedCRing`**: Adds commutativity, extending `CompleteValuedRing` and `CRing`.

#### Concrete Instances

- **`RatValue`**: The `ExPseudoValuedRing` structure on `Rat` using `RatField` operations.
- **`RatValuedRing`**: `Rat` as a `PseudoValuedRing` via `RatNormed` and `RatField`.
- **`RealValuedRing`**: `Real` as a `CompleteValuedCRing` via `RealNormed` and `RealField`.
