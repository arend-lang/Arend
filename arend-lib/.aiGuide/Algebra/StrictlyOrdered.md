### Algebra.StrictlyOrdered

Hierarchy of algebraic structures equipped with a strict order compatible with their operations, ranging from ordered monoids to ordered fields.

This module builds an extensive tower of order-compatible algebraic classes. The strict order `<` is taken as primitive at the monoid level (with addition required to be strictly monotone in each argument), while at the group level `<` is reconstructed from a positivity predicate `isPos` so that `x < y ↔ isPos (y - x)`. Linearly ordered variants additionally require comparison/connectedness/cotransitivity-style axioms, yielding lattice and apartness structure for free. Multiplicative compatibility is layered on via ordered (commutative) semirings, rings, and fields, with rings using the positivity predicate to interact with the apartness `#0` of a domain. Decidable variants (`Dec` subclasses) provide trichotomy and constructive case analysis. The `Ordered…Algebra` classes lift this to algebras over an ordered base ring.

#### Ordered Additive Monoids

- **`OrderedAddMonoid`**: Strict poset structure on an additive monoid with monotonicity `<_+-left`, `<_+-right` of addition in each argument. Provides `<_+` (additivity of strict inequality on both sides).
- **`OrderedAddMonoid.<_+`**: `a < c` and `b < d` give `a + b < c + d`.
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
- **`OrderedAddGroup.positive_negative'`**: From `isPos x` conclude `isNeg (negative x)`.
- **`OrderedAddGroup.negative_positive'`**: From `isNeg (negative x)` conclude `isPos x`.
- **`negative_<`**, **`negative_<-inv`**, **`negative_<-left`**, **`negative_<-right`**: Order-reversal for `negative`.
- **`<-diff-mid`/`<-diff-mid-conv`**: `x - y < z ↔ x < z + y`.
- **`from>0`/`to>0`**: `0 < y - x ↔ x < y`.
- **`from<0`/`to<0`**: `x - y < 0 ↔ x < y`.

#### Ordered Abelian Groups

- **`OrderedAbGroup`**: Abelian ordered additive group.
- **`<-diff-left`**: From `x - y < z` derive `x - z < y`.
- **`LinearlyOrderedAbGroup`**: Adds comparison `<_+-comparison` (positivity of a sum splits over summands), and tightness `<_+-connectedness`; it is also a `LatticeAbGroup`, so `abs` and its properties are available. Derives the linearly-ordered cancellation laws and `<-comparison`.
- **`abs_-_<`**: `|x - y| < z` from the two one-sided strict bounds `x - y < z` and `y - x < z`.

#### Ordered Semirings

- **`OrderedSemiring`**: Ordered abelian additive monoid with multiplicative compatibility: `zro<ide`, and four monotonicity laws `<_*_positive-left/right`, `<_*_negative-left/right`.
- **`natCoef_<`**: Strict monotonicity of `natCoef` on `Nat`.
- **`<_*_positive_positive`**, **`<_*_negative_negative`**: Sign of products of strictly positive/negative elements.
- **`<_*_positive_negative`**, **`<_*_negative_positive`**: Mixed-sign products are negative.
- **`<_*`**: Joint strict monotonicity in both factors when both are positive.
- **`pow>0`**: Powers of a positive element are positive.
- **`pow<id`**: For `0 < a < 1` and `k > 1`, `pow a k < a`.
- **`OrderedSemiring.pow<ide`**: For `0 < a < 1`, `pow a (suc k) < 1`.
- **`OrderedSemiring.pow_<-degree`**: For `0 < a < 1` and `n < k`, `pow a k < pow a n`: powers of `a` strictly decrease in the exponent.

#### Linearly Ordered Semirings

