### Algebra.Ordered

Algebraic structures equipped with a compatible partial order, ranging from ordered monoids to lattice-ordered abelian groups and ordered (semi)rings.

This module builds a tower of mixed order/algebra classes by combining the additive hierarchy (`AddMonoid`, `AddGroup`, `AbGroup`) with order-theoretic structure (`Poset`, `JoinSemilattice`, `MeetSemilattice`, `DistributiveLattice`). The key compatibility axiom `<=_+` (addition is monotone) propagates through the hierarchy, while in groups it lets one freely translate between `x <= y` and `0 <= y - x`. The lattice-ordered abelian group `LatticeAbGroup` is the central abstraction: it derives distributivity from group cancellation and supports an absolute value `abs x = x ∨ negative x` with all standard inequalities. Two `\where`-classes `FromMeet`/`FromJoin` show that one lattice operation determines the other via negation. `AbsAbGroup` adds `abs>=0` and develops the positive/negative-part decomposition `x = part+ x - part- x`. The ordered (semi)ring layer `PosetSemiring`/`PosetRing` axiomatizes monotone multiplication by nonnegatives and derives sign rules and monotonicity of `pow` and `natCoef`.

#### Ordered Additive Monoids

- **`PosetAddMonoid`**: Extends `AddMonoid` and `Poset` with `<=_+`: addition is monotone in both arguments.
- **`PosetAddMonoid.BigSum_<=`**: Pointwise inequality of arrays lifts to their `BigSum`.
- **`PosetAddMonoid.BigSum_>=0`**: A sum of nonnegatives is nonnegative.
- **`PosetAddMonoid.<=_+-positive`**: `0 <= a` and `0 <= b` imply `0 <= a + b`.
- **`PosetAddMonoid.*n_<=`**: Natural-number scaling `n *n -` is monotone.
- **`PosetAddMonoid.*n_>=0`**: `n *n a` is nonnegative when `a` is.
- **`PosetAbMonoid`**: Combines `PosetAddMonoid` with commutativity (`AbMonoid`).

#### Lattice-Ordered Monoids

- **`JoinSemilatticeAddMonoid`**: Adds left/right distributivity of `+` over `∨` (`join_+-left`, `join_+-right`).
- **`MeetSemilatticeAddMonoid`**: Adds left/right distributivity of `+` over `∧` (`meet_+-left`, `meet_+-right`).
- **`JoinSemilatticeAbMonoid`**: Commutative version; derives `join_+-right` from `join_+-left` via `+-comm`.
- **`MeetSemilatticeAbMonoid`**: Commutative version; derives `meet_+-right` from `meet_+-left` via `+-comm`.

#### Ordered Additive Groups

- **`PosetAddGroup`**: Combines `PosetAddMonoid` with `AddGroup`.
- **`PosetAddGroup.from>=0`**, **`PosetAddGroup.to>=0`**: `x <= y ↔ 0 <= y - x`.
- **`PosetAddGroup.from<=0`**, **`PosetAddGroup.to<=0`**: `x <= y ↔ x - y <= 0`.
- **`PosetAddGroup.negative_<=`**, **`PosetAddGroup.negative_<=-conv`**: Negation is order-reversing (and reflects ≤).
- **`PosetAddGroup.negative>=0`**, **`PosetAddGroup.negative-to<=0`**: `x <= 0 ↔ 0 <= negative x`.
- **`PosetAddGroup.negative<=0`**, **`PosetAddGroup.negative-to>=0`**: `0 <= x ↔ negative x <= 0`.
- **`PosetAddGroup.<=_+-cancel-left`**, **`PosetAddGroup.<=_+-cancel-right`**: Cancellation of an additive term in an inequality.
- **`PosetAbGroup`**: Combines `PosetAddGroup`, `PosetAbMonoid`, and `AbGroup`.

#### Lattice-Ordered Abelian Groups

