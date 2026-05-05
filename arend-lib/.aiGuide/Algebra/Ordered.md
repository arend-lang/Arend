### Algebra.Ordered

Algebraic structures (monoids, groups, semirings, rings) compatible with a partial order, including ordered lattice-groups and absolute value structures.

#### Ordered Additive Monoids

- **`PosetAddMonoid`**: Extends `AddMonoid` and `Poset`. Addition is monotone in both arguments via `<=_+`: `a <= b -> c <= d -> a + c <= b + d`.
- **`PosetAbMonoid`**: Extends `PosetAddMonoid` and `AbMonoid`. Commutative ordered additive monoid.
- **`JoinSemilatticeAddMonoid`**: Extends `PosetAddMonoid` and `JoinSemilattice`. Addition distributes over joins on both sides via `join_+-left` and `join_+-right`.
- **`MeetSemilatticeAddMonoid`**: Extends `PosetAddMonoid` and `MeetSemilattice`. Addition distributes over meets on both sides via `meet_+-left` and `meet_+-right`.
- **`JoinSemilatticeAbMonoid`**: Commutative variant; derives `join_+-right` from `join_+-left` using commutativity.
- **`MeetSemilatticeAbMonoid`**: Commutative variant; derives `meet_+-right` from `meet_+-left` using commutativity.

#### Ordered Additive Groups

- **`PosetAddGroup`**: Extends `PosetAddMonoid` and `AddGroup`. Ordered additive group.
- **`PosetAbGroup`**: Extends `PosetAddGroup`, `PosetAbMonoid`, and `AbGroup`. Ordered abelian group.
- **`LatticeAbGroup`**: Extends `PosetAbGroup`, `MeetSemilatticeAbMonoid`, `JoinSemilatticeAbMonoid`, and `DistributiveLattice`. Lattice-ordered abelian group; provides automatic derivations of `meet_+-left`, `join_+-left`, and the distributive law `ldistr>=` from the group and order axioms.

#### Lattice-Group Constructors

- **`LatticeAbGroup.FromMeet`**: Builds a `LatticeAbGroup` given only a meet, defining `join a b := -(-a ∧ -b)` and deriving the join laws via negation duality.
- **`LatticeAbGroup.FromJoin`**: Dual constructor; builds a `LatticeAbGroup` from only a join, defining `meet a b := -(-a ∨ -b)`.

#### Absolute Value

- **`AbsAbGroup`**: Extends `LatticeAbGroup`. Adds an absolute value `abs` with the axiom `abs>=0`: `0 <= abs x`.

#### Ordered Rings and Semirings

- **`PosetSemiring`**: Extends `Semiring` and `PosetAbMonoid`. Ordered semiring with `zro<=ide` (`0 <= 1`) and one-sided multiplicative monotonicity by nonnegative elements: `<=_*_positive-left` and `<=_*_positive-right`.
- **`PosetRing`**: Extends `PosetSemiring`, `Ring`, and `PosetAbGroup`. Ordered ring axiomatized by `<=_*-positive` (product of nonnegatives is nonnegative); the one-sided monotonicity laws are derived using subtraction and the `from>=0`/`to>=0` correspondence between `x <= y` and `0 <= y - x`.