- **`LinearlyOrderedSemiring`**: Adds cancellation `<_*-cancel-left/right` (which case-split on the sign of the cancelled factor) and integrates with `PosetSemiring`. Derives `<=_*_positive-left/right` and `zro<=ide`.
- **`square_>=0`**: `0 ≤ x * x`.
- **`sum_squares_>0`**: A sum of squares is strictly positive when at least one summand is nonzero.
- **`>0_Inv`/`<0_Inv`/`>=0_Inv`/`<=0_Inv`**: Sign of the multiplicative inverse matches the sign of the original element.
- **`mid_inv`**: Midpoint `(a + b) * t.inv` given an inverse `t` of `1 + 1`.
- **`mid_inv>left`/`mid_inv<right`**: The midpoint lies strictly between `a` and `b` when `a < b`.
- **`LinearlyOrderedSemiring.denseOrder`**: Promotes the order to a `DenseLinearOrder` using midpoints when `2` is invertible.
- **`natCoef>0`**: `natCoef n > 0` for `n > 0`.
- **`pow_<-monotone`**: `pow` is strictly monotone on nonneg arguments for nonzero exponent.
- **`LinearlyOrderedSemiring.Dec`** (inner class): Decidable linearly ordered semiring (using `LinearOrder.Dec`); derives `<_+-cancel-left`, `<_*-cancel-left`, `<_*-cancel-right` from trichotomy.
- **`splitSum`**: From `x + y ≤ a + b` infer `x ≤ a ∨ y ≤ b` decidably.
- **`LinearlyOrderedSemiring.<_*_positive-cancel-left`**: Cancellation of a nonnegative left factor from a strict inequality: `0 <= x` and `x * y < x * z` give `y < z`.
- **`LinearlyOrderedSemiring.<_*_positive-cancel-right`**: Cancellation of a nonnegative right factor: `0 <= z` and `x * z < y * z` give `x < y`.
- **`LinearlyOrderedSemiring.<_*_negative-cancel-left`**: Cancellation of a nonpositive left factor: `x <= 0` and `x * y < x * z` give `z < y`.
- **`LinearlyOrderedSemiring.<_*_negative-cancel-right`**: Cancellation of a nonpositive right factor: `z <= 0` and `x * z < y * z` give `y < x`.
- **`LinearlyOrderedSemiring.square_<-mono`**: For `0 <= x` and `x < y`, `x * x < y * y`.
- **`LinearlyOrderedSemiring.square_<=-cancel`**: For `0 <= x`, `0 <= y` and `x * x <= y * y`, conclude `x <= y`.
- **`LinearlyOrderedSemiring.square_<-cancel`**: For `0 <= x`, `0 <= y` and `x * x < y * y`, conclude `x < y`.
- **`LinearlyOrderedSemiring.<=_Inv-cancel-left`**: Non-strict cancellation of an invertible nonnegative left factor: `x * y <= x * z` gives `y <= z`.
- **`LinearlyOrderedSemiring.<=_*-cancel-right`**: Non-strict cancellation of a positive right factor: `y * x <= z * x` and `0 < x` give `y <= z`.
- **`LinearlyOrderedSemiring.<=_Inv-cancel-right`**: Non-strict cancellation of an invertible nonnegative right factor: `y * x <= z * x` gives `y <= z`.
- **`LinearlyOrderedSemiring.meet_*-left`**: Multiplication by a nonnegative `a` on the left distributes over `∧`: `a * (b ∧ c) = (a * b) ∧ (a * c)`.
- **`LinearlyOrderedSemiring.meet_*-right`**: Multiplication by a nonnegative `c` on the right distributes over `∧`: `(a ∧ b) * c = (a * c) ∧ (b * c)`.
- **`LinearlyOrderedSemiring.join_*-left`**: Multiplication by a nonnegative `a` on the left distributes over `∨`: `a * (b ∨ c) = (a * b) ∨ (a * c)`.
- **`LinearlyOrderedSemiring.join_*-right`**: Multiplication by a nonnegative `c` on the right distributes over `∨`: `(a ∨ b) * c = (a * c) ∨ (b * c)`.

#### Ordered Commutative Semirings

- **`OrderedCSemiring`**: Commutative ordered semiring; right-multiplicative laws follow from left ones via `*-comm`.
- **`LinearlyOrderedCSemiring`**: Linear version; derives `<_*-cancel-right` from `<_*-cancel-left`.
- **`LinearlyOrderedCSemiring.Dec`** (inner class): Decidable linearly ordered commutative semiring.

#### Ordered Rings

- **`OrderedRing`**: Combines `Domain`, `LinearlyOrderedSemiring`, `LinearlyOrderedAbGroup`, and `PosetRing`. Adds `ide>zro`, multiplicative closure of `isPos`, sign cancellation laws, and a bridge `positive=>#0`/`negative=>#0`/`#0=>eitherPosOrNeg` linking `isPos`/`isNeg` with the apartness `#0`. Provides default implementations expressing `#0` as `isPos ∨ isNeg`.
- **`positive_*-cancel`**, **`negative_*-cancel`**: Sign of factors recovered from sign of product.
- **`positive_negative_*`**, **`negative_positive_*`**, **`<_*_negative`**: Sign rules for products.
- **`pos#0`/`neg#0`**: Strict inequalities imply additive apartness.
- **`OrderedRing.denseOrder`**: Promotes to an `UnboundedDenseLinearOrder` (no upper or lower bound, dense via midpoints) when `2` is invertible.
- **`inv-negative`**: A negative invertible element has negative inverse.
- **`OrderedRing.positive_*-cancel-left`**: From `0 < x * y` and `0 < x` conclude `0 < y`.
- **`OrderedRing.positive_*-cancel-right`**: From `0 < x * y` and `0 < y` conclude `0 < x`.
- **`OrderedRing.square>0-cancel`**: If `0 < x * x` then `0 < x` or `x < 0`.
- **`OrderedRing.abs-square`**: `abs x * abs x = x * x`.
- **`abs_*`**, **`abs_ide`**, **`abs_pow`**: Multiplicativity of `abs`.
- **`OrderedRing.Dec`** (inner class): Decidable ordered ring; uses additive trichotomy `+_trichotomy` to derive comparison, connectedness, and `pos_*-cancel-left`. The default `#0` becomes nonzero (`/= zro`).

#### Ordered Commutative Rings

- **`OrderedCRing`**: Combines `OrderedRing` with `IntegralDomain` and `LinearlyOrderedCSemiring`; right cancellation derived via commutativity.
- **`OrderedCRing.Dec`** (inner class): Decidable ordered commutative integral domain.

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
- **`DiscreteOrderedField.<_rotate-right-conv`**: For `0 < y`, `x < z * finv y` gives `x * y < z`.
- **`DiscreteOrderedField.<_rotate-left-conv`**: For `0 < y`, `finv y * x < z` gives `x < y * z`.
- **`finv_<=`**: Non-strict order reversal.
- **`finv>=0`**: Non-strict version of positivity preservation.
- **`finv>0-diff`**: Telescoping identity `finv x - finv (x + 1) = finv (x * (x + 1))`.

#### Ordered Algebras

- **`OrderedAAlgebra`**: Associative algebra over an `OrderedCRing` with order-reflecting/preserving coefficient map (`coef_<`, `coef_<-inv`).
- **`coef_<=`**: Non-strict version of `coef_<`.
- **`OrderedCAlgebra`**: Commutative ordered algebra over an ordered commutative ring.
- **`OrderedFieldAlgebra`**: Commutative ordered algebra whose underlying ring is an ordered field.