- **`LatticeAbGroup`**: Extends `PosetAbGroup`, both lattice-monoid classes, and `DistributiveLattice`. Derives `meet_+-left`, `join_+-left`, and the distributive inequality `ldistr>=` from group cancellation alone.
- **`LatticeAbGroup.meet_negative`**: `negative (x ∧ y) = negative x ∨ negative y`.
- **`LatticeAbGroup.join_negative`**: `negative (x ∨ y) = negative x ∧ negative y`.
- **`LatticeAbGroup.modularity`**: `x + y = (x ∨ y) + (x ∧ y)`.
- **`LatticeAbGroup.abs`**: Absolute value `abs x = x ∨ negative x`.
- **`LatticeAbGroup.abs>=id`**, **`LatticeAbGroup.abs>=neg`**: `abs` dominates both `x` and `negative x`.
- **`LatticeAbGroup.abs_negative`**: `abs (negative x) = abs x`.
- **`LatticeAbGroup.abs-ofPos`**, **`LatticeAbGroup.abs-ofNeg`**: `abs` on signed elements.
- **`LatticeAbGroup.abs_abs`**: Idempotence of `abs`.
- **`LatticeAbGroup.abs_+`**: Triangle inequality `abs (x + y) <= abs x + abs y`.
- **`LatticeAbGroup.abs_-`**: `abs (x - y) = abs (y - x)`.
- **`LatticeAbGroup.abs_-left`**, **`LatticeAbGroup.abs_-right`**: Reverse triangle inequalities for `abs`.
- **`LatticeAbGroup.abs_zro`**, **`LatticeAbGroup.abs_zro-ext`**: `abs` of zero, and the extensional converse `abs x = 0 → x = 0`.
- **`LatticeAbGroup.abs>=_-`**: `y - x <= abs (x - y)`.
- **`LatticeAbGroup.abs-univ`**: Universal property of `abs (x - y)` from two-sided bounds.
- **`LatticeAbGroup.IsSolid`**: Predicate that a subset is closed under `abs y <= abs x` (a solid/order-ideal property).
- **`LatticeAbGroup.FromMeet`**: Builds the join from negation and meet: `a ∨ b := negative (negative a ∧ negative b)`.
- **`LatticeAbGroup.FromJoin`**: Builds the meet from negation and join: `a ∧ b := negative (negative a ∨ negative b)`.

#### Abelian Groups with Nonnegative Absolute Value

- **`AbsAbGroup`**: Extends `LatticeAbGroup` with `abs>=0`.
- **`AbsAbGroup.part+`**: Positive part `x ∨ 0`.
- **`AbsAbGroup.part-`**: Negative part `negative x ∨ 0`.
- **`AbsAbGroup.parts_join`**: `part+ x ∨ part- x = abs x`.
- **`AbsAbGroup.parts_meet`**: `part+ x ∧ part- x = 0` (positive/negative parts are disjoint).
- **`AbsAbGroup.parts_+`**: `part+ x + part- x = abs x`.
- **`AbsAbGroup.parts_-`**: `part+ x - part- x = x` (canonical Jordan decomposition).
- **`AbsAbGroup.join_+-double`**: `x + y <= (x + x) ∨ (y + y)`.
- **`AbsAbGroup.join_+-comm`**: `(x ∨ y) + (x ∨ y) = (x + x) ∨ (y + y)`.
- **`AbsAbGroup.join_*n`**: `n *n` distributes over `∨`; the inner `steps` lemma handles powers of two by induction.
- **`AbsAbGroup.abs_*n`**: `n *n abs x = abs (n *n x)`.
- **`AbsAbGroup.meet_*n`**: `n *n` distributes over `∧`.

#### Ordered Semirings and Rings

- **`PosetSemiring`**: Extends `Semiring` and `PosetAbMonoid` with `zro<=ide` and monotonicity of multiplication on the left/right by nonnegatives (`<=_*_positive-left`, `<=_*_positive-right`).
- **`PosetSemiring.<=_*_positive_positive`**: Product of two nonnegatives is nonnegative.
- **`PosetSemiring.<=_*_positive_negative`**, **`PosetSemiring.<=_*_negative_positive`**: Sign rules for products with one nonpositive factor.
- **`PosetSemiring.natCoef>=0`**: The natural-number coercion is nonnegative.
- **`PosetSemiring.natCoef_<=`**: `natCoef` is monotone on `Nat`.
- **`PosetSemiring.pow>=0`**: `pow a k` is nonnegative when `a` is.
- **`PosetSemiring.pow_<=-monotone`**: `pow` is monotone in the base for nonnegative bases.
- **`PosetSemiring.pow<=id`**: For `0 <= a <= 1` and `k ≠ 0`, `pow a k <= a`.
- **`PosetSemiring.pow>=id`**: For `1 <= a` and `k ≠ 0`, `a <= pow a k`.
- **`PosetSemiring.pow<=1`**: Powers of an element in `[0,1]` stay below `1`.
- **`PosetSemiring.pow_<=-degree`**: For `0 <= a <= 1`, `pow` is antitone in the exponent.
- **`PosetRing`**: Extends `PosetSemiring`, `Ring`, and `PosetAbGroup`. Reduces to a single axiom `<=_*-positive` (product of nonnegatives is nonnegative); derives the one-sided monotonicity laws using `from>=0`/`to>=0` and ring distributivity.
