### Algebra.StrictlyOrdered

Hierarchy of algebraic structures equipped with a strict order compatible with their operations, ranging from ordered monoids to ordered fields.

This module builds an extensive tower of order-compatible algebraic classes. The strict order `<` is taken as primitive at the monoid level (with addition required to be strictly monotone in each argument), while at the group level `<` is reconstructed from a positivity predicate `isPos` so that `x < y ↔ isPos (y - x)`. Linearly ordered variants additionally require comparison/connectedness/cotransitivity-style axioms, yielding lattice and apartness structure for free. Multiplicative compatibility is layered on via ordered (commutative) semirings, rings, and fields, with rings using the positivity predicate to interact with the apartness `#0` of a domain. Decidable variants (`Dec` subclasses) provide trichotomy and constructive case analysis. The `Ordered…Algebra` classes lift this to algebras over an ordered base ring.

#### Ordered Additive Monoids

- **`OrderedAddMonoid`**: Strict poset structure on an additive monoid with monotonicity `<_+-left`, `<_+-right` of addition in each argument. Provides `<_+` (additivity of strict inequality on both sides).
- **`OrderedAbMonoid`**: Commutative ordered additive monoid; right-monotonicity is derived from left-monotonicity via `+-comm`.
- **`BiorderedLatticeAbMonoid`**: Combines `BiorderedLattice` with abelian monoid, supplying join/meet semilattice structure compatible with `+`.
- **`LinearlyBiorderedAbMonoid`**: Adds the linear lattice property to the biordered abelian monoid.

#### Linearly Ordered Abelian Monoid

- **`LinearlyOrderedAbMonoid`**: Linearly biordered ordered abelian monoid with strict cancellation laws `<_+-cancel-left`, `<_+-cancel-right`. Derives `<=_+`, and the compatibility of `+` with `meet`/`join` (`meet_+-left`, `join_+-left`).
- **`<_+-invert-right`**, **`<_+-invert-left`**: From `b + d ≤ a + c` (or strict) plus a comparison between `c` and `d`, conclude `b < a`.
- **`<=_+-left`**, **`<=_+-right`**: Mixed strict/non-strict additivity of inequalities.
- **`BigSum_>0`**: A nonneg array with at least one strictly positive entry has strictly positive `BigSum`.
- **`Big_join-choice`**: For a nonempty array and `e > 0`, some element exceeds the join minus `e` (an `ε`-approximation of the supremum).

#### Preordered and Ordered Additive Groups

- **`PreorderedAddGroup`**: Additive group with a `\Prop`-valued positivity predicate `isPos`, axioms `zro/>0` and additive closure `positive_+`.
- **`OrderedAddGroup`**: Defines `x < y := isPos (y - x)` and derives the strict-order axioms from positivity. Introduces `isNeg x := isPos (negative x)`.
- **`<` (where-clause)**: The defining relation `\infix 4 <` on a `PreorderedAddGroup`.
- **`fromNeg`/`toNeg`**: Conversion between `isNeg (x - y)` and `x < y`.
- **`pos_>0`/`>0_pos`**: `isPos x ↔ 0 < x`.
- **`neg_<0`/`<0_neg`**: `isNeg x ↔ x < 0`.
- **`positive_negative`/`negative_positive`** (and primed variants): Sign-flipping under negation.
- **`negative_<`**, **`negative_<-inv`**, **`negative_<-left`**, **`negative_<-right`**: Order-reversal for `negative`.
- **`<-diff-mid`/`<-diff-mid-conv`**: `x - y < z ↔ x < z + y`.
- **`from>0`/`to>0`**: `0 < y - x ↔ x < y`.
- **`from<0`/`to<0`**: `x - y < 0 ↔ x < y`.

#### Ordered Abelian Groups

- **`OrderedAbGroup`**: Abelian ordered additive group.
- **`<-diff-left`**: From `x - y < z` derive `x - z < y`.
- **`LinearlyOrderedAbGroup`**: Adds comparison `<_+-comparison` (positivity of a sum splits over summands), tightness `<_+-connectedness`, and absolute value compatibility (`abs>=0`). Derives the linearly-ordered cancellation laws and `<-comparison`.
- **`abs_-_<`**: `|x - y| < z` from the two one-sided strict bounds `x - y < z` and `y - x < z`.

#### Ordered Semirings

- **`OrderedSemiring`**: Ordered abelian additive monoid with multiplicative compatibility: `zro<ide`, and four monotonicity laws `<_*_positive-left/right`, `<_*_negative-left/right`.
- **`natCoef_<`**: Strict monotonicity of `natCoef` on `Nat`.
- **`<_*_positive_positive`**, **`<_*_negative_negative`**: Sign of products of strictly positive/negative elements.
- **`<_*_positive_negative`**, **`<_*_negative_positive`**: Mixed-sign products are negative.
- **`<_*`**: Joint strict monotonicity in both factors when both are positive.
- **`pow>0`**: Powers of a positive element are positive.
- **`pow<id`**: For `0 < a < 1` and `k > 1`, `pow a k < a`.

#### Linearly Ordered Semirings

