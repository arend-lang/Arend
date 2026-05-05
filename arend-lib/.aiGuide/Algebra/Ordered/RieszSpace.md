### Algebra.Ordered.RieszSpace

Ordered abelian groups and modules where divisibility properties interact with the order, culminating in Riesz spaces (lattice-ordered vector spaces over the rationals).

#### Classes

- **`PosetDivAbGroup`**: Extends `PosetAbGroup` and `TorsionFreeGroup`. A poset abelian group where positivity of `n *n a` (for nonzero `n`) implies positivity of `a`. The torsion-free property is derived from this divisibility-order condition via antisymmetry.
- **`PosetQModule`**: Extends `QModule` and `PosetDivAbGroup`. A rational-scalar module equipped with a compatible poset structure satisfying the divisibility condition on positivity.
- **`RieszSpace`**: Extends `PosetQModule`, `AbsAbGroup`, and `LatticeAbGroup.FromJoin`. A Riesz space — a `QModule` that is also a lattice with absolute values, where `abs a >= 0` is derived from the join structure and `<=_*n-div` is derived from `noTorsion-div` and the absolute-value identity.

#### Construction

- **`RieszSpace.fromAbs`**: Builds a `RieszSpace` from a `PosetQModule` `A` together with a function producing, for each `a`, an element `b` that is a least upper bound of `a` and `negative a` (i.e., the absolute value as a supremum). Defines the join via `join a b = (1/2) *q (a + b + |a - b|)` and verifies the lattice axioms (`join-left`, `join-right`, `join-univ`) using rational scaling cancellation.
  - **`getAbs`**: Helper extracting the supremum-of-`{a, -a}` data from the input function.
  - **`join`**: The join operation defined as `(1/2) *q (a + b + abs(a - b))`.
