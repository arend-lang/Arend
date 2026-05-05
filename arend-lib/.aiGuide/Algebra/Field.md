### Algebra.Field

Fields and discrete fields, where every nonzero element is invertible.

#### Field

- **`Field`**: A commutative ring in which the apartness relation `#0` is defined as invertibility (`Inv x`). Extends `LocalCRing` and `GCDDomain`. Every nonzero element is a unit, and the GCD of any two nonzero elements is `1`.

#### DiscreteField

- **`DiscreteField`**: A field with decidable equality to zero. Extends `Field` and `EuclideanDomain`.
- **`finv`**: The field inverse function `E -> E`, totally defined (with `finv 0 = 0`).
- **`finv_zro`**: `finv 0 = 0`.
- **`finv-right`**: For `x /= 0`, `x * finv x = 1`.
- **`eitherZeroOrInv`**: Decidability — every element is either `0` or invertible (`(x = 0) || Inv x`).
- Implements `isEuclidean` using `finv` to perform division (quotient is `finv y * x`, remainder `0` when `y /= 0`).
- Derives `locality`, `decideEq`, and `nonZeroApart` from `eitherZeroOrInv`.

#### Transport Along Ring Equivalences

- **`aux`**: Proposition-level lemma that `(x = 0) || Inv x` is propositional in a nontrivial commutative ring (zero and invertible are mutually exclusive).
- **`backwards`**: Transfers a `DiscreteField` structure backwards along a ring homomorphism `f : RingHom` that is an equivalence — given `f.Cod` is a discrete field, so is `f.Dom`.
- **`forward`**: Transfers a `DiscreteField` structure forwards along a ring equivalence — given `f.Dom` is a discrete field, so is `f.Cod`. Implemented by applying `backwards` to the inverse equivalence.