- **`LinearlyOrderedSemiring`**: Adds cancellation `<_*-cancel-left/right` (which case-split on the sign of the cancelled factor) and integrates with `PosetSemiring`. Derives `<=_*_positive-left/right` and `zro<=ide`.
- **`<_*_positive-cancel-left/right`**, **`<_*_negative-cancel-left/right`**: Strict cancellation of multiplication when the canceled factor has known sign.
- **`square_>=0`**: `0 ≤ x * x`.
- **`sum_squares_>0`**: A sum of squares is strictly positive when at least one summand is nonzero.
- **`>0_Inv`/`<0_Inv`/`>=0_Inv`/`<=0_Inv`**: Sign of the multiplicative inverse matches the sign of the original element.
- **`<=_Inv-cancel-left/right`**: Cancel an invertible nonneg factor in non-strict inequalities.
- **`mid_inv`**: Midpoint `(a + b) * t.inv` given an inverse `t` of `1 + 1`.
- **`mid_inv>left`/`mid_inv<right`**: The midpoint lies strictly between `a` and `b` when `a < b`.
- **`denseOrder`**: Promotes the order to a `DenseLinearOrder` using midpoints when `2` is invertible.
- **`natCoef>0`**: `natCoef n > 0` for `n > 0`.
- **`pow_<-monotone`**: `pow` is strictly monotone on nonneg arguments for nonzero exponent.
- **`meet_*-left/right`**, **`join_*-left/right`**: Distributivity of `*` over `meet`/`join` when the multiplied factor is nonneg.
- **`Dec`** (inner class): Decidable linearly ordered semiring (using `LinearOrder.Dec`); derives `<_+-cancel-left`, `<_*-cancel-left`, `<_*-cancel-right` from trichotomy.
- **`splitSum`**: From `x + y ≤ a + b` infer `x ≤ a ∨ y ≤ b` decidably.

#### Ordered Commutative Semirings

- **`OrderedCSemiring`**: Commutative ordered semiring; right-multiplicative laws follow from left ones via `*-comm`.
- **`LinearlyOrderedCSemiring`**: Linear version; derives `<_*-cancel-right` from `<_*-cancel-left`.
- **`Dec`** (inner class): Decidable linearly ordered commutative semiring.

#### Ordered Rings

- **`OrderedRing`**: Combines `Domain`, `LinearlyOrderedSemiring`, `LinearlyOrderedAbGroup`, and `PosetRing`. Adds `ide>zro`, multiplicative closure of `isPos`, sign cancellation laws, and a bridge `positive=>#0`/`negative=>#0`/`#0=>eitherPosOrNeg` linking `isPos`/`isNeg` with the apartness `#0`. Provides default implementations expressing `#0` as `isPos ∨ isNeg`.
- **`positive_*-cancel`**, **`negative_*-cancel`**: Sign of factors recovered from sign of product.
- **`positive_negative_*`**, **`negative_positive_*`**, **`<_*_negative`**: Sign rules for products.
- **`pos#0`/`neg#0`**: Strict inequalities imply additive apartness.
- **`positive_*-cancel-left/right`**: Strict-version cancellation of a positive factor.
- **`denseOrder`**: Promotes to an `UnboundedDenseLinearOrder` (no upper or lower bound, dense via midpoints) when `2` is invertible.
- **`inv-negative`**: A negative invertible element has negative inverse.
- **`abs_*`**, **`abs_ide`**, **`abs_pow`**: Multiplicativity of `abs`.
- **`Dec`** (inner class): Decidable ordered ring; uses additive trichotomy `+_trichotomy` to derive comparison, connectedness, and `pos_*-cancel-left`. The default `#0` becomes nonzero (`/= zro`).

#### Ordered Commutative Rings

- **`OrderedCRing`**: Combines `OrderedRing` with `IntegralDomain` and `LinearlyOrderedCSemiring`; right cancellation derived via commutativity.
- **`Dec`** (inner class): Decidable ordered commutative integral domain.

#### Ordered Fields

- **`OrderedField`**: Ordered commutative ring that is a `Field`. Constructively guarantees that every element is either `< 0` or `+1` is positive (locality), and `pos_*-cancel-left` uses field invertibility.
- **`pinv`**: The inverse of a strictly positive element, packaged with its positivity proof.
- **`pinv-left`/`pinv-right`**: `pinv x * x = 1` and `x * pinv x = 1`.
- **`pinv_pos-inv`**: Involutivity: `pinv (pinv x) = x`.
- **`pinv_<`/`pinv_<-conv`**: `pinv` reverses strict order on positives.
- **`pinv_<=`/`pinv_<=-conv`**: Non-strict order-reversal version.
- **`pinv>0`**: Positivity of `pinv` on positives.
- **`pinv>1`**: For `0 < x < 1`, `1 < pinv x`.
- **`pinv_ide`**: `pinv 1 = 1`.
- **`pinv_*`**: Multiplicativity of `pinv`.
- **`pinv_pow`**: Compatibility with `pow`.

#### Discrete Ordered Fields

- **`DiscreteOrderedField`**: Ordered field with decidable equality (extends `DiscreteField` and `OrderedCRing.Dec`); uses `eitherZeroOrInv` to supply trichotomy.
- **`finv>0`/`finv<0`**: Sign of the discrete inverse `finv`.
- **`finv<1`/`finv>1`**: Position of `finv` relative to `1`.
- **`finv_<`/`finv_<-conv`**: `finv` reverses strict inequality on positives.
- **`finv_<-left`/`finv_<-right`**: Inequality manipulations swapping a `finv` across sides.
- **`<_rotate-right`/`<_rotate-left`** (and `-conv` variants): Move a positive factor across an inequality as `finv`.
- **`finv_<=`**: Non-strict order reversal.
- **`finv>=0`**: Non-strict version of positivity preservation.
- **`finv>0-diff`**: Telescoping identity `finv x - finv (x + 1) = finv (x * (x + 1))`.

#### Ordered Algebras

- **`OrderedAAlgebra`**: Associative algebra over an `OrderedCRing` with order-reflecting/preserving coefficient map (`coef_<`, `coef_<-inv`).
- **`coef_<=`**: Non-strict version of `coef_<`.
- **`OrderedCAlgebra`**: Commutative ordered algebra over an ordered commutative ring.
- **`OrderedFieldAlgebra`**: Commutative ordered algebra whose underlying ring is an ordered field.
