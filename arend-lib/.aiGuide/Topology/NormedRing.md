### Topology.NormedRing

Normed and valued (pseudo) ring structures combining ring operations with submultiplicative norms, including completeness and the canonical instances on `Rat` and `Real`.

#### Normed Pseudo-Ring Classes

- **`ExPseudoNormedPseudoRing`**: Extends `BoundedExPseudoNormedAbGroup`, `TopSemigroup`, and `PseudoRing`. Adds the submultiplicative inequality `norm_*_<= : norm (x * y) <= norm x * norm y` (in `ExUpperReal`) and derives bilinear continuity of multiplication via `*-cont`.
- **`PseudoNormedPseudoRing`**: Extends `ExPseudoNormedPseudoRing` with `PseudoNormedAbGroup` (norms are bounded reals rather than extended upper reals).
- **`ExPseudoNormedRing`**: Extends `ExPseudoNormedPseudoRing` and `TopRing`, adding `norm_ide_<= : norm 1 <= 1` for the multiplicative identity.
- **`CompleteExNormedRing`**: Extends `ExPseudoNormedRing` and `CompleteExNormedAbGroup`; the underlying group is Cauchy-complete.
- **`PseudoNormedRing`**: Extends `ExPseudoNormedRing` and `PseudoNormedPseudoRing` for rings with real-valued norms.

#### Continuity and Powers

- **`*-locally-uniform`**: Multiplication `(x,y) ↦ x * y` is a `LocallyUniformMap` from `X ⨯ X` to `X`.
- **`norm_<=_pow`**: `norm (pow x n) <= pow (norm x) n` in `ExUpperReal`.
- **`pow-cover`**: For each `n : Nat`, the map `x ↦ pow x n` is a `CoverMap X X`.
- **`lnorm_<=_pow`**: Real-valued analogue: `lnorm (pow x n) <= pow (lnorm x) n` in a `PseudoNormedRing`.

#### Submultiplicativity in Real Norm

- **`lnorm_*_<=`**: `lnorm (x * y) <= lnorm x * lnorm y` for the real-valued local norm.
- **`lnorm_ide_<=`**: `norm 1 <= 1` in a `PseudoNormedRing`.

#### Norm of the Identity

- **`norm_ide=0`**: If `norm 1 = 0` and `norm x` is bounded, then `norm x = 0`.
- **`norm_ide<1`**: If `(norm 1).U 1` (i.e. `norm 1 < 1`), then `norm 1 = 0`.
  - **`norm_ide<1.aux`**: Auxiliary: if `0 < q < 1` and `(norm 1).U q`, then `(norm 1).U (pow q n)` for all `n`.
- **`norm_ide/=0`**: If `norm 1 /= 0`, then `norm 1 = 1`.
- **`norm_ide-left`**: `norm 1 * norm x = norm x`.
- **`norm_ide-right`**: `norm x * norm 1 = norm x`.
- **`norm_zro_ide`**: In a `PseudoNormedRing`, `norm 1 = 0` or `norm 1 = 1` (real-valued dichotomy).

#### Valued (Pseudo) Rings — Multiplicative Norms

- **`ExPseudoValuedPseudoRing`**: Extends `ExPseudoNormedPseudoRing` strengthening submultiplicativity to equality `norm_* : norm (x * y) = norm x * norm y`.
- **`ExPseudoValuedRing`**: Extends `ExPseudoValuedPseudoRing`, `ExPseudoNormedRing`, and `Ring`; requires `norm_ide : norm 1 = 1`.
- **`PseudoValuedRing`**: Extends `PseudoNormedRing` and `ExPseudoValuedRing` (real-valued multiplicative norm on a ring).
- **`CompleteValuedRing`**: Extends `PseudoValuedRing` and `CompleteExNormedRing` (and `CompleteNormedAbGroup`).
- **`CompleteValuedCRing`**: Extends `CompleteValuedRing` and `CRing` for commutative complete valued rings.

#### Valued-Ring Lemmas

- **`lnorm_*`**: Multiplicativity of the real local norm: `lnorm (x * y) = lnorm x * lnorm y`.
- **`norm_pow`**: `norm (pow x n) = pow (norm x) n` in `ExUpperReal`.
- **`lnorm_ide`**: `norm 1 = 1` in a `PseudoValuedRing`.
- **`lnorm_pow`**: `lnorm (pow x n) = pow (lnorm x) n` for the real local norm.

#### Concrete Instances

- **`RatValue`**: The data of an `ExPseudoValuedRing` structure on `Rat` built from `RatField`.
- **`RatValuedRing`**: Instance making `Rat` a `PseudoValuedRing`, combining `RatNormed` and `RatField`.
- **`RealValuedRing`**: Instance making `Real` a `CompleteValuedCRing`, combining `RealNormed` and `RealField`.
